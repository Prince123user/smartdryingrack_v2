# Firmware 1.1.4: output polarity correction

The user reports physically reversed house-light switches (app OFF lights the LED, app ON extinguishes it). Release 1.1.4 defaults `HOUSE_LIGHT_ACTIVE_LOW` to 1 for all six zones to match that observation. Each entry in `HOUSE_LIGHT_ACTIVE_LOW_BY_ZONE` can instead be set separately if circuits differ. For active-high circuits define `HOUSE_LIGHT_ACTIVE_LOW` as 0 before its `#ifndef`, or build with `-DHOUSE_LIGHT_ACTIVE_LOW=0`. The circuit has not yet been electrically verified. This change does not determine wire polarity, LED current ratings, or supply voltage.

Both initialization and every brightness/power update use the same polarity conversion. OFF and ON-at-0% write the electrically inactive duty. Logical power and remembered brightness remain unchanged in the API; the phone should not invert its switches. The `lights` diagnostic array now includes `activeLow`.

At 1000 Hz, expected `pwmDutyReadback` for ON at 0%, 10%, 100% is **255, 229, 0 for active-low**, or **0, 26, 255 for active-high**. Readback is a register value, not measured illumination. The firmware cannot identify automatically whether the actual LED circuit is active-low.

A resistor-limited ordinary LED sinking into GPIO may use a 3.3 V source, subject to the LED ratings and ESP32 current budget; a 5 V source must not directly reach GPIO and needs a suitable driver/interface. Ratings/resistors remain unconfirmed from photos. Disconnect power before checking wiring.

If the phone's selected/device-confirmed number itself stays at 100%, polarity alone cannot explain that: collect the pending/error message and `/api/lights` JSON after a slider change. If percentages and duty change correctly but visible intensity does not, investigate the physical output path. No Android change is required for polarity conversion.

All three host suites run in both polarity configurations. The default active-low release is compiled for classic ESP32 with the pinned libraries. Physical dimming and rain protection still require validation.

## Rain and mode behavior retained from 1.1.3


## What changed

Rain AO now uses a rolling median of five readings, sampled every 250 ms without delays. Hysteresis and consecutive-reading confirmation remain. `rainRaw` is the latest ADC value; `/api/diagnostics` adds `rainFiltered`, `rainFilterSamples`, `rainSamplePeriodMs`, `rainWetWhenBelow`, and `rainHysteresis`. Serial prints both raw and filtered values. With a full dry window, sustained wet input confirms after five wet ticks (about 1.25 seconds); sustained dry clears after seven dry ticks (about 1.75 seconds). Startup and samples around the hysteresis band can differ. The median rejects isolated impulses; it cannot correct a sustained false signal or wrong threshold.

The default threshold is unchanged: filtered AO <=1900 enters wet, >=2080 exits wet. Measure dry and wetted values on the actual plate before selecting a threshold between their ranges. Do not blindly lower the threshold until false rain disappears: that may also hide real rain. `/api/config` accepts an integer `rainThreshold` from 180 to 3915; this setting remains RAM-only and resets on reboot. Keep AO within the ESP32's 3.3 V input range, connect AO to GPIO34, and share ground. A GPIO34 input without a connected AO can float. If raw/filtered dry and wet ranges overlap, fix the sensor, supply or wiring before relying on protection.

AUTO extends only after confirmed dry, valid/fresh DHT readings, and ten seconds of continuous readiness while retracted. MANUAL never extends on this timer. Enabled rain protection still retracts in either mode. STOP remains latched in either mode; a fresh accepted movement request releases it. A wet EXTEND request is rejected while protection is enabled.

## Brightness diagnosis

For the earlier active-high configuration, house outputs use PWM on GPIOs front23/left22/right21/back17/cr16/balcony25, 1000 Hz, 8-bit. Servo and buzzer allocation is unchanged. Each zone still has independent power and retained brightness; brightness changes while OFF do not illuminate the LED.

With a zone ON, select 0%, 10%, then 100% in the app and wait for the device-confirmed line after each selection. Copy `/api/lights` at each step. The additional fields in its legacy `lights` array are `gpio`, `pwmDutyReadback` (0..255), and `pwmFrequencyHz`. `outputDuty` remains the calculated requested duty. For active-high expected readback is 0, 26, 255; for the 1.1.4 active-low default it is 255, 229, 0, at 1000 Hz. These are peripheral register readings, not measured current or illumination.

- If confirmed brightness never changes, report the app error and JSON; investigate command/confirmation before rewiring.
- If confirmed brightness and duty change but physical light does not, check which GPIO and ground actually reach that LED, its series resistor, and any driver. Do not infer polarity from wire colors. An LED tied to constant power bypasses PWM; a GPIO-to-supply arrangement may invert control and requires the active-low configuration and a voltage-compatible circuit.
- If frequency or duty disagrees with the requested output, report the JSON and firmware boot log. Do not add additional PWM/servo consumers without reviewing timer allocation.
- Brightness perception is nonlinear: use 0%, 10%, 100% rather than comparing nearby high values.

Bare two-lead LEDs require individual current-limiting resistors sized from their ratings. The photos do not establish ratings or whether a resistor is hidden under heat-shrink. The four-lead RGB indicator remains disabled until its common pin/type and channel pins are identified.

## Validation boundary

Host tests simulate sensor waveforms, AUTO/MANUAL/STOP, and every integer brightness from 0 through 100 for six outputs. Existing route-isolation tests remain. The ESP32 target build checks real library compatibility. Neither test measures this user's wiring, ADC interference, servo travel, LED current, or illumination; physical dry/wet and PWM checks are required.
