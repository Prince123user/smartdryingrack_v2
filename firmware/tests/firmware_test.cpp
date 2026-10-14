// Execute the real sketch with host substitutes for GPIO, HTTP and NVS.
// This checks firmware logic; it does not replace compiling for ESP32 or hardware testing.
#include <cassert>
#include <iostream>
#include "../SmartDryingRack/SmartDryingRack.ino"
int main() {
  setup();
  assert(server.routes.count("/api/buzzer1"));
  assert(std::string(motionReason)=="BOOT_HOMING");
  moving=false;bootRetraction=false;rackState=EXTENDED;
  dhtValid=true;temperatureC=29.5;humidityPercent=64;
  int writes=rackServo.writes;
  // A dry sensor and repeated app status requests do not cause retraction.
  for(int i=0;i<100;i++){fakeNow+=RAIN_PERIOD_MS;updateRain();handleStatus();}
  assert(!rainConfirmed && rackState==EXTENDED && !moving);
  assert(rackServo.writes==writes);
  assert(server.response.value.find("\"rainRaw\":3300")!=std::string::npos);
  assert(server.response.value.find("\"buzzerEnabled\":true")!=std::string::npos);
  // Local rain protection still operates independently of the phone.
  fakeADC=1000;
  for(int i=0;i<2;i++){fakeNow+=RAIN_PERIOD_MS;updateRain();assert(!moving);}
  fakeNow+=RAIN_PERIOD_MS;updateRain();
  assert(rainConfirmed && moving && rackState==RETRACTING);
  assert(std::string(motionReason)=="RAIN_PROTECTION");
  // Disable, acknowledge and persist; enable produces a test beep.
  server.body="{\"enabled\":false,\"requestId\":\"off-1\"}";handleBuzzer();
  assert(server.code==200 && !buzzerEnabled && !deviceSettings.getBool("buzzer",true));
  assert(server.response.value=="{\"accepted\":true,\"buzzerEnabled\":false}");
  int silent=tones;beep(1800,150);assert(tones==silent);
  server.body="{\"enabled\":true,\"requestId\":\"on-1\"}";handleBuzzer();
  assert(buzzerEnabled && deviceSettings.getBool("buzzer",false) && tones==silent+1);
  handleBuzzer();assert(tones==silent+1); // Duplicate request must not beep twice.
  for(const char* invalid:{"\"false\"","1","null"}){
    server.body=String("{\"enabled\":")+String(invalid)+",\"requestId\":\"invalid\"}";handleBuzzer();
    assert(server.code==400 && buzzerEnabled);
  }
  server.body="{\"enabled\":false,\"requestId\":null}";handleBuzzer();
  assert(server.code==400 && buzzerEnabled);
  std::cout<<"Firmware logic checks passed: dry polling, confirmed wet retraction, buzzer acknowledgement/persistence, duplicate suppression, invalid requests.\n";
}
