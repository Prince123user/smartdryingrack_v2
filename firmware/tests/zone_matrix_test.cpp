// Exercise each registered house-light HTTP route against the real sketch and ArduinoJson.
#include <array>
#include <cassert>
#include <iostream>
#include "../SmartDryingRack/SmartDryingRack.ino"
int main() {
  setup();
  String earliestRequest;
  for (unsigned zone = 0; zone < 6; ++zone) {
    unsigned step = 0;
    auto command = [&](bool power, unsigned brightness) {
      std::array<HouseLightState, 6> before;
      std::array<int, 6> duties;
      for (unsigned j = 0; j < 6; ++j) { before[j] = houseLights[j]; duties[j] = pwmDuty[HOUSE_LIGHT_PINS[j]]; }
      const auto started = fakeNow;
      JsonDocument body;
      body["requestId"] = std::string("matrix-") + std::to_string(zone) + "-" + std::to_string(step++);
      body["deviceSession"] = lightingSession;
      body["expectedRevision"] = houseLights[zone].revision;
      body["power"] = power; body["brightness"] = brightness;
      serializeJson(body, server.body);
      if (zone == 0 && step == 1) earliestRequest = server.body;
      server.routes.at(std::string("/api/lights/zones/") + HOUSE_LIGHT_IDS[zone] + "1")();
      assert(server.code == 200);
      assert(houseLights[zone].power == power && houseLights[zone].brightness == brightness);
      assert(pwmDuty[HOUSE_LIGHT_PINS[zone]] == (power ? brightnessToDuty(brightness) : 0));
      assert(fakeNow == started); // Handler contains no delay/timed waiting.
      for (unsigned j = 0; j < 6; ++j) if (j != zone) {
        assert(houseLights[j].power == before[j].power && houseLights[j].brightness == before[j].brightness);
        assert(houseLights[j].revision == before[j].revision && houseLights[j].lastRequestId == before[j].lastRequestId);
        assert(houseLights[j].available == before[j].available && pwmDuty[HOUSE_LIGHT_PINS[j]] == duties[j]);
      }
      assert(!STATUS_RGB_ENABLED && statusRed == 0 && statusGreen == 0 && statusBlue == 255);
    };
    command(true, 73);
    command(false, 73); // OFF retains selected brightness.
    command(false, 25); // Editing brightness while OFF keeps the output OFF.
    command(true, 25);
    for (unsigned brightness : {0, 1, 50, 99, 100}) command(true, brightness);
    std::cout << "PASS " << HOUSE_LIGHT_IDS[zone] << ": power, retained/off brightness, 0/1/50/99/100%, isolation, registered route\n";
  }
  const auto version = lightingVersion;
  server.body = earliestRequest; handleAppLight(0);
  assert(server.code == 409 && lightingVersion == version); // Evicted old command never reapplies.
  handleReadOnlyIndicator(); assert(server.code == 403 && lightingVersion == version);
  std::cout << "PASS replay protection after cache eviction; automatic RGB has no manual route\n";
}
