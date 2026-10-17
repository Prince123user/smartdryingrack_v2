# Rack unreachable from both phone browser and app

A timeout connecting to `192.168.1.92:80` means that address did not complete the TCP connection. It does **not** establish a bad lighting JSON response or missing lighting endpoint. If the phone browser also cannot open `/api/status`, first restore basic rack connectivity; an APK update cannot power the ESP32, repair wiring or find its changed DHCP address automatically.

## Restore the known working baseline

1. Turn power off before changing wiring. Temporarily remove only the newly added lighting connections and return to the previously working rack arrangement. The lighting wiring plan is provisional: it is not a confirmed diagram for the user's unidentified expansion board and LED parts. Do not connect an unconfirmed RGB pinout or resistorless bare LEDs from that plan.
2. Power the original rack normally. Keep the mechanism clear: the repository sketch moves the servo during boot. Record USB Serial Monitor startup output at 115200 baud. Reset/brownout messages, repeated startup or Wi-Fi failures can help distinguish a power/startup problem from an address issue; these causes are not established by the screenshot alone. Remove Wi-Fi credentials before sharing logs.
3. Confirm the ESP32 appears in the router's connected-device/DHCP list and obtain its **current IP**. Do not assume the previous `.92` address remains assigned. The repository sketch prints “API started” but does not print its IP; that message alone does not mean Wi-Fi connected. The actual flashed working source is still needed for a precise firmware diagnosis.
4. Use the ESP32's supported 2.4 GHz Wi-Fi and put the phone on the same local network. Avoid a guest network/client-isolation setting that prevents devices from reaching each other. From that phone, open `http://CURRENT-IP/api/status`.
5. Save that current base URL in the app's **Connection settings**, then Save and connect. House Lights now links directly to this screen. It refetches actual state on return/reconnect and retries read failures automatically, without replaying light commands.

Do not increase GPIO loads, change power rails or rewrite working rain/STOP/servo behavior to hide a connection symptom. Before final lighting wiring, supply the actual flashed `.ino`, expansion-board model/pinout or clear photographs, each LED's voltage/current/resistor information, and RGB part/pinout. The final driver/circuit and firmware merge remain pending those inputs.

## Interpret the response

| Observation | Meaning / next step |
| --- | --- |
| Browser and app time out or connection is refused | Verify actual IP, power/boot, Wi-Fi association, HTTP server and LAN reachability. No lighting state can be confirmed. |
| `/api/status` returns HTTP 503 sensor failure | The HTTP server is reachable; inspect the sensor error. A sensor error is different from a TCP timeout. |
| `/api/status` returns JSON, `/api/lights` returns 404/501 | Rack is reachable, but compatible lighting support is absent. Install a correctly integrated firmware after its source/hardware are confirmed. |
| `/api/lights` returns another HTTP error | Server reached; investigate its lighting service/driver error. |
| `/api/lights` returns incompatible JSON | Firmware must implement the ordinary-LED schema 2, not the earlier RGB-house-zone proposal. |
| Both endpoints return valid JSON but the app fails | Report the endpoint status, app version and connection message; check the selected network/address. The app now prefers Android's active Wi-Fi when multiple Wi-Fi networks are exposed. |

The shipped repository `.ino` still has no lighting HTTP routes or physical lighting driver. `HouseLighting.h` is an unintegrated, host-tested component. The app does not manufacture state, enable unknown outputs or claim that an unreachable device lacks an API.
