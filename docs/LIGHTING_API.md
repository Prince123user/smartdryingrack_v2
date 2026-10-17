# House Lights — ordinary LEDs, schema 2

This replaces the earlier six-RGB-zone proposal. **Front, Left side, Right side, Back, CR and Balcony are ordinary single-color LEDs. Only the independent automatic system indicator is RGB.** There are no house-light color controls or RGB request fields.

## Implementation status

- Android client/UI: implemented, with six independent power/brightness controls, per-zone pending/error feedback, actual-state fetching, and a separate read-only RGB status card.
- `firmware/SmartDryingRack/HouseLighting.h`: hardware-independent C++ state/command component, host-tested with a simulated output driver. It is **not included by or enabled in the current sketch**.
- ESP32 lighting HTTP endpoints and physical driver: **not integrated yet**. The supplied working `.ino` has not been attached in this conversation. The repository sketch lacks the described stable-dry extension and latched STOP behavior, so it is deliberately unchanged. Do not replace working firmware with it to obtain lighting.
- Wiring: provisional plan in [LIGHTING_WIRING.md](LIGHTING_WIRING.md). Expansion-board identity, LED electrical ratings/resistors and RGB part/pinout are still required. No finished circuit, GPIO assignment, upload-ready updated working `.ino`, target compile or physical operation is claimed.

The existing rack `/api/status`, `/api/commands` and `/api/buzzer` APIs, rain monitoring, servo, buzzer, history and notifications remain unchanged. Missing lighting routes (GET 404/501) disable lighting controls; other read failures retry without replaying commands. The app rejects the previous RGB house-zone schema 1 rather than issuing incompatible writes.

## State and values

Stable case-sensitive IDs: `front`, `left`, `right`, `back`, `cr`, `balcony`. `system_status` is not a manual zone.

Each ordinary LED has `power` (strict JSON boolean) and `brightness` (strict JSON integer 0–100 percent). Zero means no visible output, even while power remains ON. OFF suppresses output but preserves the selected brightness. Changing brightness while OFF does not turn the LED on. RGB/color fields are invalid for house zones.

The proposed firmware component starts **all six OFF with brightness 50% retained in RAM** on every boot, after its driver successfully applies OFF to all outputs. No house settings persist in NVS; reboot loses prior selections. Existing buzzer persistence is unrelated and must be preserved. Hardware must also default OFF during reset/brownout before firmware starts; see wiring requirements.

`deviceSession` is a unique boot token; change it every reboot. Session/request tokens use 1–96 ASCII letters, digits, `.`, `_` or `-` (UUIDs are valid). `revision`, `expectedRevision`, `stateVersion` are nonnegative integers ≤9007199254740991. `stateVersion` increases for every applied house or status-indicator change; each zone's revision is its most recent applied global version, ≤stateVersion. Initial values may be zero. Only applied driver state is publishable, not merely queued targets; optical feedback is not implied.

## GET actual state

`GET /api/lights` → 200 with exactly six distinct house zones. Example values below are illustrative, not live readings:

```json
{
  "schemaVersion":2,
  "deviceSession":"boot-unique-token",
  "stateVersion":1,
  "zones":[
    {"id":"front",   "power":true,  "brightness":70,"revision":1,"lastRequestId":"front-request"},
    {"id":"left",    "power":false, "brightness":50,"revision":0,"lastRequestId":null},
    {"id":"right",   "power":false, "brightness":50,"revision":0,"lastRequestId":null},
    {"id":"back",    "power":false, "brightness":50,"revision":0,"lastRequestId":null},
    {"id":"cr",      "power":false, "brightness":50,"revision":0,"lastRequestId":null},
    {"id":"balcony", "power":false, "brightness":50,"revision":0,"lastRequestId":null}
  ],
  "indicator":null
}
```

`indicator:null` explicitly means actual RGB output/meaning is unavailable. Do not substitute a computed desired color or a proposed mapping for actual output. Once the RGB LED type and its driver are confirmed and integrated, replace null with:

```json
{
  "id":"system_status","mode":"AUTOMATIC","power":true,
  "rgb":{"r":255,"g":0,"b":0},"brightness":30,
  "meaning":"rain","description":"Rain detected","pattern":"solid"
}
```

Indicator RGB channels are integers 0–255; brightness is 0–100; power is boolean. `meaning` and `description` are nonempty device-reported text, max 96 and 256 characters respectively. `pattern` is `solid`, `blink`, `pulse` or `off`. The app displays these fields without choosing colors. Disconnected cached state is labeled stale. `lastRequestId` must be present (null until the first applied request). Local/external changes must advance the addressed revision and clear its request ID unless they correspond to a known command.

## Update exactly one ordinary LED

`POST /api/lights/zones/front`:

```json
{
  "requestId":"new-uuid",
  "deviceSession":"boot-unique-token",
  "expectedRevision":1,
  "power":false,
  "brightness":70
}
```

Apply a complete absolute setting only to the addressed zone. Require exactly these fields; reject unknown/missing/duplicate keys, nested values, string/number booleans, fractional brightness, out-of-range values and any RGB/color field. JSON parsing and type validation belong in the HTTP adapter before constructing the C++ typed request. The companion component is not a JSON parser.

Reject wrong session/stale addressed-zone revision before output changes. Each zone's revision is independent: changing Front does not invalidate an unrelated Left command. Never treat invalid/omitted zone IDs as all zones. `POST .../system_status` must return 403; no manual indicator-write or bulk endpoint exists.

### Accepted

The proposed component applies one short, bounded driver transaction synchronously. Only on success does it publish state and return `200 OK`:

```json
{"accepted":true,"requestId":"new-uuid","zone":"front","deviceSession":"boot-unique-token","revision":2}
```

The mobile client also supports a future `202 Accepted` implementation, but this component has no command queue. A queued adapter would need its own per-zone serialization and reservation logic. Do not return success before driver application with the synchronous component.

The app keeps confirmed state separate from the pending selection. ACK alone is insufficient: a fresh GET must show the same session, zone, request ID, exact power/brightness and revision ≥ the acknowledged revision. An old/mismatched ACK or an old snapshot cannot overwrite a newer selection. Even in a newer global snapshot, a zone revision must not regress, and its values/request identity cannot change without a new zone revision. Inconsistent responses leave the previous confirmed values intact, label them stale and report a connection error.

The component caches the most recent 32 successful request identities across zones. An identical cached request returns its original ACK without reapplying. Reusing a cached ID with different fields returns 409. After eviction, an old request's stale expected revision still rejects it without reapplication; it may return 409 instead of the original ACK. Clients must generate unique IDs. A new explicit retry uses a new UUID and freshly read revision. Never automatically replay old commands after reconnecting.

### Rejected or unavailable

Example `409 Conflict`:

```json
{
  "accepted":false,"requestId":"new-uuid","zone":"front","deviceSession":"boot-unique-token",
  "error":{"code":"REVISION_CONFLICT","message":"Fetch current zone state before retrying."}
}
```

HTTP adapter mapping:

| Component result | HTTP | Error code |
| --- | --- | --- |
| INVALID_REQUEST / invalid JSON or types | 400 | `INVALID_REQUEST` |
| READ_ONLY_INDICATOR | 403 | `READ_ONLY_INDICATOR` |
| UNKNOWN_ZONE | 404 | `UNKNOWN_ZONE` |
| SESSION_CHANGED | 409 | `SESSION_CHANGED` |
| REVISION_CONFLICT | 409 | `REVISION_CONFLICT` |
| REQUEST_ID_REUSE | 409 | `REQUEST_ID_REUSE` |
| NOT_READY / VERSION_EXHAUSTED | 503 | `LIGHTING_UNAVAILABLE` |
| DRIVER_FAILURE | 503 | `DRIVER_FAILURE` |

Validation rejection causes no output changes. A failed bus write can have an uncertain physical outcome: the component marks itself unavailable and does not publish the intended setting as confirmed. GET must then return 503 rather than stale state as actual. Reinitialize/read back hardware under an explicit recovery policy before becoming ready. Other rack functions must continue.

Use `Content-Type: application/json`, `Cache-Control: no-store`, bounded request bodies (e.g. 512 bytes), bounded response bodies (client limit 65,536 characters), and no redirects. The app shows non-2xx errors by HTTP code; it also handles `accepted:false` in a legacy 2xx body without false success.

## Automatic RGB mapping — proposal, not a physical implementation

The isolated `proposeIndicator()` function expresses this candidate policy for integration after the RGB type is confirmed:

| Priority | Condition | Proposed RGB | Meaning |
| --- | --- | --- | --- |
| 1 | Sensor/device/driver error | Yellow (255,255,0) | `error` |
| 2 | Wi-Fi connecting/disconnected | Blue (0,0,255) | `connecting` |
| 3 | Rain detected | Red (255,0,0) | `rain` |
| 4 | Dry, normal operation | Green (0,255,0) | `normal` |

Error overrides connection state, which overrides rain, which overrides normal. Rain safety continues even when blue/yellow takes visual priority. Firmware must define valid/fresh sensor conditions; do not announce green while sensors are invalid. Wi-Fi state is locally detected; absence of phone polling alone is not disconnection. Proposed colors may be solid; brightness and physical polarity remain unassigned until electrical details are available. The function returns a proposed logical color only, never a confirmed physical state. Only call `indicatorApplied()` after an actual driver update succeeds.

## App reliability and verification

The screen fetches on open/resume/reconnect, polls every 2 seconds and retries read failures every 5 seconds. Brightness edits debounce 400 ms; power sends immediately. Per-zone state separates confirmed and pending values. A zone awaiting acceptance/confirmation is disabled while other zones remain available. Lighting traffic has its own mutex, independent of motor and buzzer traffic. Pending selections are clearly unconfirmed on failure, with explicit Retry/Discard. Confirmation timeout is 8 seconds after the first post-ACK read. There is no demo fallback, saved-color state or offline mutation queue.

Cloud checks cover the real app build, mocked API validation/HTTP payloads, zone isolation, OFF brightness retention, zero output, reconnect/stale responses, six manual switches/sliders and repeated navigation. C++ host checks simulate only the output driver. Neither check certifies physical LEDs, the final circuit, target ESP32 compilation or the missing working firmware's behavior.

After integration, test distinct brightness values on all six LEDs, OFF/ON retention, zero, reboot-OFF, simultaneous edits, Wi-Fi loss/recovery and status-priority overlaps. Confirm the working firmware's stable-dry extension, latched STOP, rain retraction, manual control, diagnostics and buzzer patterns remain intact under lighting traffic.

For TCP timeouts, address checks and distinguishing missing firmware from an unreachable rack, see [connection troubleshooting](CONNECTION_TROUBLESHOOTING.md).
