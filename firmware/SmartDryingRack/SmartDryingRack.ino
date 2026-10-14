/*
 * Smart Drying Rack - ESP32 WROOM-32 firmware
 *
 * Exact Android contract implemented here:
 *   GET  /api/status
 *   POST /api/commands
 *
 * The Android app sends EXTEND, RETRACT and STOP, and POST /api/buzzer.
 * Mode, rain-protection and sensitivity remain local firmware settings.
 *
 * Target: ESP32 Arduino core 3.x, board "ESP32 Dev Module".
 * Required libraries:
 *   - DHT sensor library by Adafruit 1.4.6+
 *   - Adafruit Unified Sensor 1.1.15+
 *   - ESP32Servo 3.0.6+
 * WiFi.h and WebServer.h are supplied by the ESP32 board package.
 */

#include <WiFi.h>
#include <WebServer.h>
#include <ESP32Servo.h>
#include <DHT.h>
#include <Preferences.h>

// ---------------------------- Wi-Fi ---------------------------------------
const char* WIFI_SSID = "REPLACE_WITH_WIFI_NAME";
const char* WIFI_PASSWORD = "REPLACE_WITH_WIFI_PASSWORD";

// ---------------------------- Pin wiring -----------------------------------
constexpr uint8_t RAIN_AO_PIN = 34;       // FC-37 AO, ADC input only
constexpr uint8_t RAIN_DO_PIN = 27;       // Optional FC-37 DO
constexpr bool USE_RAIN_DIGITAL_OUTPUT = false;
constexpr uint8_t DHT_PIN = 4;
constexpr uint8_t SERVO_PIN = 18;
constexpr uint8_t BUZZER_PIN = 19;

// ---------------------------- Calibration ----------------------------------
// Start conservatively. Determine these values with the rack unloaded and
// the servo horn disconnected, then test with the horn attached.
constexpr int RETRACT_ANGLE = 25;
constexpr int EXTEND_ANGLE = 125;
constexpr int SERVO_SPEED_DEG_PER_SECOND = 35;

// FC-37 AO is commonly lower when wet, but modules vary. Set true if a wet
// cloth makes the ADC value go down. The digital output polarity is separate.
constexpr bool RAIN_WET_WHEN_ANALOG_BELOW = true;
constexpr bool RAIN_DIGITAL_ACTIVE_LOW = true;
constexpr int RAIN_THRESHOLD = 1900;
constexpr int RAIN_HYSTERESIS = 180;
constexpr uint8_t RAIN_CONFIRM_READINGS = 3;
constexpr uint8_t RAIN_CLEAR_READINGS = 5;

// Local safety policy. The Android app has no API for changing these yet.
constexpr bool RAIN_PROTECTION_ENABLED = true;
constexpr bool BUZZER_ENABLED = true;
constexpr bool BUZZER_ON_MOVEMENT = false;
enum OperatingMode { MODE_AUTO, MODE_MANUAL };
// The current Android API does not send a mode field. Change this value for a
// firmware-only default; rain protection remains a device-side safety priority
// in either mode.
constexpr OperatingMode STARTUP_MODE = MODE_AUTO;
constexpr unsigned long SENSOR_PERIOD_MS = 2500;
constexpr unsigned long RAIN_PERIOD_MS = 250;
constexpr unsigned long SERVO_STEP_PERIOD_MS = 20;
constexpr unsigned long WIFI_RETRY_PERIOD_MS = 10000;
constexpr unsigned long BOOT_RETRACT_TIME_MS = 2500;
constexpr unsigned long MAX_MOTION_TIME_MS = 15000;

constexpr char FIRMWARE_VERSION[] = "1.1.0";

DHT dht(DHT_PIN, DHT22);
Servo rackServo;
WebServer server(80);
Preferences deviceSettings;
bool buzzerEnabled = BUZZER_ENABLED;
const char* motionReason = "NONE";

enum RackState { EXTENDED, RETRACTED, EXTENDING, RETRACTING, STOPPED, ERROR_STATE, UNKNOWN };
RackState rackState = UNKNOWN;
OperatingMode operatingMode = STARTUP_MODE;
int currentAngle = RETRACT_ANGLE;
int targetAngle = RETRACT_ANGLE;
bool moving = false;
bool bootRetraction = false;
unsigned long motionStartedAt = 0;
unsigned long motionDuration = 0;

float temperatureC = NAN;
float humidityPercent = NAN;
bool dhtValid = false;
unsigned long lastSensorAt = 0;

int lastRainRaw = 0;
bool rainWet = false;
bool rainConfirmed = false;
uint8_t wetReadings = 0;
uint8_t clearReadings = 0;
unsigned long lastRainAt = 0;
bool previousRainConfirmed = false;

unsigned long lastServoStepAt = 0;
unsigned long lastWifiAttemptAt = 0;

struct CommandRecord {
  String requestId;
  String response;
  unsigned long storedAt;
};
constexpr uint8_t COMMAND_CACHE_SIZE = 16;
CommandRecord commandCache[COMMAND_CACHE_SIZE];
uint8_t commandCacheNext = 0;

String stateName(RackState state) {
  switch (state) {
    case EXTENDED: return "EXTENDED";
    case RETRACTED: return "RETRACTED";
    case EXTENDING: return "EXTENDING";
    case RETRACTING: return "RETRACTING";
    case STOPPED: return "STOPPED";
    case ERROR_STATE: return "ERROR";
    default: return "UNKNOWN";
  }
}

const char* modeName() { return operatingMode == MODE_AUTO ? "AUTO" : "MANUAL"; }

void sendJson(int statusCode, const String& body) {
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.sendHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
  server.sendHeader("Access-Control-Allow-Headers", "Content-Type");
  server.send(statusCode, "application/json", body);
}

void sendError(int statusCode, const char* code, const char* message) {
  String body = "{\"error\":\"";
  body += code;
  body += "\",\"message\":\"";
  body += message;
  body += "\"}";
  sendJson(statusCode, body);
}

// Small JSON helpers are enough for the app's fixed request schema and avoid
// adding an unrequested JSON dependency to the Arduino project.
String jsonString(const String& body, const char* key) {
  String marker = "\"" + String(key) + "\"";
  int keyAt = body.indexOf(marker);
  if (keyAt < 0) return "";
  int colon = body.indexOf(':', keyAt + marker.length());
  if (colon < 0) return "";
  int firstQuote = colon + 1;
  while (firstQuote < (int)body.length() && (body[firstQuote] == ' ' || body[firstQuote] == '\t' || body[firstQuote] == '\r' || body[firstQuote] == '\n')) firstQuote++;
  if (firstQuote >= (int)body.length() || body[firstQuote] != '"') return "";
  int secondQuote = body.indexOf('"', firstQuote + 1);
  if (secondQuote < 0) return "";
  // Request IDs/actions use unescaped ASCII tokens, not nested JSON or quoted values.
  for (int i = firstQuote + 1; i < secondQuote; ++i) {
    if (body[i] == '\\' || (unsigned char)body[i] < 32) return "";
  }
  return body.substring(firstQuote + 1, secondQuote);
}

String requestIdFor(const String& body) { return jsonString(body, "requestId"); }
String actionFor(const String& body) { return jsonString(body, "action"); }

String statusJson(bool accepted, const char* reason = nullptr) {
  String body = "{\"accepted\":";
  body += accepted ? "true" : "false";
  if (reason != nullptr) {
    body += ",\"reason\":\"";
    body += reason;
    body += "\"";
  }
  body += "}";
  return body;
}

String cachedResponse(const String& requestId) {
  if (requestId.isEmpty()) return "";
  for (uint8_t i = 0; i < COMMAND_CACHE_SIZE; ++i) {
    if (commandCache[i].requestId == requestId && millis() - commandCache[i].storedAt < 10UL * 60UL * 1000UL) {
      return commandCache[i].response;
    }
  }
  return "";
}

void cacheResponse(const String& requestId, const String& response) {
  commandCache[commandCacheNext] = { requestId, response, millis() };
  commandCacheNext = (commandCacheNext + 1) % COMMAND_CACHE_SIZE;
}

void beep(uint16_t frequency, uint16_t duration) {
  if (buzzerEnabled) tone(BUZZER_PIN, frequency, duration);
}

void stopBuzzer() { noTone(BUZZER_PIN); }

void setServoError() {
  moving = false;
  bootRetraction = false;
  rackState = ERROR_STATE;
  stopBuzzer();
}

void beginMotion(bool retract, const char* reason) {
  motionReason = reason;
  targetAngle = retract ? RETRACT_ANGLE : EXTEND_ANGLE;
  targetAngle = constrain(targetAngle, 0, 180);
  int distance = abs(targetAngle - currentAngle);
  motionDuration = max(1000UL, (unsigned long)distance * 1000UL / max(1, SERVO_SPEED_DEG_PER_SECOND));
  motionDuration = min(motionDuration, MAX_MOTION_TIME_MS);
  motionStartedAt = millis();
  moving = true;
  bootRetraction = false;
  rackState = retract ? RETRACTING : EXTENDING;
  if (BUZZER_ON_MOVEMENT) beep(retract ? 1200 : 1800, 100);
}

void beginBootRetraction() {
  motionReason = "BOOT_HOMING";
  // There is no position sensor. Move to the conservative angle immediately,
  // then expose RETRACTING until the configured travel window has elapsed.
  currentAngle = RETRACT_ANGLE;
  targetAngle = RETRACT_ANGLE;
  rackServo.write(RETRACT_ANGLE);
  moving = true;
  bootRetraction = true;
  motionStartedAt = millis();
  motionDuration = BOOT_RETRACT_TIME_MS;
  rackState = RETRACTING;
}

void stopMotion() {
  moving = false;
  bootRetraction = false;
  rackState = STOPPED;
  stopBuzzer();
}

void updateServo() {
  if (!moving) return;
  unsigned long now = millis();
  if (now - motionStartedAt > motionDuration || now - motionStartedAt > MAX_MOTION_TIME_MS) {
    if (bootRetraction) {
      currentAngle = RETRACT_ANGLE;
      rackState = RETRACTED;
    } else {
      currentAngle = targetAngle;
      rackState = targetAngle == RETRACT_ANGLE ? RETRACTED : EXTENDED;
    }
    rackServo.write(currentAngle);
    moving = false;
    bootRetraction = false;
    return;
  }
  if (bootRetraction || now - lastServoStepAt < SERVO_STEP_PERIOD_MS) return;
  lastServoStepAt = now;
  if (currentAngle < targetAngle) currentAngle++;
  if (currentAngle > targetAngle) currentAngle--;
  rackServo.write(currentAngle);
}

void updateDht() {
  if (millis() - lastSensorAt < SENSOR_PERIOD_MS) return;
  lastSensorAt = millis();
  float h = dht.readHumidity();
  float t = dht.readTemperature();
  if (!isnan(h) && !isnan(t) && isfinite(h) && isfinite(t) && t >= -50.0f && t <= 80.0f && h >= 0.0f && h <= 100.0f) {
    temperatureC = t;
    humidityPercent = h;
    dhtValid = true;
  } else {
    dhtValid = false;
  }
}

bool readRainWet() {
  if (USE_RAIN_DIGITAL_OUTPUT) {
    bool active = digitalRead(RAIN_DO_PIN) == (RAIN_DIGITAL_ACTIVE_LOW ? LOW : HIGH);
    lastRainRaw = digitalRead(RAIN_DO_PIN);
    return active;
  }
  lastRainRaw = analogRead(RAIN_AO_PIN);
  if (RAIN_WET_WHEN_ANALOG_BELOW) {
    if (!rainWet && lastRainRaw <= RAIN_THRESHOLD) rainWet = true;
    if (rainWet && lastRainRaw >= RAIN_THRESHOLD + RAIN_HYSTERESIS) rainWet = false;
  } else {
    if (!rainWet && lastRainRaw >= RAIN_THRESHOLD) rainWet = true;
    if (rainWet && lastRainRaw <= RAIN_THRESHOLD - RAIN_HYSTERESIS) rainWet = false;
  }
  return rainWet;
}

void updateRain() {
  if (millis() - lastRainAt < RAIN_PERIOD_MS) return;
  lastRainAt = millis();
  bool wet = readRainWet();
  if (wet) {
    clearReadings = 0;
    if (wetReadings < RAIN_CONFIRM_READINGS) wetReadings++;
    if (wetReadings >= RAIN_CONFIRM_READINGS) rainConfirmed = true;
  } else {
    wetReadings = 0;
    if (clearReadings < RAIN_CLEAR_READINGS) clearReadings++;
    if (clearReadings >= RAIN_CLEAR_READINGS) rainConfirmed = false;
  }
  if (RAIN_PROTECTION_ENABLED && rainConfirmed && !previousRainConfirmed) {
    beep(2200, 180);
    if (rackState == EXTENDED || rackState == EXTENDING || rackState == STOPPED) beginMotion(true, "RAIN_PROTECTION");
  }
  previousRainConfirmed = rainConfirmed;
}

void updateWifi() {
  if (WiFi.status() == WL_CONNECTED) return;
  if (millis() - lastWifiAttemptAt < WIFI_RETRY_PERIOD_MS) return;
  lastWifiAttemptAt = millis();
  WiFi.disconnect(false, false);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
}

void handleOptions() { sendJson(204, "{}"); }

void handleStatus() {
  // A failed DHT sample is deliberately unavailable rather than silently
  // presenting cached readings as current. Rain protection continues locally.
  if (!dhtValid) {
    sendError(503, "SENSOR_FAILURE", "DHT22 reading unavailable");
    return;
  }
  String body = "{\"state\":\"" + stateName(rackState) + "\",\"firmwareVersion\":\"" + FIRMWARE_VERSION + "\",\"sensors\":{";
  body += "\"temperature\":" + String(temperatureC, 1);
  body += ",\"humidity\":" + String(humidityPercent, 1);
  body += ",\"rainDetected\":" + String(rainConfirmed ? "true" : "false");
  body += ",\"clothesDryPercentage\":null";
  body += ",\"rainRaw\":" + String(lastRainRaw);
  body += "},\"positionSource\":\"COMMAND_ESTIMATE\",\"positionMeasured\":false";
  body += ",\"mode\":\"" + String(modeName()) + "\"";
  body += ",\"rainProtectionEnabled\":" + String(RAIN_PROTECTION_ENABLED ? "true" : "false");
  body += ",\"buzzerEnabled\":" + String(buzzerEnabled ? "true" : "false");
  body += ",\"motionReason\":\"" + String(motionReason) + "\"";
  body += ",\"rainThreshold\":" + String(RAIN_THRESHOLD);
  body += ",\"rainWetWhenAnalogBelow\":" + String(RAIN_WET_WHEN_ANALOG_BELOW ? "true" : "false");
  body += "}";
  sendJson(200, body);
}

void handleCommand() {
  if (!server.hasArg("plain")) {
    sendError(400, "INVALID_REQUEST", "JSON request body is required");
    return;
  }
  String body = server.arg("plain");
  String requestId = requestIdFor(body);
  String action = actionFor(body);
  if (requestId.isEmpty() || action.isEmpty()) {
    sendError(400, "INVALID_REQUEST", "action and requestId are required");
    return;
  }
  String previous = cachedResponse(requestId);
  if (!previous.isEmpty()) {
    sendJson(200, previous);
    return;
  }

  String response;
  if (action == "STOP") {
    stopMotion();
    response = statusJson(true);
  } else if (action == "RETRACT") {
    if (rackState == RETRACTED && !moving) response = statusJson(false, "ALREADY_RETRACTED");
    else {
      beginMotion(true, "REST_COMMAND");
      response = statusJson(true);
    }
  } else if (action == "EXTEND") {
    if (RAIN_PROTECTION_ENABLED && rainConfirmed) response = statusJson(false, "RAIN_PROTECTION");
    else if (!dhtValid) response = statusJson(false, "SENSOR_UNAVAILABLE");
    else if ((rackState == EXTENDED) && !moving) response = statusJson(false, "ALREADY_EXTENDED");
    else {
      beginMotion(false, "REST_COMMAND");
      response = statusJson(true);
    }
  } else {
    response = statusJson(false, "UNKNOWN_ACTION");
  }
  cacheResponse(requestId, response);
  sendJson(response.startsWith("{\"accepted\":true") ? 200 : 409, response);
}

// Accept a JSON boolean, not a quoted string or a numeric truthy value.
bool jsonBoolean(const String& body, const char* key, bool& value) {
  String marker = "\"" + String(key) + "\"";
  int at = body.indexOf(marker);
  if (at < 0) return false;
  int colon = body.indexOf(':', at + marker.length());
  if (colon < 0) return false;
  int end = body.indexOf(',', colon + 1);
  int brace = body.indexOf('}', colon + 1);
  if (brace < 0) return false;
  if (end < 0 || brace < end) end = brace;
  String token = body.substring(colon + 1, end);
  token.trim();
  if (token == "true") { value = true; return true; }
  if (token == "false") { value = false; return true; }
  return false;
}

void handleBuzzer() {
  if (!server.hasArg("plain")) { sendError(400, "INVALID_REQUEST", "JSON request body is required"); return; }
  String body = server.arg("plain");
  String id = requestIdFor(body);
  bool enabled;
  if (id.isEmpty() || !jsonBoolean(body, "enabled", enabled)) {
    sendError(400, "INVALID_REQUEST", "enabled must be a boolean and requestId is required");
    return;
  }
  String cacheKey = "buzzer:" + id;
  String previous = cachedResponse(cacheKey);
  if (!previous.isEmpty()) { sendJson(200, previous); return; }
  buzzerEnabled = enabled;
  deviceSettings.putBool("buzzer", enabled);
  if (!enabled) stopBuzzer();
  else beep(1800, 150);
  String response = "{\"accepted\":true,\"buzzerEnabled\":" + String(enabled ? "true" : "false") + "}";
  cacheResponse(cacheKey, response);
  sendJson(200, response);
}

void handleNotFound() { sendError(404, "NOT_FOUND", "Use /api/status, /api/commands or /api/buzzer"); }

void setup() {
  Serial.begin(115200);
  pinMode(BUZZER_PIN, OUTPUT);
  deviceSettings.begin("rack", false);
  buzzerEnabled = deviceSettings.getBool("buzzer", BUZZER_ENABLED);
  pinMode(RAIN_AO_PIN, INPUT);
  if (USE_RAIN_DIGITAL_OUTPUT) pinMode(RAIN_DO_PIN, INPUT);
  analogReadResolution(12);
  analogSetPinAttenuation(RAIN_AO_PIN, ADC_11db);

  dht.begin();
  rackServo.setPeriodHertz(50);
  rackServo.attach(SERVO_PIN, 500, 2400);
  beginBootRetraction();

  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  WiFi.persistent(false);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  lastWifiAttemptAt = millis();

  server.on("/api/status", HTTP_GET, handleStatus);
  server.on("/api/status", HTTP_OPTIONS, handleOptions);
  server.on("/api/commands", HTTP_POST, handleCommand);
  server.on("/api/commands", HTTP_OPTIONS, handleOptions);
  server.on("/api/buzzer", HTTP_POST, handleBuzzer);
  server.on("/api/buzzer", HTTP_OPTIONS, handleOptions);
  server.onNotFound(handleNotFound);
  server.begin();
  Serial.println("Smart Drying Rack API started");
}

void loop() {
  server.handleClient();
  updateWifi();
  updateDht();
  updateRain();
  if (RAIN_PROTECTION_ENABLED && rainConfirmed && (rackState == EXTENDED || rackState == EXTENDING || rackState == STOPPED)) beginMotion(true, "RAIN_PROTECTION");
  updateServo();
  delay(1);
}
