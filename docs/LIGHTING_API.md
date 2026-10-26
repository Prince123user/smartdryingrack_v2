# House Lights — ordinary LEDs, schema 2

This replaces the earlier six-RGB-zone proposal. **Front, Left side, Right side, Back, CR and Balcony are ordinary single-color LEDs. Only the independent automatic system indicator is RGB.** There are no house-light color controls or RGB request fields.

## Implementation status

Firmware **1.1.2** in `firmware/SmartDryingRack/SmartDryingRack.ino` is merged from the supplied **SmartDryingRack_complete.ino**. It preserves the uploaded rack automation, latched STOP, wiring and existing APIs, including legacy GET/POST `/api/lights`. Additive schema-2 fields and per-zone POST routes now match the Android client. The previous unused portable scaffold has been removed.

The sketch uses the upload's six direct ESP32 LEDC outputs, not the superseded external-PWM plan. See [firmware/build guide](ESP32_FIRMWARE.md) and [source-aligned wiring](LIGHTING_WIRING.md). The app and firmware are built/tested in software; actual electrical operation and the board-specific circuit are not physically verified. Credentials are placeholders in the repository and must be configured locally.

Release 1.1.5 enables common-cathode RGB output on R26/G32/B33 using the user-supplied diagram. GET now reports `indicator` state and `statusIndicator.hardwareEnabled:true`. Older releases or builds with RGB disabled return `indicator:null`; legacy `statusIndicator` reports desired logical colors with `hardwareEnabled:false` for diagnostics only. The app reads the strictly boolean `statusIndicator.hardwareEnabled` as configuration metadata and shows an explicit disabled message when false. A missing flag remains unknown; true with `indicator:null` means enabled but actual state unreported. False with a non-null physical indicator is rejected as inconsistent. Cached configuration is labeled last reported after disconnection. The app must not display the legacy logical colors/reason as a physical light state.

GET 404/501 disables lighting controls; a timeout is separately reported as an unreachable rack, never proof that firmware support is absent. The app rejects earlier RGB house-zone schema 1 instead of writing an incompatible request.

## State and values

Stable case-sensitive IDs: `front`, `left`, `right`, `back`, `cr`, `balcony`. `system_status` is not a manual zone.

Each zone also reports `available` (boolean): false means PWM initialization/write failed. Its retained settings then describe the last stored selection, not a verified current output. The app disables only that zone and labels it unavailable; other zones remain usable. The app accepts omitted availability as true for earlier schema-2 implementations.

Each ordinary LED has `power` (strict JSON boolean) and `brightness` (strict JSON integer 0–100 percent). Zero means no visible output, even while power remains ON. OFF suppresses output but preserves the selected brightness. Changing brightness while OFF does not turn the LED on. RGB/color fields are invalid for house zones.

The uploaded implementation starts **all six OFF with selected brightness 100% in RAM** on every boot. No house settings persist in NVS; reboot loses prior selections. The uploaded device config and buzzer setting are also RAM-only; the compatibility routes preserve this policy. Hardware must also default OFF during reset/brownout before firmware starts; see wiring requirements.

`deviceSession` is a unique boot token; change it every reboot. Session/request tokens use 1–96 ASCII letters, digits, `.`, `_` or `-` (UUIDs are valid). `revision`, `expectedRevision`, `stateVersion` are nonnegative integers ≤9007199254740991. `stateVersion` increases for every applied house or status-indicator change; each zone's revision is its most recent applied global version, ≤stateVersion. Initial values may be zero. Only applied driver state is publishable, not merely queued targets; optical feedback is not implied.

## GET actual state

`GET /api/lights` → 200 with exactly six distinct house zones. The schema-2 portion is shown below; the actual response additionally retains `firmwareVersion`, `settingsPersist`, legacy `lights` and logical `statusIndicator` fields. Example values below are illustrative, not live readings:

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
  "rgb":{"r":255,"g":0,"b":0},"brightness":100,
  "meaning":"RAIN_DETECTED","description":"RAIN_DETECTED","pattern":"solid"
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

Apply a complete absolute setting only to the addressed zone. Require exactly these fields; reject unknown/missing/duplicate keys, nested values, string/number booleans, fractional brightness, out-of-range values and any RGB/color field. The integrated handlers use ArduinoJson with strict type checks and reject duplicate top-level fields in these flat request schemas. The original legacy POST retains its uploaded contract.

Reject wrong session/stale addressed-zone revision before output changes. Each zone's revision is independent: changing Front does not invalidate an unrelated Left command. Never treat invalid/omitted zone IDs as all zones. `POST .../system_status` must return 403; no manual indicator-write or bulk endpoint exists.

### Accepted

The integrated handler applies one bounded LEDC duty write synchronously. Only on success does it publish state and return `200 OK`:

```json
{"accepted":true,"requestId":"new-uuid","zone":"front","deviceSession":"boot-unique-token","revision":2}
```

The mobile client also supports a future `202 Accepted` implementation, but this firmware has no command queue. A queued adapter would need its own per-zone serialization and reservation logic. Do not return success before driver application with the synchronous handler.

The app keeps confirmed state separate from the pending selection. ACK alone is insufficient: a fresh GET must show the same session, zone, request ID, exact power/brightness and revision ≥ the acknowledged revision. An old/mismatched ACK or an old snapshot cannot overwrite a newer selection. Even in a newer global snapshot, a zone revision must not regress, and its values/request identity cannot change without a new zone revision. Inconsistent responses leave the previous confirmed values intact, label them stale and report a connection error.

The firmware caches the most recent 32 successful request identities across zones. An identical cached request returns its original ACK without reapplying. Reusing a cached ID with different fields returns 409. After eviction, an old request's stale expected revision still rejects it without reapplication; it may return 409 instead of the original ACK. Clients must generate unique IDs. A new explicit retry uses a new UUID and freshly read revision. Never automatically replay old commands after reconnecting.

### Rejected or unavailable

Example `409 Conflict` (the existing uploaded error format is preserved):

```json
{"error":"REVISION_CONFLICT","message":"Zone changed; fetch actual state"}
```

Implemented statuses/codes: 400 `INVALID_LIGHT` / `INVALID_JSON` / `INVALID_REQUEST`, 403 `READ_ONLY_INDICATOR`, 404 `NOT_FOUND`, 409 `SESSION_CHANGED` / `REVISION_CONFLICT` / `REQUEST_ID_REUSE`, 503 `LIGHT_UNAVAILABLE`. Validation rejection changes no output. A duty-write failure marks that zone unavailable, advances its revision and clears request identity without publishing the intended setting as successful. GET still returns other zones and includes `available:false` on the failed zone. Hardware recovery/reinitialization needs actual device validation; no automatic command replay occurs.

Use `Content-Type: application/json`, `Cache-Control: no-store`, request bodies limited to 1024 bytes, bounded response bodies (client limit 65,536 characters), and no redirects. The app shows non-2xx errors by HTTP code; it also handles `accepted:false` in a legacy 2xx body without false success.

## Automatic status mapping from the uploaded source

The firmware's logical priority is: servo fault → stale/invalid DHT → rain → STOP latch → Wi-Fi connecting → starting → rack moving → dry/extended → retracted idle. Respective colors are yellow, yellow, red, yellow, blue, blue, cyan, green and white. Rain protection remains independent of indication. This preserves the uploaded implementation rather than substituting the earlier proposed priority.

With `STATUS_RGB_ENABLED=false`, this is **logical indication only**, not physical output. The optional digital GPIO RGB implementation is for a confirmed four-pin part and only supports channel ON/OFF colors at full channel level. Release 1.1.5 enables it as common-cathode according to the supplied diagram; verify the actual pin order and individual current-limiting resistors before powering the circuit. Physical operation is unverified. The app displays a read-only physical indicator only when the firmware reports the enabled output; house-light commands have no route to these RGB outputs.

## App reliability and verification

The screen fetches on open/resume/reconnect, polls every 2 seconds and retries read failures every 5 seconds. Brightness edits debounce 400 ms; power sends immediately. Per-zone state separates confirmed and pending values. A zone awaiting acceptance/confirmation is disabled while other zones remain available. Lighting traffic has its own mutex, independent of motor and buzzer traffic. Pending selections are clearly unconfirmed on failure, with explicit Retry/Discard. Confirmation timeout is 8 seconds after the first post-ACK read. There is no demo fallback, saved-color state or offline mutation queue.

Cloud checks cover the real app build, mocked API validation/HTTP payloads, zone isolation, OFF brightness retention, zero output, reconnect/stale responses, six manual switches/sliders and repeated navigation. C++ host checks execute the merged sketch with simulated GPIO, Wi-Fi and HTTP, using the real ArduinoJson parser. Neither check certifies physical LEDs, the final circuit, target ESP32 compilation or the real rack's physical behavior.

After integration, test distinct brightness values on all six LEDs, OFF/ON retention, zero, reboot-OFF, simultaneous edits, Wi-Fi loss/recovery and status-priority overlaps. Confirm the working firmware's stable-dry extension, latched STOP, rain retraction, manual control, diagnostics and buzzer patterns remain intact under lighting traffic.

For TCP timeouts, address checks and distinguishing missing firmware from an unreachable rack, see [connection troubleshooting](CONNECTION_TROUBLESHOOTING.md).
