# App and firmware compatibility check — 2026-10-26

## Verified software

Firmware 1.1.7 compiled for classic ESP32 (`esp32:esp32:esp32:FlashMode=dio`) with Arduino-ESP32 3.0.7 and ESP32Servo 3.0.6. Flash: 1,000,917 bytes (76%); static RAM: 48,552 bytes (14%).

All seven host suites passed with both house-light polarity settings (14 executions): core APIs, six-zone isolation, rain/AUTO/MANUAL/STOP, common-cathode RGB, buzzer mode policy, injected tone failures, and real firmware contract export. These execute the sketch against simulated GPIO/Wi-Fi with the real ArduinoJson library.

Android debug build passed. All 129 tests passed, with no failures/errors/skips; lint reported zero errors. The suite includes small-screen/large-text controls, repeated connection-drop recovery, command confirmation, older-response rejection, unsupported firmware, and RGB read-only controls.

`contract_export_test.cpp` generated `app/src/test/resources/firmware-contract-v1.1.7.json` from the real sketch. `FirmwareContractTest` feeds those exact status/config/command/buzzer/lighting responses through Android JSON and the app HTTP clients using a loopback HTTP server. It verifies AUTO with the manual mute preference OFF, MANUAL OFF/ON, movement acceptance, lighting acknowledgement/confirmation, zone independence and automatic RGB state. Loopback HTTP is not an ESP32 radio test.

## Defect corrected

Tone-generation return values were ignored, so the buzzer test and enable endpoint could acknowledge success after peripheral failure. Positive-frequency writes are now checked. Failure attempts to mute output, clears pattern state, latches `buzzerToneFault:true` and `buzzerAvailable:false`, and returns HTTP 503 instead of a successful test/enable acknowledgement. A failed enable restores the previous manual preference. Failure during a later pulse is also detected. Recovery requires reboot/reinitialization; requests are not replayed automatically. A buzzer fault does not stop protective rack retraction; STOP still works.

The app strictly parses optional boolean `buzzerAvailable`, refreshes it from telemetry and clears it on disconnect. It disables the sound switch and reports unavailable output when false, including AUTO. Missing availability on older firmware remains unknown. This avoids calling an unavailable output working just because AUTO requires sound.

`buzzerEnabled` remains a manual preference. AUTO requires movement sound; MANUAL obeys that preference. Sound permission is not proof of PWM generation or audible sound. Nonzero timer frequency confirms peripheral configuration only; the firmware has no microphone/current feedback.

## Pin and resource inspection

No overlap in the configured signal pins: DHT4; CR16; back17; servo18; buzzer19; right21; left22; front23; balcony25; RGB26/32/33; rain AO34 and optional DO27. GPIO34 is input-only and is used as ADC input. Servo uses high-speed timer0; house PWM uses channels2–7/timers1–3; buzzer uses channel14 in the other speed group. RGB uses digital outputs, not additional PWM channels. This allocation is for classic ESP32 WROOM-32, not every ESP32 variant.

## Physical checks still required

The user's hardware is not accessible from this cloud environment. Audible buzzer output, actual LED current/polarity, ADC noise, servo movement and supply/radio stability have NOT been physically tested.

Known wiring issue: the six bare house LEDs were reported connected without series resistors. Leave them disconnected until each has a correctly sized resistor and a GPIO-compatible circuit. RGB also needs one resistor per color. The RGB common-cathode configuration follows the supplied diagram; actual leg identification still needs verification. The two-pin buzzer is connected between D19 and GND, but its voltage/current/type specifications and need for a transistor driver remain unconfirmed. Do not power an unidentified load directly from GPIO to test it. Verify servo supply capacity and common ground before loading the system.

After the circuit is correctly rated and fitted:

1. Upload firmware 1.1.7 with local Wi-Fi credentials; install the matching app. Check `/api/config` and `/api/diagnostics`, particularly mode, buzzerEnabled, buzzerAutoRequired, buzzerEffectiveEnabled, buzzerAvailable and buzzerToneFault.
2. MANUAL + sound OFF: extend/retract without a movement beep. MANUAL + sound ON: each start should beep. AUTO: both movements should beep even after returning from MANUAL OFF. STOP cancels the pattern. Restore MANUAL and confirm the previous preference remains.
3. Confirm RGB state matches `/api/lights`; errors/rain may override other colors according to priority. Change one house zone at a time at 0/10/100%; confirm other zones and RGB remain independent.
4. Test disconnect/reconnect while idle; verify state refresh and no old command replay. Capture Serial Monitor at 115200 during any timeout. Report resets/brownout messages or changes in WiFi status, without credentials.

A clean diagnostic flag does not prove the buzzer is audible. If software reports healthy PWM but the buzzer remains silent, investigate the rated buzzer, driver, wiring or damaged GPIO with appropriate electrical measurements.
