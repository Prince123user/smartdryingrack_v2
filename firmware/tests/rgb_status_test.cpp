#include <cassert>
#include <iostream>
#include "../SmartDryingRack/SmartDryingRack.ino"
void expectColor(int red, int green, int blue, const char* meaning) {
  updateStatusIndicator();
  assert(digitalLevels[26] == red && digitalLevels[32] == green && digitalLevels[33] == blue);
  handleGetLights(); JsonDocument doc; assert(!deserializeJson(doc, server.response));
  assert(doc["statusIndicator"]["hardwareEnabled"].as<bool>());
  assert(doc["indicator"]["mode"] == "AUTOMATIC");
  assert(doc["indicator"]["meaning"].as<std::string>() == meaning);
  assert(doc["indicator"]["rgb"]["r"].as<int>() == red * 255);
  assert(doc["indicator"]["rgb"]["g"].as<int>() == green * 255);
  assert(doc["indicator"]["rgb"]["b"].as<int>() == blue * 255);
}
int main() {
  setup(); assert(STATUS_RGB_ENABLED && !STATUS_RGB_COMMON_ANODE);
  fakeNow = 5000; dhtValid = true; lastDhtSuccessAt = fakeNow;
  moving = false; bootRetraction = false; stopLatched = false;
  rainWet = rainConfirmed = false; rackState = EXTENDED;
  expectColor(LOW, HIGH, LOW, "DRY_EXTENDED");
  rainConfirmed = true; expectColor(HIGH, LOW, LOW, "RAIN_DETECTED");
  dhtValid = false; expectColor(HIGH, HIGH, LOW, "SENSOR_ERROR");
  dhtValid = true; rainConfirmed = false; stopLatched = true;
  expectColor(HIGH, HIGH, LOW, "STOPPED");
  stopLatched = false; rackState = RETRACTED;
  expectColor(HIGH, HIGH, HIGH, "RETRACTED_IDLE");
  rackState = EXTENDING; moving = true;
  expectColor(LOW, HIGH, HIGH, "RACK_MOVING");
  const auto levels = digitalLevels;
  for (int i = 0; i < 6; ++i) {
    assert(applyHouseLight(i, true, 10 + i));
    assert(applyHouseLight(i, false, 10 + i));
  }
  assert(digitalLevels == levels);
  handleReadOnlyIndicator(); assert(server.code == 403 && digitalLevels == levels);
  std::cout << "PASS common-cathode RGB outputs, API state, priority and house-light isolation (simulated GPIO)\n";
}
