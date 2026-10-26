// Export responses emitted by the real sketch for the Android contract tests.
#include <cassert>
#include <fstream>
#include <iostream>
#include "../SmartDryingRack/SmartDryingRack.ino"
int main(int argc,char** argv) {
  setup(); fakeNow=5000; dhtValid=true; lastDhtSuccessAt=fakeNow;
  temperatureC=29; humidityPercent=70; moving=false; bootRetraction=false;
  rackState=RETRACTED; rainWet=rainConfirmed=false; stopLatched=false;
  JsonDocument contracts;
  auto save=[&](const char* key) {
    assert(server.code==200); JsonDocument body;
    assert(!deserializeJson(body,server.response)); contracts[key]=body.as<JsonVariant>();
  };
  server.body="{\"mode\":\"AUTO\",\"buzzerEnabled\":false}";
  handleSetConfig(); save("autoConfig"); handleStatus(); save("autoMutedPreferenceStatus");
  server.body="{\"mode\":\"MANUAL\"}";
  handleSetConfig(); save("manualConfig"); handleStatus(); save("manualMutedStatus");
  server.body="{\"enabled\":true,\"requestId\":\"contract-buzzer-on\"}";
  handleAppBuzzer(); save("buzzerEnableAck"); handleStatus(); save("manualEnabledStatus");
  server.body="{\"action\":\"EXTEND\",\"requestId\":\"contract-extend\"}";
  handleCommand(); save("movementAck");
  handleGetLights(); save("lightsBefore");
  JsonDocument command;
  command["requestId"]="contract-front"; command["deviceSession"]=lightingSession;
  command["expectedRevision"]=houseLights[0].revision;command["power"]=true;command["brightness"]=25;
  serializeJson(command,server.body); handleAppLight(0); save("lightAck");
  handleGetLights(); save("lightsAfter");
  if(argc>1) {std::ofstream output(argv[1]);serializeJsonPretty(contracts,output);assert(output.good());}
  std::cout<<"PASS real firmware status/config/movement/buzzer/lighting contract responses\n";
}
