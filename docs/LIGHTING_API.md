# House Lights: proposed ESP32 contract (schema 1)

**Status: app client implemented; firmware lighting support is NOT implemented.** The current `SmartDryingRack.ino` exposes rack status, movement and buzzer APIs, but no lighting endpoints, LED driver or status-indicator mapping. This document proposes the contract for compatible firmware. It does not assert that seven LEDs are wired or operating. GPIO assignments, LED electrical type/protocol, driver circuitry, power supply and the number of physical LEDs per zone must be specified from the actual hardware before firmware implementation. No existing rack GPIO is repurposed.

The Android screen opens from Home or Control, preserves the existing tabs and Back behavior, and follows the current explicitly requested rack connection. A missing lighting endpoint (404 or 501) disables lighting controls while leaving rack functions available. Unknown values are shown as not reported. The app never substitutes sample lighting values or a locally calculated status meaning.

## Identifiers and values

Manual zone IDs are exactly `front` (Front), `left` (Left side), `right` (Right side), `back` (Back), `cr` (CR), `balcony` (Balcony). They are case-sensitive, unique, stable and represent independently controlled outputs. `system_status` is reserved, automatic and never a manual zone.

Each setting contains:

- `power`: JSON boolean, no string/number coercion.
- `rgb`: object with `r`, `g`, `b` JSON integers, each 0–255. These are selected unscaled color channels; no alpha channel.
- `brightness`: JSON integer 0–100, in percent. Zero means no visible output even with `power: true`. Black RGB likewise has no visible output. A driver applies brightness separately from the stored RGB values; any electrical inversion or color calibration belongs in firmware.

OFF suppresses physical output while retaining the selected RGB and brightness. Editing color/brightness while OFF updates those retained fields without turning the light on. Changing one zone must never alter another zone or the system indicator. Persistence across reboots is a firmware policy that must be documented when implemented; the app always reads actual post-reboot values.

All revisions and versions are nonnegative JSON integers ≤ 9007199254740991. `deviceSession` is a unique, nonempty, opaque boot identifier, changed on every device restart (1–96 characters). The app must not infer identity from an IP alone. Request IDs are nonempty opaque strings of 1–96 characters (the app generates UUIDs). Descriptive strings are nonempty; `meaning` is ≤96 characters and `description` ≤256 characters. All responses are JSON with `Content-Type: application/json`, `Cache-Control: no-store` and a bounded payload (client limit 65,536 characters).

## Read actual state

`GET /api/lights` → `200 OK` with a single atomic, actual device snapshot, including exactly six distinct zones and the independent indicator. The following values are **illustrative examples, not live device readings or firmware defaults**:

```json
{
  "schemaVersion": 1,
  "deviceSession": "unique-boot-id",
  "stateVersion": 12,
  "zones": [
    {"id":"front",   "power":true,  "rgb":{"r":0,"g":180,"b":255}, "brightness":70, "revision":12, "lastRequestId":"front-request-id"},
    {"id":"left",    "power":false, "rgb":{"r":255,"g":120,"b":0}, "brightness":50, "revision":0,  "lastRequestId":null},
    {"id":"right",   "power":false, "rgb":{"r":255,"g":255,"b":255}, "brightness":50, "revision":0, "lastRequestId":null},
    {"id":"back",    "power":false, "rgb":{"r":255,"g":255,"b":255}, "brightness":50, "revision":0, "lastRequestId":null},
    {"id":"cr",      "power":false, "rgb":{"r":255,"g":255,"b":255}, "brightness":50, "revision":0, "lastRequestId":null},
    {"id":"balcony", "power":false, "rgb":{"r":255,"g":255,"b":255}, "brightness":50, "revision":0, "lastRequestId":null}
  ],
  "indicator": {
    "id":"system_status", "mode":"AUTOMATIC", "power":true,
    "rgb":{"r":0,"g":255,"b":0}, "brightness":30,
    "meaning":"normal", "description":"Normal operation", "pattern":"solid"
  }
}
```

`stateVersion` increases for every applied lighting change, including automatic indicator changes. A zone's `revision` is the global version at its last applied change, never greater than snapshot `stateVersion`; unchanged zones keep their revisions. Initial revisions/version may be zero. `lastRequestId` is null until a request is applied to that zone; retain the most recently applied ID on subsequent GETs until another change. External/local zone changes must advance revisions and clear `lastRequestId` unless they correspond to a known request. The snapshot reports applied driver state, not merely a queued target; optical feedback is not implied unless physical feedback hardware is supplied.

The indicator must include actual settings, a firmware meaning code, a human-readable description, and pattern (`solid`, `blink`, `pulse`, `off`). The app displays these reported fields without inventing their mapping. While disconnected it labels retained data stale; the ESP32 itself continues automatic indication without the phone.

## Update only one house zone

`POST /api/lights/zones/{zoneId}` accepts a complete absolute setting for exactly the addressed zone, not a toggle or relative increment:

```json
{
  "requestId":"new-unique-request-id",
  "deviceSession":"unique-boot-id",
  "expectedRevision":12,
  "power":false,
  "rgb":{"r":0,"g":180,"b":255},
  "brightness":70
}
```

Validate all fields before any side effect; reject unknown/missing fields, invalid IDs, wrong JSON types, fractional values, out-of-range values and unexpected nested keys. Reject `system_status` with 403; there is no bulk update, indicator-write or all-zones endpoint. Never reinterpret a missing/invalid zone as all zones. Reject requests from a different `deviceSession` or with a stale `expectedRevision` (409). Validate against the addressed zone's current applied revision so changing Front does not invalidate a pending Left edit.

### Accepted is not confirmed

An accepted command returns `202 Accepted` when queued or `200 OK` if already applied:

```json
{"accepted":true,"requestId":"new-unique-request-id","zone":"front","deviceSession":"unique-boot-id","revision":13}
```

Reserve a globally unique increasing revision at acceptance. Apply commands in revision order, serialize operations for each zone, and reject additional conflicting queued operations for that zone with 409. Other zones stay independent. Do not publish a reserved revision in GET until its corresponding setting is actually applied. A GET must not regress any already published global version. Advance the global counter for indicator changes as well. If driver application fails, expose an error via the indicator and leave that zone's last applied fields/revision unchanged.

Deduplicate `(deviceSession, requestId)` throughout the boot: identical payload + same zone returns the same acknowledgement without reapplying; reuse with a different payload/zone returns 409. A bounded implementation must reject new IDs when its deduplication capacity is exhausted (503), rather than silently forgetting IDs that could be replayed. On boot all old session requests are invalid. These rules prevent delayed or duplicate commands from overwriting newer state.

The client regards a command as confirmed only after a fresh GET reports the same zone, session, `lastRequestId`, exact setting and revision ≥ the accepted revision. The acknowledged revision must be greater than `expectedRevision`. Matching color/power alone cannot confirm a command from this app.

### Rejected

Use non-2xx status and this body, e.g. `409 Conflict`:

```json
{
  "accepted":false,
  "requestId":"new-unique-request-id",
  "zone":"front",
  "deviceSession":"unique-boot-id",
  "error":{"code":"REVISION_CONFLICT","message":"Zone changed; fetch current state before retrying."}
}
```

Suggested statuses/codes: 400 `INVALID_REQUEST`, 403 `READ_ONLY_INDICATOR`, 404 `UNKNOWN_ZONE`, 409 `SESSION_CHANGED` / `REVISION_CONFLICT` / `REQUEST_ID_REUSE` / `ZONE_BUSY`, 413 `PAYLOAD_TOO_LARGE`, 503 `DRIVER_UNAVAILABLE` / `COMMAND_CAPACITY`. A rejection must not change any output. The current app reports HTTP failures by status code, and reads an optional error message for a legacy 2xx body with `accepted:false`; both leave the selection unconfirmed. GET 404/501 means no compatible lighting API; other failures are retryable state-read errors. Do not redirect these requests.

## Proposed automatic status mapping — NOT implemented

The firmware should choose the highest active priority below and report its actual `meaning`, `description`, color, brightness and pattern in GET. These colors/patterns are a proposal only, not app logic or existing hardware behavior:

| Priority | Condition | Proposed indication | Meaning |
| --- | --- | --- | --- |
| 1 (highest) | Safety, sensor, motor or lighting-driver error | Red blinking | `error` |
| 2 | ESP32 Wi-Fi disconnected / reconnecting | Amber blinking | `disconnected` |
| 3 | Rain protection active, with Wi-Fi available and no higher error | Blue pulsing | `rain_protection` |
| 4 | Normal operation | Green solid | `normal` |

The firmware must define what constitutes an error and disconnected state. Wi-Fi loss is locally detectable; absence of app polling alone must not mean Wi-Fi disconnected. Rain protection and limit/motor safety continue regardless of which indicator condition wins. Reconnection does not clear an unresolved error or wet condition. The status light never inherits a house zone's power, color or brightness. Blink frequency, brightness and startup behavior require hardware/user validation before firmware implementation.

## App behavior and limits

Opening/resuming the screen and reconnecting reads fresh state. Valid snapshots poll every 2 seconds, retrying read failures every 5 seconds. Controls require a valid lighting connection and confirmed zone state; retained stale values are labeled. Per-zone selected edits and confirmed settings are distinct. Slider edits debounce 400 ms; color-picker sliders are local previews until Apply, followed by the same debounce. Power changes send immediately. Each zone disables edits while its command is sending/confirming; another zone remains available. Requests share a lighting-only network mutex, separate from motor/buzzer control.

An ACK triggers a GET; confirmation is allowed 8 seconds after that first read. Rejection, timeout or disconnect preserves a visible unconfirmed selection with an error. Only explicit Retry sends a new request ID using the latest device revision; Discard restores the displayed confirmed state. There is no automatic mutation retry or offline command queue. Leaving the screen or losing the requested connection cancels unsent changes and marks uncertain outcomes for review on return. A request already received by hardware cannot be undone by cancellation. New address clears old state; a new boot invalidates pending commands; old boot and out-of-order version responses are ignored.

Existing `/api/status`, `/api/commands` and `/api/buzzer` contracts and rack automation remain unchanged. This feature does not invoke motor, rain, schedule, buzzer, notification or history actions.

## Hardware acceptance after firmware implementation

Verify all six IDs against the actual wiring; turn each zone on/off, set distinct colors and brightness, and confirm no other zone or indicator changes. Test ON at 0%, OFF/ON retention, color changes while OFF, RGB black, simultaneous zones, app restart, firmware reboot, Wi-Fi loss/recovery, rejected/stale commands and delayed responses. Trigger real status conditions and their priority combinations. Confirm rack/rain/buzzer safety behavior during lighting traffic. Cloud tests use simulated device responses and cannot certify physical outputs.
