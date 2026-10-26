#include <cassert>
#include <iostream>
#include "../SmartDryingRack/SmartDryingRack.ino"
void resetToneFault() {
  failedToneOutput=false; buzzerAvailable=true; buzzerToneFault=false; stopBuzzer();
}
int main() {
  setup(); operatingMode=MODE_MANUAL; BUZZER_ENABLED=false; stopBuzzer();
  failedToneOutput=true;
  server.body="{\"enabled\":true,\"requestId\":\"failed-enable\"}";
  handleAppBuzzer();
  assert(server.code==503 && !BUZZER_ENABLED && !buzzerAvailable && buzzerToneFault && !buzzerActive);
  assert(pwmDuty[19]==0);
  handleDiagnostics(); JsonDocument diagnostic; assert(!deserializeJson(diagnostic,server.response));
  assert(diagnostic["buzzerToneFault"].as<bool>() && !diagnostic["buzzerAvailable"].as<bool>());
  resetToneFault(); BUZZER_ENABLED=true; failedToneOutput=true;
  handleBuzzerTest(); assert(server.code==503 && buzzerToneFault && !buzzerActive);
  resetToneFault();
  startBuzzerPattern(2400,10,10,2,1); assert(buzzerActive);
  fakeNow+=10; updateBuzzer(); assert(!buzzerOnPhase && buzzerActive);
  failedToneOutput=true; fakeNow+=10; updateBuzzer();
  assert(!buzzerActive && buzzerToneFault && !buzzerAvailable && pwmDuty[19]==0);
  resetToneFault(); operatingMode=MODE_AUTO; BUZZER_ENABLED=false; failedToneOutput=true;
  beginMotion(true);
  assert(moving && rackState==RETRACTING && buzzerToneFault && !buzzerAvailable);
  stopMotion(); assert(stopLatched && !moving);
  std::cout<<"PASS failed tone start/test/later pulse: no success ACK, mute attempt, fault latch and preference rollback\n";
}
