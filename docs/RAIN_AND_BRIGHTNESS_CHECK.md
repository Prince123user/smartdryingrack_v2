# Firmware 1.1.3: rain and brightness checks

## What changed

Rain AO now uses a rolling median of five readings, sampled every 250 ms without delays. Hysteresis and consecutive-reading confirmation remain. `rainRaw` is the latest ADC value; `/api/diagnostics` adds `rainFiltered`, `rainFilterSamples`, `rainSamplePeriodMs`, `rainWetWhenBelow`, and `rainHysteresis`. Serial prints both raw and filtered values. With a full dry window, sustained wet input confirms after five wet ticks (about 1.25 seconds); sustained dry clears after seven dry ticks (about 1.75 seconds). Startup and samples around the hysteresis band can differ. The median rejects isolated impulses; it cannot correct a sustained false signal or wrong threshold.

The default threshold is unchanged: filtered AO <=1900 enters wet, >=2080 exits wet. Measure dry and wetted values on the actual plate before selecting a threshold between their ranges. Do not blindly lower the threshold until false rain disappears: that may also hide real rain. `/api/config` accepts an integer `rainThreshold` from 180 to 3915; this setting remains RAM-only and resets on reboot. Keep AO within the ESP32's 3.3 V input range, connect AO to GPIO34, and share ground. A GPIO34 input without a connected AO can float. If raw/filtered dry and wet ranges overlap, fix the sensor, supply or wiring before relying on protection.

AUTO extends only after confirmed dry, valid/fresh DHT readings, and ten seconds of continuous readiness while retracted. MANUAL never extends on this timer. Enabled rain protection still retracts in either mode. STOP remains latched in either mode; a fresh accepted movement request releases it. A wet EXTEND request is rejected while protection is enabled.

## Brightness diagnosis

No brightness or wiring polarity change is assumed. Existing house outputs are active-high PWM, GPIOs front23/left22/right21/back17/cr16/balcony25, 1000 Hz, 8-bit. Servo and buzzer allocation is unchanged. Each zone still has independent power and retained brightness; brightness changes while OFF do not illuminate the LED.

With a zone ON, select 0%, 10%, then 100% in the app and wait for the device-confirmed line after each selection. Copy `/api/lights` at each step. The additional fields in its legacy `lights` array are `gpio`, `pwmDutyReadback` (0..255), and `pwmFrequencyHz`. `outputDuty` remains the calculated requested duty. Expected readback is 0, 26, 255 respectively, at 1000 Hz. These are peripheral register readings, not measured current or illumination.

- If confirmed brightness never changes, report the app error and JSON; investigate command/confirmation before rewiring.
- If confirmed brightness and duty change but physical light does not, check which GPIO and ground actually reach that LED, its series resistor, and any driver. Do not infer polarity from wire colors. An LED tied to constant power bypasses PWM; a GPIO-to-supply arrangement may invert control and does not match the active-high design.
- If frequency or duty disagrees with the requested output, report the JSON and firmware boot log. Do not add additional PWM/servo consumers without reviewing timer allocation.
- Brightness perception is nonlinear: use 0%, 10%, 100% rather than comparing nearby high values.

Bare two-lead LEDs require individual current-limiting resistors sized from their ratings. The photos do not establish ratings or whether a resistor is hidden under heat-shrink. The four-lead RGB indicator remains disabled until its common pin/type and channel pins are identified.

## Validation boundary

Host tests simulate sensor waveforms, AUTO/MANUAL/STOP, and every integer brightness from 0 through 100 for six outputs. Existing route-isolation tests remain. The ESP32 target build checks real library compatibility. Neither test measures this user's wiring, ADC interference, servo travel, LED current, or illumination; physical dry/wet and PWM checks are required.
