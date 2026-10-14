# Connection stability review

## App checks

`ConnectionStabilityTest` exercises the production Wi-Fi controller and REST client against a local HTTP server. Only the test transport maps the validated private device URL to loopback. The production constructor still uses its original REST client and IO coroutine scope.

All six targeted checks passed:

- Automatic recovery across 20 status failures: 41 status requests, with every failure marking the connection unavailable and every subsequent valid response restoring it.
- Malformed telemetry stays unavailable until a valid response arrives.
- A response body delayed beyond the 2.5-second read timeout fails, then the next status retry restores connection.
- Explicit Disconnect cancels polling and clears position, readings, and firmware metadata.
- Ten consecutive Connect calls leave a single polling loop.
- A rejected motor command is sent once; status polling can subsequently restore connection without resending the command.

Polling waits two seconds after success and five seconds after failure. These intervals are in addition to HTTP request duration. Connect and read each have a 2.5-second timeout; they are not a single 2.5-second deadline for the complete request. Automation requires readings no older than six seconds. Motor requests are not intentionally retried.

## Firmware inspection

The ESP32 sketch enables automatic Wi-Fi reconnection and explicitly retries association every ten seconds without a blocking connection loop. Its main loop continues sensor and local rain/servo updates. This is source inspection, not a firmware compile or physical radio/power test.

Important practical limits:

- A failed DHT22 sample returns HTTP 503. The app currently reports this as unavailable/connection error, so this indicator alone does not prove Wi-Fi disconnected.
- The app stores a fixed device address. A DHCP address change needs an updated app address; a router DHCP reservation avoids that change.
- Phone polling only runs while the application process can execute. Background suspension or process termination can interrupt it; local firmware protection remains independent.
- The sketch logs API startup, but does not currently log Wi-Fi reconnection events, IP changes, or signal strength.

## Physical check — awaiting user results

1. With the phone and ESP32 on the same Wi-Fi, open `http://<rack-IP>/api/status` on the phone. Confirm valid telemetry; record any HTTP error.
2. Connect the app and leave the rack stationary. Observe the connection for 15 minutes and count any unexpected connection errors.
3. Turn the phone's Wi-Fi off for ten seconds, restore the same network, return to the app and allow up to 30 seconds for recovery. Repeat five times. Record each recovery time and whether tapping Connect was required. Thirty seconds is a diagnostic observation window, not a guaranteed timeout.
4. When convenient, repeat a reconnect test with the ESP32 restarting and confirm its router-assigned IP has not changed. A reboot can command startup retraction: keep the mechanism clear and only perform this when safe for the installation.
5. During ordinary supervised movement, note whether failures coincide with servo activity or ESP32 resets. The documented hardware requires suitable external servo power with a common ground; the ESP32 3.3 V pin must not supply the servo.

Physical signal quality, power stability, actual firmware installation, Android Wi-Fi routing and end-to-end reconnect timing remain unverified until those results are supplied. Do not describe the actual hardware as stable based only on the local server tests.

Run software verification from the repository root:

```bash
source /workspace/.cloud-tools/activate.sh
cloud_gradle :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```
