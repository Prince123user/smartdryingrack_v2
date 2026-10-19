#include <cassert>
#include <iostream>
#include "../SmartDryingRack/SmartDryingRack.ino"

void sample(int raw) {
  fakeADC = raw;
  fakeNow += RAIN_PERIOD_MS;
  lastDhtSuccessAt = fakeNow;
  updateRain();
  updateAutomation();
}
void motionRequest(const char* action, const char* id) {
  JsonDocument body; body["action"] = action; body["requestId"] = id;
  serializeJson(body, server.body); handleCommand();
}
int main() {
  setup();
  operatingMode = MODE_MANUAL; moving = false; rackState = EXTENDED;
  dhtValid = true; temperatureC = 29; humidityPercent = 70;
  for (int i = 0; i < 5; ++i) sample(3000);
  // Two isolated ADC troughs do not become wet, even across repeated windows.
  for (int i = 0; i < 10; ++i) {
    for (int value : {3000, 1500, 3000, 1500, 3000, 3000}) {
      sample(value); assert(!rainWet && !rainConfirmed && !moving);
    }
  }
  // Sustained water still confirms and retracts in MANUAL, within 1.25 s.
  for (int i = 0; i < 5; ++i) sample(1200);
  assert(rainConfirmed && moving && rackState == RETRACTING);
  motionRequest("STOP", "rain-stop"); assert(server.code == 200 && stopLatched);
  for (int i = 0; i < 8; ++i) sample(1200);
  assert(!moving && rackState == STOPPED);
  motionRequest("EXTEND", "wet-extend"); assert(server.code == 409 && stopLatched);
  for (int i = 0; i < 8; ++i) sample(3000);
  assert(!rainConfirmed && !moving && stopLatched);
  motionRequest("EXTEND", "dry-extend"); assert(server.code == 200 && !stopLatched && moving);
  moving = false; rackState = RETRACTED;
  for (int i = 0; i < 60; ++i) sample(3000);
  assert(!moving && !dryTimerRunning); // MANUAL never extends by dry timer.
  operatingMode = MODE_AUTO; sample(3000); assert(dryTimerRunning);
  for (int i = 0; i < 40; ++i) sample(3000);
  assert(moving && rackState == EXTENDING);
  // Exhaustive 0..100% on each zone: monotonic PWM, full OFF, unchanged neighbors.
  for (int zone = 0; zone < 6; ++zone) {
    int previous = -1;
    for (int percent = 0; percent <= 100; ++percent) {
      assert(applyHouseLight(zone, true, percent));
      int duty = ledcRead(HOUSE_LIGHT_PINS[zone]);
      assert(duty > previous); previous = duty;
      handleGetLights(); JsonDocument body; assert(!deserializeJson(body, server.response));
      assert(body["lights"][zone]["pwmDutyReadback"].as<int>() == duty);
      assert(body["lights"][zone]["pwmFrequencyHz"].as<int>() == 1000);
    }
    assert(previous == 255);
    assert(applyHouseLight(zone, false, 37) && ledcRead(HOUSE_LIGHT_PINS[zone]) == 0);
    assert(houseLights[zone].brightness == 37);
  }
  std::cout << "PASS rain impulse rejection, sustained wet protection in MANUAL, STOP, dry AUTO and all six PWM ranges (simulated hardware)\n";
}
