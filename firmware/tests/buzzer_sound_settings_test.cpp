#include <cassert>
#include <iostream>
#include "../SmartDryingRack/SmartDryingRack.ino"
int main() {
  setup();
  assert(buzzerDutyForPercent(1)==5);
  assert(buzzerDutyForPercent(50)==256);
  assert(buzzerDutyForPercent(100)==511);
  for(int level=2;level<=100;++level) assert(buzzerDutyForPercent(level)>buzzerDutyForPercent(level-1));
  operatingMode=MODE_MANUAL;BUZZER_ENABLED=true;stopBuzzer();
  beginMotion(true);assert(ledcReadFreq(19)==BUZZER_RETRACT_HZ);
  assert(ledcRead(19)==buzzerDutyForPercent(BUZZER_VOLUME_PERCENT));
  stopBuzzer();beginMotion(false);assert(ledcReadFreq(19)==BUZZER_EXTEND_HZ);
  failedPwmPin=19;stopBuzzer();
  startBuzzerPattern(BUZZER_TEST_HZ,100,100,1,1);
  assert(buzzerToneFault && !buzzerAvailable && !buzzerActive);
  std::cout<<"PASS configurable pitch, bounded PWM level and duty-write failure reporting (simulated peripheral)\n";
}
