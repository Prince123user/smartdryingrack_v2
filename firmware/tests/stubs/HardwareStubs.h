#pragma once
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <map>
#include <sstream>
#include <iomanip>
#include <string>
#include <vector>
#include <functional>
#include <cstring>
using std::min;
using std::max;
using std::isfinite;
using std::isnan;
class String {
public:
  std::string value;
  String() = default;
  String(const char* s):value(s){}
  String(const std::string& s):value(s){}
  String(int n):value(std::to_string(n)){}
  String(float f, int precision) { std::ostringstream s; s << std::fixed << std::setprecision(precision) << f; value=s.str(); }
  const char* c_str() const { return value.c_str(); }
  char operator[](int n) const { return value[n]; }
  int length() const { return value.size(); }
  bool isEmpty() const { return value.empty(); }
  int indexOf(const String& s,int from=0) const { auto p=value.find(s.value,from); return p==std::string::npos?-1:int(p); }
  int indexOf(char c,int from=0) const { auto p=value.find(c,from); return p==std::string::npos?-1:int(p); }
  String substring(int first,int last) const { return value.substr(first,last-first); }
  bool startsWith(const char* s) const { return value.rfind(s,0)==0; }
  void trim() { auto first=value.find_first_not_of(" \r\n\t"); if(first==std::string::npos){value.clear();return;} value=value.substr(first,value.find_last_not_of(" \r\n\t")-first+1); }
  String& operator+=(const String& s){value+=s.value;return *this;}
  bool operator==(const String& s)const{return value==s.value;}
  bool operator!=(const String& s)const{return value!=s.value;}
  friend String operator+(const String&a,const String&b){return a.value+b.value;}
};
constexpr int INPUT=0, OUTPUT=1, LOW=0, HIGH=1, ADC_11db=3, DHT22=22, WIFI_STA=1, WL_CONNECTED=3;
constexpr int HTTP_GET=0, HTTP_POST=1, HTTP_OPTIONS=2;
inline unsigned long fakeNow=0;
inline int fakeADC=3300, fakeDigital=HIGH, tones=0, stops=0;
inline unsigned long millis(){return fakeNow;}
inline void delay(unsigned long n){fakeNow+=n;}
inline void pinMode(int,int){}
inline std::map<int,int> digitalLevels;
inline void digitalWrite(int pin,int level){digitalLevels[pin]=level;}
inline void analogReadResolution(int){}
inline void analogSetPinAttenuation(int,int){}
inline std::vector<int> adcSamples;
inline int analogRead(int){if(adcSamples.empty())return fakeADC;int v=adcSamples.front();adcSamples.erase(adcSamples.begin());return v;}
inline int digitalRead(int){return fakeDigital;}
inline int constrain(int n,int low,int high){return min(high,max(low,n));}
inline void tone(int,int,int){tones++;}
inline void noTone(int){stops++;}
struct SerialClass {
 void begin(int){} template<class T> void print(const T&){} template<class T> void println(const T&){}
 void print(float,int){} void println(){}
};
inline SerialClass Serial;
struct WiFiClass {int status(){return WL_CONNECTED;} void mode(int){} void setAutoReconnect(bool){} void persistent(bool){} void begin(const char*,const char*){} void setHostname(const char*){} String localIP(){return "192.168.1.92";} void disconnect(bool,bool){}};
inline WiFiClass WiFi;
class WebServer {
public:
  String body,response; int code=0; bool hasBody=true;
  std::map<std::string,std::function<void()>> routes;
  explicit WebServer(int){}
  void sendHeader(const char*,const char*){}
  void send(int c,const char*,const String&r){code=c;response=r;}
  bool hasArg(const char*){return hasBody;}
  String arg(const char*){return body;}
  void on(const char* p,int method,std::function<void()> f){routes[std::string(p)+std::to_string(method)]=f;}
  void onNotFound(void(*)()){}
  void begin(){} void handleClient(){}
};
class DHT {public:DHT(int,int){} void begin(){} float readHumidity(){return 64;} float readTemperature(){return 29.5;}};
class ESP32PWM {public:static void allocateTimer(int){}};
class Servo {public:bool attached(){return true;}int writes=0,angle=0;void setPeriodHertz(int){} void attach(int,int,int){} void write(int a){angle=a;writes++;}};
class Preferences {
public:
  std::map<std::string,bool> values;
  bool begin(const char*,bool){return true;}
  bool getBool(const char* k,bool fallback){return values.count(k)?values[k]:fallback;}
  size_t putBool(const char* k,bool v){values[k]=v;return 1;}
};

inline std::map<int, int> pwmChannels, pwmDuty;
inline int failedPwmPin = -1;
inline bool ledcAttachChannel(int pin,int frequency,int bits,int channel) {
  (void)frequency;(void)bits; pwmChannels[pin]=channel;return pin!=failedPwmPin;
}
inline bool ledcWrite(int pin,int duty){if(pin==failedPwmPin)return false;pwmDuty[pin]=duty;return true;}
inline bool failedToneOutput=false;
inline double ledcWriteTone(int pin,int hz){if(failedToneOutput && hz>0)return 0;pwmDuty[pin]=hz;if(hz)tones++;else stops++;return hz;}
inline uint32_t esp_random(){static uint32_t v=100;return ++v;}

inline uint32_t ledcRead(int pin){return pwmDuty[pin];}
inline uint32_t ledcReadFreq(int pin){return pwmChannels.count(pin)?1000:0;}
