// Run the merged uploaded sketch with simulated GPIO/Wi-Fi and the REAL ArduinoJson library.
#include <cassert>
#include <iostream>
#include <fstream>
#include "../SmartDryingRack/SmartDryingRack.ino"
JsonDocument responseJson() { JsonDocument doc; assert(!deserializeJson(doc, server.response)); return doc; }
void requestLight(int index, const char* id, bool power, int brightness, uint64_t revision) {
  JsonDocument doc;
  doc["requestId"]=id;doc["deviceSession"]=lightingSession;doc["power"]=power;
  doc["brightness"]=brightness;doc["expectedRevision"]=revision;
  serializeJson(doc,server.body);handleAppLight(index);
}
int main(int argc, char** argv) {
  setup();
  assert(server.routes.count("/api/lights0") && server.routes.count("/api/lights1"));
  assert(server.routes.count("/api/lights/zones/front1") && server.routes.count("/api/buzzer1"));
  assert(stopLatched==false && operatingMode==MODE_AUTO);
  for(int i=0;i<6;i++){assert(!houseLights[i].power && houseLights[i].brightness==100);assert(pwmDuty[HOUSE_LIGHT_PINS[i]]==houseLightOutputDuty(i,false,100));}
  handleGetLights();auto initial=responseJson();
  if(argc>1){std::ofstream output(argv[1]);output<<server.response.value;assert(output.good());}
  assert(initial["schemaVersion"]==2 && initial["zones"].size()==6 && initial["lights"].size()==6);
  assert(!initial["indicator"].isNull() && initial["statusIndicator"]["hardwareEnabled"].as<bool>());
  assert(initial["indicator"]["mode"] == "AUTOMATIC" && STATUS_RGB_ENABLED && !STATUS_RGB_COMMON_ANODE);
  assert(initial["deviceSession"].as<std::string>().size()==32);
  for(int i=0;i<6;i++){
    requestLight(i,("zone-"+std::to_string(i)).c_str(),true,10+i,0);
    assert(server.code==200 && houseLights[i].brightness==10+i);
    assert(pwmDuty[HOUSE_LIGHT_PINS[i]]==houseLightOutputDuty(i,true,10+i));
  }
  const auto leftRev=houseLights[1].revision;
  requestLight(0,"front-off",false,10,houseLights[0].revision);
  assert(server.code==200 && !houseLights[0].power && houseLights[0].brightness==10);
  String duplicate=server.body;auto rev=houseLights[0].revision;
  handleAppLight(0);assert(server.code==200 && houseLights[0].revision==rev);
  server.body=duplicate;handleAppLight(1);assert(server.code==409 && houseLights[1].revision==leftRev);
  requestLight(0,"front-on",true,10,rev);assert(server.code==200 && houseLights[0].brightness==10);
  requestLight(0,"zero",true,0,houseLights[0].revision);assert(server.code==200 && pwmDuty[23]==houseLightOutputDuty(0,true,0));
  requestLight(0,"bad",true,101,houseLights[0].revision);assert(server.code==400);
  requestLight(0,"stale",true,50,0);assert(server.code==409);
  requestLight(0,"invalid-types",true,20,houseLights[0].revision);
  auto validRequest=server.body;
  JsonDocument invalid;deserializeJson(invalid,validRequest);
  invalid["power"]="true";serializeJson(invalid,server.body);handleAppLight(0);assert(server.code==400);
  invalid["power"]=true;invalid["brightness"]=10.5;serializeJson(invalid,server.body);handleAppLight(0);assert(server.code==400);
  server.body=String("{\"brightness\":10,")+validRequest.substring(1,validRequest.length());
  handleAppLight(0);assert(server.code==400); // Duplicate top-level key must not silently win.
  assert(uniqueFlatFields("{\"a\":\"quoted \\\" colon: \\\"\",\"b\":1}",2));

  server.body="{\"id\":\"front\",\"brightness\":35}";handleSetLight();
  assert(server.code==200 && houseLights[0].brightness==35 && houseLights[0].lastRequestId.isEmpty());
  handleReadOnlyIndicator();assert(server.code==403);
  failedPwmPin=23;requestLight(0,"driver-error",true,55,houseLights[0].revision);
  assert(server.code==503 && !houseLights[0].available && houseLights[0].brightness==35);
  failedPwmPin=-1;
  server.body="{\"enabled\":false,\"requestId\":\"buzzer-off\"}";handleAppBuzzer();
  assert(server.code==200 && !BUZZER_ENABLED);
  server.body="{\"enabled\":true,\"requestId\":\"buzzer-on\"}";handleAppBuzzer();assert(server.code==200 && BUZZER_ENABLED);
  auto beeps=tones;handleAppBuzzer();assert(tones==beeps);
  // Preserve stable-dry AUTO extension and a latched STOP, independent of lighting HTTP.
  moving=false;bootRetraction=false;rackState=RETRACTED;
  fakeNow=5000;dhtValid=true;lastDhtSuccessAt=fakeNow;temperatureC=29.5;humidityPercent=64;
  rainWet=rainConfirmed=false;clearReadings=RAIN_CLEAR_READINGS;
  server.body="{\"mode\":\"MANUAL\"}";server.routes.at("/api/config1")();
  assert(server.code==200 && operatingMode==MODE_MANUAL);
  updateAutomation();assert(!moving && !dryTimerRunning);
  server.body="{\"mode\":\"AUTO\"}";server.routes.at("/api/config1")();
  assert(server.code==200 && operatingMode==MODE_AUTO);
  updateAutomation();assert(dryTimerRunning && !moving);
  fakeNow+=AUTO_EXTEND_DRY_DELAY_MS;lastDhtSuccessAt=fakeNow;
  updateAutomation();assert(moving && rackState==EXTENDING);
  stopMotion();assert(stopLatched && rackState==STOPPED);
  rainConfirmed=true;updateAutomation();assert(!moving && stopLatched);
  stopLatched=false;rackState=EXTENDED;updateAutomation();assert(moving && rackState==RETRACTING);
  handleStatus();assert(server.code==200);auto status=responseJson();
  assert(status["buzzerEnabled"].as<bool>() && status["sensors"]["rainRaw"].is<int>());
  // New upload's status-label fixes retain schema-2 revision tracking.
  moving=false;stopLatched=false;rainConfirmed=false;rainWet=true;rackState=EXTENDED;
  dhtValid=true;lastDhtSuccessAt=fakeNow;
  updateStatusIndicator();assert(std::string(statusIndicatorReason)=="RAIN_PENDING");
  rainWet=false;updateStatusIndicator();assert(std::string(statusIndicatorReason)=="DRY_EXTENDED");
  rackState=RETRACTED;updateStatusIndicator();assert(std::string(statusIndicatorReason)=="RETRACTED_IDLE");
  rackState=STOPPED;updateStatusIndicator();assert(std::string(statusIndicatorReason)=="STOPPED");
  auto stoppedVersion=lightingVersion;
  rackState=UNKNOWN;updateStatusIndicator();assert(std::string(statusIndicatorReason)=="UNKNOWN_POSITION");
  assert(lightingVersion>stoppedVersion); // Same yellow color, different meaning still advances version.
  rackState=EXTENDING;updateStatusIndicator();assert(std::string(statusIndicatorReason)=="RACK_MOVING");
  dhtValid=false;handleStatus();assert(server.code==503);
  auto sensorFailure=responseJson();
  assert(sensorFailure["deviceResponding"].as<bool>() && sensorFailure["sensors"]["temperature"].isNull());
  assert(sensorFailure["sensors"]["humidity"].isNull() && sensorFailure["error"].as<std::string>()=="SENSOR_FAILURE");
  std::cout<<"Merged firmware checks passed: legacy/schema-2 APIs, independent PWM, retained brightness, zero, deduplication, stale rejection, driver failures, buzzer, stable-dry AUTO and latched STOP.\n";
}
