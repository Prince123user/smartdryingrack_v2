#include <cassert>
#include <iostream>
#include "../SmartDryingRack/SmartDryingRack.ino"
void config(const char* body) { server.body=body; handleSetConfig(); assert(server.code==200); }
int main() {
  setup();
  BUZZER_ON_MOVEMENT=false; // Deprecated field cannot bypass the required movement policy.
  for (auto mode : {MODE_AUTO, MODE_MANUAL}) for (bool preference : {false,true}) {
    operatingMode=mode; BUZZER_ENABLED=preference;
    for (bool retract : {false,true}) {
      stopBuzzer(); auto before=tones; beginMotion(retract);
      const bool audible=mode==MODE_AUTO || preference;
      assert(buzzerActive==audible && tones==before+(audible?1:0));
      if(audible) {
        assert(ledcReadFreq(19)==(retract?BUZZER_RETRACT_HZ:BUZZER_EXTEND_HZ));
        assert(ledcRead(19)==buzzerDutyForPercent(BUZZER_VOLUME_PERCENT));
      }
      updateBuzzer(); assert(buzzerActive==audible);
    }
  }
  config("{\"mode\":\"AUTO\",\"buzzerEnabled\":false}");
  stopBuzzer(); beginMotion(true); assert(buzzerActive);
  server.body="{\"enabled\":false,\"requestId\":\"manual-pref-off-auto\"}";
  handleAppBuzzer(); assert(server.code==200 && !BUZZER_ENABLED && buzzerActive);
  config("{\"mode\":\"MANUAL\"}"); assert(!BUZZER_ENABLED && !buzzerActive);
  JsonDocument doc; assert(!deserializeJson(doc,server.response));
  assert(doc["buzzerAutoRequired"].as<bool>() && !doc["buzzerEffectiveEnabled"].as<bool>());
  config("{\"mode\":\"AUTO\"}"); assert(!BUZZER_ENABLED && buzzerSoundEnabled());
  stopBuzzer(); beginMotion(false); assert(buzzerActive);
  stopMotion(); assert(stopLatched && !buzzerActive);
  std::cout<<"PASS AUTO always requests movement sound, MANUAL preference, mode transitions, legacy flag override and STOP (simulated PWM)\n";
}
