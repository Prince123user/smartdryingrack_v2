# Connecting a physical drying rack

The Android app now uses Wi-Fi REST by default. It never connects to the mock controller. Since no existing firmware API specification was supplied, this is the implemented integration contract; adjust the adapter if your firmware uses different routes or fields.

Connect the phone and ESP32 to the same Wi-Fi network. In Connection, enter a base address such as `http://192.168.1.50` and tap **Save and connect**. Connection is considered online only after a valid status response. The address survives app restarts. Plain HTTP is accepted only for private IPv4 addresses; use HTTPS with a trusted certificate for other hosts. Redirects are rejected. Credentials in URLs are rejected. LAN HTTP is unencrypted and unauthenticated: do not expose this API through port forwarding. Authenticated remote access is not implemented.

## Status

`GET /api/status` returns HTTP 200 with JSON:

```json
{
  "state": "RETRACTED",
  "firmwareVersion": "1.2.0",
  "sensors": {
    "temperature": 29.5,
    "humidity": 64,
    "rainDetected": false,
    "clothesDryPercentage": null
  }
}
```

States: `EXTENDED`, `RETRACTED`, `EXTENDING`, `RETRACTING`, `STOPPED`, `ERROR`, `UNKNOWN`. Temperature must be a finite number from -50 to 80 °C, humidity from 0 to 100%, and rainDetected a boolean. Dryness is an optional integer from 0 to 100; omit it or return null if no calibrated clothes sensor is fitted. The app does not derive clothes dryness from air humidity. Firmware must return current sensor samples, not a cached historical reading. The app timestamps receipt and blocks extension if its last reading is over six seconds old.

Local HTTP requests use the phone’s Wi-Fi network even when Android prefers mobile data for Internet access. Wi-Fi loss/return immediately restarts a requested connection, and returning to the app revives a failed polling loop. Tapping Disconnect intentionally disables this recovery until Connect is tapped again.

Polling runs every two seconds after success and every five seconds after failure. Requests have 2.5-second connection/read timeouts. Bad status or sensor data marks the connection unavailable and rack position unknown; retained readings are labelled stale. No firmware version, signal strength or device identity is invented.

## Commands

`POST /api/commands`, Content-Type `application/json`:

```json
{
  "action": "EXTEND",
  "requestId": "65c95e0e-05cf-42c9-881a-441280a8ff24"
}
```

Actions: `EXTEND`, `RETRACT`, `STOP`. A successful HTTP 200/202 response must include:

```json
{"accepted": true}
```

The app does not intentionally retry motor commands. Firmware must deduplicate requestId and return the same acknowledgement for a duplicate. Rejection can use a non-2xx response or `{"accepted":false}`. An acknowledgement is acceptance, not physical completion. The app watches status for the requested terminal position. If it is not reached within ten seconds, a stop is attempted and the app enters a fault state; reconnect after inspecting the rack. Adapt this timeout for your physical travel time before commissioning.

Manual movement is blocked for unknown/faulted positions. Extension also requires fresh, valid and dry telemetry. Stop is permitted even when position is unknown. An emergency stop holds automation even if the request cannot be confirmed; a successfully accepted manual movement releases the hold. Selecting Auto alone does not release it. The phone starts with a local Manual default, then follows reported firmware mode when supported. Auto/Manual changes use POST /api/config and require confirmation. A new schedule starts disabled.

## Firmware responsibilities

Implement physical limit switches, motor current/obstruction protection, maximum travel timeout, local rain protection and a physical emergency stop. App requests can fail, arrive late or be queued behind another request. A phone emergency button is a network request and cannot replace a physical emergency stop.

App automation runs only while the process is alive and connected. It is not a background service. For unattended protection and schedules, implement these on the ESP32 itself. Settings and the latest 100 phone events persist locally; clearing history clears both alert and history records. The device address remains saved when disconnected; the app reconnects at its next process start.

## Not connected yet

- Weather provider and location: forecasts are shown as unavailable; no demo forecast drives automation.
- BLE, MQTT, Wi-Fi provisioning and device discovery.
- Remote authentication, cloud control and system push notifications.
- Physical hardware and device UI validation: the cloud checks validate the REST adapter against a local HTTP test server, not your ESP32.

## Commissioning

1. Build/install `app/build/outputs/apk/debug/app-debug.apk` on an Android 8+ phone.
2. Check `/api/status` against the contract before connecting the app.
3. With the motor area clear, test Manual Extend, Retract and Stop and verify actual limit-switch states.
4. Test wet-sensor extension blocking, three consecutive wet samples and automatic retraction.
5. Disconnect Wi-Fi and verify the ESP32 independently handles rain and motor safety.
6. Verify motor timeout, physical emergency stop and command deduplication before enabling unattended schedules.

## Buzzer configuration (firmware 1.1.0+)

`GET /api/status` may include a top-level boolean `buzzerEnabled`, `motionReason`, and `sensors.rainRaw`. Absence of `buzzerEnabled` means this firmware does not expose app buzzer support.

`POST /api/buzzer` accepts `{"enabled":true,"requestId":"<unique ID>"}` and returns `{"accepted":true,"buzzerEnabled":true}`. The returned boolean must match the requested value. The merged uploaded firmware keeps it in RAM until reboot and beeps briefly on enable; `/api/config` and `/api/buzzer/test` are retained. Setting changes do not move the rack, and acknowledgements from an obsolete connection session are not accepted.

Rain-triggered app retraction requires current, fresh wet telemetry and confirmation; a stale debounce confirmation is insufficient when the latest reading is dry. Explicit schedules may still retract on dry days when enabled. New schedules default to disabled.

The current firmware is based on the uploaded working sketch. See [firmware 1.1.1](ESP32_FIRMWARE.md) and [the implemented lighting API](LIGHTING_API.md) for its additive routes, volatile settings, stable-dry AUTO behavior and GPIO allocation.

For the merged firmware, phone automation respects the device STOP latch and leaves rain protection to the device. Explicit STOP attempts are allowed during telemetry errors at the actively requested address, with normal acknowledgement/error handling; explicit Disconnect still prevents requests.
