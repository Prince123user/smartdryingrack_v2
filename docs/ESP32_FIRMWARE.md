# ESP32 firmware 1.1.2 — merged from the uploaded working sketch

`firmware/SmartDryingRack/SmartDryingRack.ino` is the complete updated sketch, based on the supplied **SmartDryingRack_complete.ino** (lighting version 1.1.0 / rack revision 1.0.2). The original uploaded file is unchanged. Repository Wi-Fi literals are placeholders; enter your own credentials locally before uploading and never commit them.

## What changed

- Preserved the uploaded GPIOs, calibration (retract 0°, extend 90°), stable-dry AUTO extension, rain protection, latched STOP, servo movement, DHT diagnostics, Wi-Fi retry and nonblocking buzzer patterns.
- Kept GET/POST `/api/lights` and its legacy `lights`/`statusIndicator` response fields.
- Added schema-2 fields to GET `/api/lights`, with per-zone revisions, availability, last request identity and a boot session. Added six POST `/api/lights/zones/{id}` routes used by the app.
- Added POST `/api/buzzer` to match the app, while keeping `/api/config` and `/api/buzzer/test`. Status now also reports `buzzerEnabled` and `sensors.rainRaw` in the app's expected locations.
- Added DHCP hostname `smart-drying-rack`. Existing Serial output prints **Device URL: http://CURRENT-IP** when Wi-Fi connects. A previous IP such as `.92` is not guaranteed after DHCP changes.
- Release 1.1.5 enables common-cathode RGB output (`STATUS_RGB_ENABLED=true`, `STATUS_RGB_COMMON_ANODE=false`) on R26/G32/B33 following the user-supplied diagram. Use one current-limiting resistor per color and common GND. The API reports configured output state, not optically measured illumination.

This supersedes the earlier repository sketch and external-PWM proposal. **Do not combine the previous PCA9685 wiring plan with this sketch:** GPIO21/22 are house-light outputs here, not I²C pins. No external PWM driver is required by this uploaded design; electrical load drivers and LED current limits are still required as appropriate.

## Exact GPIO assignments from the uploaded source

| Function | GPIO | PWM allocation |
| --- | --- | --- |
| Rain AO | 34 | ADC input only |
| Optional rain DO | 27 | Reserved input |
| DHT22 DATA | 4 | Existing sensor connection |
| Servo signal | 18 | First ESP32Servo attachment, 50 Hz |
| Passive buzzer | 19 | LEDC channel 14, low-speed group timer 3 |
| Front | 23 | LEDC channel 2, high-speed timer 1 |
| Left side | 22 | LEDC channel 3, high-speed timer 1 |
| Right side | 21 | LEDC channel 4, high-speed timer 2 |
| Back | 17 | LEDC channel 5, high-speed timer 2 |
| CR | 16 | LEDC channel 6, high-speed timer 3 |
| Balcony | 25 | LEDC channel 7, high-speed timer 3 |
| Automatic common-cathode RGB R/G/B | 26 / 32 / 33 | Digital outputs only; no dimming |

House PWM is 1 kHz, 8-bit. Channel pairs share frequency/resolution; all six house channels intentionally use the same values. With pinned Arduino-ESP32 3.0.7 and ESP32Servo 3.0.6, setup attaches the single servo before other LEDC users. Core `ledcAttach` chooses the first free channel (0), which uses high-speed timer 0. Buzzer channel 14 is in the other speed group; its variable frequency does not share the house high-speed timers. Do not add another servo, call additional timer allocation or introduce automatic LEDC consumers without reviewing this allocation again. This mapping is for classic ESP32 WROOM-32, not S2/S3/C3/WROVER alternatives.

Software PWM duty confirms a commanded electrical output, not measured illumination. Individual PWM initialization/write failures are reported unavailable; the app disables that zone while retaining other zones. See [wiring and required electrical details](LIGHTING_WIRING.md) before connecting LED loads.

## Build and upload

Pinned validation target: Arduino-ESP32 **3.0.7**, board **ESP32 Dev Module**, Flash Mode **DIO**; ESP32Servo **3.0.6**, DHT sensor library **1.4.6**, Adafruit Unified Sensor **1.1.15**, ArduinoJson **7.4.2**.

Open the sketch folder in Arduino IDE, install those versions, configure your own 2.4 GHz Wi-Fi SSID/password and upload to the correct board/port. Back up the currently working flashed source and local calibration first. Keep the mechanism clear: the firmware retracts during startup. Initial calibration must be tested unloaded before attaching the servo to the mechanism.

CLI equivalent:

```bash
arduino-cli compile --fqbn esp32:esp32:esp32:FlashMode=dio --jobs 4 firmware/SmartDryingRack
```

In the prepared cloud workspace:

```bash
export PYTHONPATH=/workspace/.cloud-tools/arduino/python-deps
export XDG_CACHE_HOME=/workspace/.cloud-tools/arduino/cache
/workspace/.cloud-tools/arduino/arduino-cli --config-file /workspace/.cloud-tools/arduino/arduino-cli.yaml compile \
  --fqbn esp32:esp32:esp32:FlashMode=dio --jobs 4 firmware/SmartDryingRack
```

Open Serial Monitor at **115200 baud**. Wait for `Device URL: http://...`, then use that current base URL in the app's Connection settings. “API started; waiting for Wi-Fi” alone does not prove a successful connection. If no URL appears, retain the Wi-Fi status/reset logs and diagnose power/network association. A build or API compatibility fix cannot establish physical LAN reachability.

## Behavioral details preserved from the upload

AUTO extends after 10 seconds of continuous readiness: valid/fresh DHT, dry rain readings/debounce, retracted and idle. A wet sample cancels the dry timer. MANUAL prevents automatic extension; enabled rain protection can still retract unless STOP is latched. STOP holds in either mode until a fresh accepted movement command resumes it. Device mode and protection settings remain available through GET/POST `/api/config`; the updated app reads the actual device mode from telemetry and sends Auto/Manual changes through `/api/config`. It keeps the displayed mode unchanged until confirmation, shows pending/error feedback, and blocks movement during a mode change. Legacy firmware without mode telemetry retains local phone-mode behavior. Phone rain commands do not interrupt firmware-owned rain protection, and scheduled/automatic phone movement respects a reported firmware STOP latch.

House lights reboot **OFF**, with selected brightness **100%** in RAM. OFF preserves brightness; ON at 0% has no visible output. Lighting and device config/buzzer settings are not persisted across reboot in this uploaded implementation. This replaces the old repository's NVS buzzer-persistence behavior with the uploaded source's existing RAM-only policy; the new compatibility route follows that policy.

Existing routes retained: GET `/api/status`, POST `/api/commands`, GET/POST `/api/config`, GET `/api/diagnostics`, POST `/api/buzzer/test`, GET/POST `/api/lights`. New app-compatible routes are additive. `/api/diagnostics` works even when DHT is unavailable; `/api/status` may return 503 for invalid/stale DHT readings. No clothes-moisture measurement or physical position feedback is invented.

## API examples

See [lighting contract](LIGHTING_API.md). Example commands, replacing `CURRENT-IP` and reading actual revisions/session first:

```text
GET  http://CURRENT-IP/api/lights
POST http://CURRENT-IP/api/lights
{"id":"front","power":true,"brightness":40}

POST http://CURRENT-IP/api/lights/zones/front
{"requestId":"new-uuid","deviceSession":"BOOT-TOKEN-FROM-GET","expectedRevision":0,"power":true,"brightness":40}

POST http://CURRENT-IP/api/buzzer
{"requestId":"new-uuid","enabled":true}

POST http://CURRENT-IP/api/config
{"mode":"MANUAL"}
```

The legacy lighting POST is preserved for existing clients; the mobile app uses the guarded per-zone POST and confirms via a subsequent GET. No command can target the automatic RGB light. HTTP requests remain unencrypted private-LAN traffic; keep this interface on the trusted local network.

## Validation scope

The Android build/tests/lint and host tests exercise real firmware code with substitute hardware and the real ArduinoJson library. The target build is separate evidence: compiling for ESP32 does not test GPIO voltage/current, the circuit, radio connection, servo travel or physical LEDs. Verify the final reported target-build result before flashing; no physical validation has been performed in cloud.

Host regression command (ArduinoJson installed as above):

```bash
g++ -std=c++17 -Ifirmware/tests/stubs \
  -I/workspace/.cloud-tools/arduino/user/libraries/ArduinoJson/src \
  firmware/tests/firmware_test.cpp -o /tmp/smart-rack-firmware-test
/tmp/smart-rack-firmware-test
```

Test real hardware with the original rack baseline first, then add one properly driven/current-limited LED at a time. Verify all six zones independently, distinct brightness values, zero, OFF/ON retention, reboot-OFF, loss/recovery of Wi-Fi, PWM availability failures, and status-priority overlaps. Recheck dry extension, rain retraction, latched STOP, manual commands, sensor diagnostics and buzzer patterns during lighting traffic. Confirm RGB type/pinout before enabling its optional code.

An explicit STOP can still be attempted at the actively requested rack address when telemetry is in error (for example DHT HTTP 503). It must receive an actual command acknowledgement; failure remains unconfirmed. An explicit Disconnect disables these attempts. Emergency STOP is best-effort software control, not proof of physical motion cessation.

## Release 1.1.2

Combines the latest uploaded status improvements with the app-compatible APIs. Moisture awaiting debounce reports RAIN_PENDING; moving, extended, retracted, stopped and unknown positions get accurate logical labels. A meaning change advances lighting stateVersion even if its color stays the same. DHT failure remains HTTP 503, with null temperature/humidity and extra rack/rain diagnostics. The current app treats that as a sensor/status failure and does not consume the additional error-body fields; it never parses null readings as valid telemetry. The schema-2 lighting response, guarded zone routes, buzzer toggle, healthy status fields and rack functions are retained.

Build target: ESP32 WROOM-32 / DIO, core 3.0.7 and pinned libraries listed above. Version 1.1.2 was compiled for that target and host-simulated, including every house-light route and the new status transitions. Physical operation remains unverified.

## Release 1.1.5 — automatic RGB enabled

Common cathode connects to GND. Red goes through its own resistor to GPIO26, green through its own resistor to GPIO32, and blue through its own resistor to GPIO33. Resistor values require the LED ratings and desired GPIO-safe current; do not use the earlier resistorless house-light arrangement. The physical part must match the supplied diagram.

Existing automatic mapping and priority remain: servo/sensor errors (yellow) override rain (red), then STOP (yellow), then Wi-Fi connecting (blue), pending rain (yellow), movement (cyan), extended/dry (green), and retracted idle (white). Unknown position is yellow. There are no app manual controls for RGB and house commands cannot target it. No new PWM channels are used for this digital RGB output. Rain, buzzer, rack and house-light polarity behavior are unchanged.

Target compilation and host simulated-GPIO checks do not establish physical LED operation. Configure local Wi-Fi before uploading and verify the device reports firmware 1.1.5 with indicator state in `/api/lights`.

## Release 1.1.6 — buzzer policy by mode

Every accepted movement start requests a short beep: retract 1800 Hz / 350 ms, extend 2400 Hz / 350 ms. A higher-priority rain pattern already playing may take precedence. AUTO always permits sound regardless of the manual mute preference. MANUAL uses `buzzerEnabled` to allow or mute sound. STOP still cancels the current pattern. Startup keeps its two chirps. A failed PWM output or unsuitable electrical circuit can still prevent audible sound.

`buzzerEnabled` remains the manual preference in existing GET/POST contracts, retained across mode changes in RAM (not persisted through reboot). Healthy `/api/status`, `/api/config`, and `/api/diagnostics` add `buzzerAutoRequired:true` and `buzzerEffectiveEnabled` (the current mode's sound permission, not measured audio). Old `/api/buzzer` acknowledgements still confirm the manual preference. Writing false while AUTO does not mute an active pattern; entering MANUAL with that preference stops it. The legacy `buzzerOnMovement` setting is accepted for compatibility but no longer suppresses movement beeps: the mode and `buzzerEnabled` define this policy.

The matching app locks the switch ON in AUTO only when this capability is device-reported. In MANUAL it displays the retained preference and enables the existing acknowledged toggle. Older firmware gets an update message instead of a claimed automatic override. Update both firmware and app for the complete experience.

Validation: simulated extend/retract with both modes and both preferences, mode transitions, legacy movement-flag override, STOP, API policy parsing, and AUTO switch lockout. Physical sound and the unidentified two-pin buzzer's drive requirements remain unverified. Keep resistorless house LEDs disconnected until suitable per-LED resistors are fitted.

## Release 1.1.7 — compatibility verification and tone failure reporting

Positive-frequency buzzer writes are checked. A failed write attempts to mute GPIO19, marks PWM unavailable, latches `buzzerToneFault`, and stops the pattern. The buzzer test/enable endpoint returns HTTP 503 for detected tone failure; a failed enable restores the previous manual preference. The app displays unavailable output instead of a working sound switch. Reboot/reinitialization is required after a latched tone fault. AUTO/MANUAL policy, RGB and other rack controls are retained. See `COMPATIBILITY_CHECK_1.1.7.md` for measured software results and remaining physical checks.
