# ESP32 Smart Drying Rack firmware

`firmware/SmartDryingRack/SmartDryingRack.ino` is a complete Arduino IDE sketch for the specified ESP32 WROOM-32 wiring. It uses DHT22 temperature/humidity, the FC-37 analog rain output, a servo, and a passive buzzer. There is no Bluetooth, dryness sensor, weather service, fan, or heater.

## Wiring

| Part | Pin | ESP32 |
|---|---|---|
| FC-37 VCC | 3.3 V | 3V3 |
| FC-37 GND | GND | GND |
| FC-37 AO | analog | GPIO34 |
| FC-37 DO (optional) | digital | GPIO27 |
| DHT22 VCC | 3.3 V | 3V3 |
| DHT22 GND | GND | GND |
| DHT22 DATA | digital | GPIO4 |
| Servo signal | PWM | GPIO18 |
| Servo power | external supply | suitable servo supply |
| Servo ground | common ground | ESP32 GND |
| Passive buzzer signal | digital | GPIO19 |
| Passive buzzer ground | GND | GND |

Do not power the servo from the ESP32 3.3 V pin. Use a suitable external supply and connect its ground to ESP32 ground. GPIO34 is input-only, which is appropriate for FC-37 AO.

## Android API compatibility

The Android application currently calls exactly these endpoints:

### `GET /api/status`

Successful response, HTTP 200:

```json
{
  "state":"RETRACTED",
  "firmwareVersion":"1.1.0",
  "sensors": {
    "temperature":29.5,
    "humidity":64.0,
    "rainDetected":false,
    "clothesDryPercentage":null,
    "rainRaw":3300
  },
  "positionSource":"COMMAND_ESTIMATE",
  "positionMeasured":false,
  "rainProtectionEnabled":true,
  "buzzerEnabled":true,
  "motionReason":"BOOT_HOMING",
  "rainThreshold":1900,
  "rainWetWhenAnalogBelow":true
}
```

The app consumes `state`, `firmwareVersion`, and the three required sensor fields. Extra fields are ignored. The sketch returns HTTP 503 with an error JSON when the DHT22 has no valid sample; it never labels an old DHT sample as current.

Valid states are `EXTENDED`, `RETRACTED`, `EXTENDING`, `RETRACTING`, `STOPPED`, `ERROR`, and `UNKNOWN`.

### `POST /api/commands`

Content-Type: `application/json`:

```json
{"action":"EXTEND","requestId":"65c95e0e-05cf-42c9-881a-441280a8ff24"}
```

Actions are `EXTEND`, `RETRACT`, and `STOP`. Accepted commands return HTTP 200:

```json
{"accepted":true}
```

Rejected commands return HTTP 409 with `accepted:false` and a reason. The request ID is cached for ten minutes, so a duplicate request receives the same response without moving the servo twice.

### `POST /api/buzzer` (firmware 1.1.0+)

```json
{"enabled":false,"requestId":"buzzer-001"}
```

The response is `{"accepted":true,"buzzerEnabled":false}`. The app changes its displayed setting only after a matching acknowledgement, then keeps it synchronized with status polling. Disabling stops the buzzer immediately. Enabling emits a short test beep. The enabled flag persists in ESP32 NVS; duplicate request IDs do not beep twice. The specified hardware is a passive buzzer on GPIO19.

**Update both the Android APK and this sketch.** Older firmware lacks this endpoint; the app leaves the switch disabled and explains that an update is required.

The app still does not send mode, rain-protection, or physical rain-threshold settings. Those remain local firmware configuration. The Home Auto switch now changes the app's local mode rather than just navigating to Controls. Selecting Auto with a dry sensor and schedules off sends no movement request.

The sketch has a firmware-only `STARTUP_MODE` (`MODE_AUTO` or `MODE_MANUAL`) and reports it as an extra `mode` field. Since the current app never sends a mode, changing the app’s local Auto/Manual control does not change this ESP32 variable. Movement commands remain compatible in either mode, while confirmed rain protection always has priority.

Because this hardware has no limit or position sensor, `EXTENDED` and `RETRACTED` mean the configured servo travel command completed. `positionSource` makes that limitation explicit. Add physical limit switches and obstruction/current protection before unattended installation.

## Libraries and board package

In Arduino IDE Library Manager install:

- **DHT sensor library by Adafruit**, 1.4.6 or newer
- **Adafruit Unified Sensor**, 1.1.15 or newer
- **ESP32Servo**, 3.0.6 or newer

Install **esp32 by Espressif Systems 3.x** through Boards Manager. `WiFi.h` and `WebServer.h` come from that board package. The passive buzzer uses the ESP32 core-compatible `tone(pin, frequency, duration)` and `noTone(pin)` functions; no LEDC compatibility shim is needed.

## Wi-Fi and upload

1. Open `firmware/SmartDryingRack/SmartDryingRack.ino` in Arduino IDE.
2. Replace `WIFI_SSID` and `WIFI_PASSWORD` with the 2.4 GHz network credentials used by the phone.
3. Select **Tools → Board → esp32 → ESP32 Dev Module**.
4. Select the correct COM port. Use 115200 baud in Serial Monitor.
5. Select the normal flash settings for the expansion board and click Upload. Hold BOOT if the board waits for download mode.
6. Read the assigned IP address in Serial Monitor or your router. Enter `http://<private-ip>` in the Android app’s connection screen.

The Android app accepts plain HTTP only for a private IPv4 address. Do not port-forward this unauthenticated LAN API to the internet.

## Calibration and commissioning

1. Disconnect the servo horn or clear the mechanism. Set `RETRACT_ANGLE` to the mechanically sheltered endpoint and `EXTEND_ANGLE` to the drying endpoint. Keep both between 0 and 180 degrees.
2. Set `SERVO_SPEED_DEG_PER_SECOND` so the configured travel time is below the physical travel limit. Increase `MAX_MOTION_TIME_MS` only after checking the mechanism; it is a safety timeout, not a position measurement.
3. Read `sensors.rainRaw` from `/api/status` or the updated app Controls screen to observe AO. Record dry and wet ADC values. Set `RAIN_WET_WHEN_ANALOG_BELOW`, `RAIN_THRESHOLD`, and `RAIN_HYSTERESIS` between those values. If using DO, set `USE_RAIN_DIGITAL_OUTPUT` and `RAIN_DIGITAL_ACTIVE_LOW` instead.
4. Leave `RAIN_CONFIRM_READINGS` at 3 and `RAIN_CLEAR_READINGS` at 5 initially. This confirms rain after three wet samples and clears only after five dry samples, preventing relay-like movement from fluctuating water readings.
5. Confirm DHT22 readings are plausible before testing movement. A failed DHT sample makes the API unavailable and blocks extension, while the local rain loop keeps running.
6. Test `RETRACT`, `EXTEND`, and `STOP` with the rack unloaded. Test rain while the phone and Wi-Fi are disconnected. Verify a physical emergency stop, mechanical end stops, and obstruction protection before enabling unattended use.

The first boot immediately commands the configured retracted angle and exposes `RETRACTING` during the conservative boot travel window. Since no position sensor is installed, inspect the mechanism after commissioning and do not treat the terminal state as proof that a limit switch was reached.

## Quick API checks

```bash
curl http://192.168.1.50/api/status
curl -X POST http://192.168.1.50/api/commands \
  -H 'Content-Type: application/json' \
  -d '{"action":"RETRACT","requestId":"commission-001"}'
```

## Unexpected retraction diagnosis

`motionReason` identifies the last device movement trigger: `BOOT_HOMING`, `RAIN_PROTECTION`, or `REST_COMMAND`. Boot homing remains enabled because this hardware has no position sensor; reconnecting the app does not itself home the servo. Repeated `GET /api/status` requests never move it.

The app now requires a fresh, currently wet reading plus rain confirmation before issuing rain-triggered retraction. An old wet debounce latch cannot issue retraction after the device reports dry. Scheduled and explicitly requested movements remain separate.

If a dry rack still retracts, capture `motionReason`, `sensors.rainDetected`, `sensors.rainRaw`, and `rainThreshold` immediately afterwards. `BOOT_HOMING` indicates a reboot/startup; `RAIN_PROTECTION` points to physical sensor calibration; `REST_COMMAND` indicates a client command. Record raw dry and wet values before changing threshold or polarity. Do not disable rain protection to hide the symptom.

## Validation

Android build, unit/UI tests and lint cover the new endpoint, acknowledged setting state, dry-rain command blocking, and functional Home Auto/Buzzer switches. Host firmware logic tests execute the real sketch with substitutes for GPIO, HTTP and NVS:

```bash
g++ -std=c++17 -Ifirmware/tests/stubs firmware/tests/firmware_test.cpp -o /tmp/smart-rack-firmware-test
/tmp/smart-rack-firmware-test
```

These are not physical hardware tests or an ESP32 target compile. The cloud target build currently requires allowing `downloads.arduino.cc` in environment network settings; the domain addition is saved in the environment draft for review/save/publication. Compile and upload with the documented ESP32 board/libraries in Arduino IDE, retaining your own Wi-Fi credentials and calibrated servo angles.
