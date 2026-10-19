/* Release 1.1.2: combines the latest uploaded status-label / sensor-error
 * improvements with the checked Android-compatible lighting and buzzer APIs.
 * Retains schema 2, per-zone guarded writes, boot sessions, revisions,
 * buzzerEnabled, nested rainRaw, latched STOP and stable-dry AUTO behavior.
 * Configure Wi-Fi locally. RGB remains disabled until hardware is identified.
 */
/* Integration revision 1.1.1: based on the supplied SmartDryingRack_complete.ino.
 * Preserves uploaded rack automation, STOP latch, wiring, PWM allocation and legacy APIs.
 * Adds schema-2 lighting metadata/per-zone routes and Android buzzer compatibility.
 * Credentials below are placeholders; configure them locally before uploading.
 * Physical wiring, LED current limits and RGB pinout still require confirmation.
 */
/*
 * LIGHTING ADDITION 1.1.0 - Read before wiring
 * This is the complete uploaded working 1.0.2 sketch plus lighting additions.
 * Six ordinary single-color LEDs: front D23, left D22, right D21, back D17,
 * CR D16, balcony D25. Each pin drives an active-high low-side driver input.
 * Never connect an unresisted LED or a 5 V LED load directly to GPIO.
 * Use individually sized current limiting and a suitably rated driver/supply.
 * All driver/supply grounds join ESP32 GND. Servo keeps its external supply.
 * Channel allocation: servo timer 0; houses 2/3,4/5,6/7 at 1000 Hz, 8-bit;
 * buzzer channel 14 remains in the other LEDC speed group on CLASSIC ESP32.
 * Do not enable extra ESP32Servo timers or add other PWM consumers blindly.
 *
 * New API (requires corresponding mobile-app changes):
 * GET /api/lights -> {lights:[{id,power,brightness,available,type,outputDuty}],
 *                    statusIndicator:{automatic,hardwareEnabled,reason,r,g,b}}
 * POST /api/lights Content-Type: application/json
 *   {"id":"front","power":true,"brightness":40}
 *   {"id":"front","power":false}
 *   {"id":"cr","brightness":15}
 * brightness 0..100; power=false keeps the selected brightness for later.
 * All house lights start OFF with remembered brightness 100 after reset.
 * Settings are RAM-only. Brightness is PWM percent, not measured light output.
 * Requests use absolute state, not toggle commands. Apps should serialize
 * updates per zone, debounce sliders, and refresh GET after reconnecting.
 *
 * RGB hardware is unidentified: STATUS_RGB_ENABLED remains false. Automatic
 * status LOGIC/API works; no RGB physical light is claimed until enabled.
 * Optional provided pin implementation is for a plain 4-pin RGB LED only:
 * R D26, G D32, B D33, one resistor per color. Verify actual pin order and
 * common-anode/cathode type. Do NOT use this for WS2812 or 2-wire RGB LEDs.
 * Set STATUS_RGB_ENABLED=true only AFTER confirming appropriate wiring.
 * Common-anode option is only for a 3.3 V common connection (no 5 V at GPIO).
 * Automatic priority: servo error yellow, DHT error yellow, rain red, STOP
 * yellow, Wi-Fi connecting blue, starting blue, motion cyan, dry extended
 * green, retracted idle white. GPIO output supports these basic colors only.
 *
 * Dependencies unchanged: ESP32Servo, DHT, Adafruit Unified Sensor,
 * ArduinoJson 7.x, ESP32 board core 3.x. Board ESP32 Dev Module, Flash DIO.
 * Software can confirm PWM command acceptance, not actual illumination.
 * Original upload had no target build evidence; integration 1.1.1 is target-compiled, not physically tested.
 */

/*
 * Smart Drying Rack - ESP32 WROOM-32 firmware
 *
 * Exact Android contract implemented here:
 *   GET  /api/status
 *   POST /api/commands
 *
 * Revision 1.0.2:
 * - AUTO: retract on confirmed rain; extend after 10 seconds of stable dry
 *   readiness (after retraction, dry debounce and valid DHT22 sample).
 * - MANUAL: no automatic extension. Enabled rain protection still retracts.
 * - STOP latches in either mode. Fresh accepted motion command resumes it.
 * - Passive piezo: 2 startup chirps, 3 rain chirps, movement chirp.
 * - GET /api/diagnostics works even if DHT22 is invalid.
 * - POST /api/buzzer/test plays 3 beeps if enabled and not in a rain alarm.
 * - Existing status, commands and config routes remain compatible.
 *
 * Upload: ESP32 Dev Module, Flash Mode DIO, Arduino-ESP32 3.x.
 * Serial Monitor: 115200 baud. Listen for two startup chirps.
 * Check GET /api/config shows mode AUTO, buzzerEnabled true.
 * Sensor defaults: wet AO <=1900, dry AO >=2080 (lower-is-wet mode).
 * Calibrate using Serial rainRaw values; wet boards may take time to dry.
 * Test with the servo disconnected from the mechanism first.
 * Buzzer test from Windows PowerShell:
 *   Invoke-RestMethod -Method Post http://DEVICE_IP/api/buzzer/test
 * If buzzerAvailable is true but no sound occurs, check piezo type, GPIO19 S,
 * ground and appropriate drive circuitry. Code cannot confirm physical sound.
 * DHT sample period is 2.5 s. Offsets default to zero; do not invent offsets.
 * Compare against a reference in the same stable air conditions to validate
 * accuracy. Raw and offset readings do not measure clothes moisture/dryness.
 * No physical rack-position sensors are present; states are command estimates.
 * Rain-plate disconnection cannot reliably be detected by this analog circuit.
 * Settings reset on reboot; app Auto/Manual must POST /api/config to affect it.
 *
 * Existing motion API preserved. New GET/POST /api/config supports runtime
 * settings; the Android app must be updated to call this additive endpoint.
 * STOP holds its command until a fresh movement command is accepted.
 *
 * Intended target (not hardware-tested): ESP32 Arduino core 3.x, board "ESP32 Dev Module".
 * Host simulation tests passed; full ESP32 build and hardware tests not performed.
 * Required libraries:
 *   - DHT sensor library by Adafruit 1.4.6+
 *   - Adafruit Unified Sensor 1.1.15+
 *   - ESP32Servo 3.0.6+
 *   - ArduinoJson 7.x
 * WiFi.h and WebServer.h are supplied by the ESP32 board package.
 */

#include <WiFi.h>
#include <WebServer.h>
#include <ESP32Servo.h>
#include <DHT.h>
#include <ArduinoJson.h>
#include <esp_system.h>

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
constexpr int RETRACT_ANGLE = 0;
constexpr int EXTEND_ANGLE = 90;
constexpr int SERVO_SPEED_DEG_PER_SECOND = 35;

// FC-37 AO is commonly lower when wet, but modules vary. Set true if a wet
// cloth makes the ADC value go down. The digital output polarity is separate.
constexpr bool RAIN_WET_WHEN_ANALOG_BELOW = true;
constexpr bool RAIN_DIGITAL_ACTIVE_LOW = true;
int RAIN_THRESHOLD = 1900;
constexpr int RAIN_HYSTERESIS = 180;
constexpr uint8_t RAIN_CONFIRM_READINGS = 3;
constexpr uint8_t RAIN_CLEAR_READINGS = 5;

// Runtime settings; defaults restored on reboot. See GET/POST /api/config.
bool RAIN_PROTECTION_ENABLED = true;
bool BUZZER_ENABLED = true;
bool BUZZER_ON_MOVEMENT = true;
enum OperatingMode { MODE_AUTO, MODE_MANUAL };
// Startup default. Runtime mode can be changed through /api/config.
// Rain protection applies in both modes, except while STOP is latched.
// AUTO extends only after confirmed dry readings plus a stable dry interval.
constexpr OperatingMode STARTUP_MODE = MODE_AUTO;
constexpr unsigned long SENSOR_PERIOD_MS = 2500;
constexpr unsigned long RAIN_PERIOD_MS = 250;
constexpr unsigned long SERVO_STEP_PERIOD_MS = 20;
constexpr unsigned long WIFI_RETRY_PERIOD_MS = 30000;
constexpr unsigned long BOOT_RETRACT_TIME_MS = 2500;
constexpr unsigned long MAX_MOTION_TIME_MS = 15000;

// Adjust only after comparing against a trusted reference instrument.
constexpr float TEMPERATURE_OFFSET_C = 0.0f;
constexpr float HUMIDITY_OFFSET_RH = 0.0f;
constexpr unsigned long AUTO_EXTEND_DRY_DELAY_MS = 10000;
constexpr unsigned long DHT_MAX_AGE_MS = 6000;
// Classic ESP32 WROOM-32 only: buzzer uses LEDC channel 14 (low-speed timer 3).
// ESP32Servo is restricted to timer 0 below to keep its 50 Hz PWM separate.
constexpr uint8_t BUZZER_LEDC_CHANNEL = 14;
constexpr char FIRMWARE_VERSION[] = "1.1.2";

// Six SINGLE-COLOR house LEDs, driven through suitable active-high drivers.
// GPIO -> driver input, NOT an unrestricted 5 V LED load. Use LED current limiting.
// Channels 2..7 use high-speed timers 1..3 on classic ESP32; servo uses timer 0.
// Buzzer channel 14 is in the other speed group, separate from house timers.
constexpr uint8_t HOUSE_LIGHT_PINS[6] = {23, 22, 21, 17, 16, 25};
constexpr uint8_t HOUSE_LIGHT_CHANNELS[6] = {2, 3, 4, 5, 6, 7};
constexpr uint32_t HOUSE_LIGHT_PWM_HZ = 1000;
constexpr uint8_t HOUSE_LIGHT_PWM_BITS = 8;
const char* const HOUSE_LIGHT_IDS[6] = {"front", "left", "right", "back", "cr", "balcony"};

// Physical RGB type is NOT identified: leave false until wiring is confirmed.
// This optional implementation is for a plain FOUR-PIN, low-current RGB LED
// with separate current-limiting resistors (not a two-wire color-cycle LED,
// not WS2812, not a high-power strip). Common cathode -> GND. For common anode
// at 3.3 V only set STATUS_RGB_COMMON_ANODE=true. Never tie GPIO to 5 V.
constexpr bool STATUS_RGB_ENABLED = false;
constexpr bool STATUS_RGB_COMMON_ANODE = false;
constexpr uint8_t STATUS_RGB_RED_PIN = 26;
constexpr uint8_t STATUS_RGB_GREEN_PIN = 32;
constexpr uint8_t STATUS_RGB_BLUE_PIN = 33;

struct HouseLightState {
  bool power = false;
  uint8_t brightness = 100;  // Remembered while OFF; volatile across resets.
  bool available = false;
  uint64_t revision = 0;
  String lastRequestId;
};
constexpr uint64_t MAX_LIGHT_VERSION = 9007199254740991ULL;
uint64_t lightingVersion = 0;
String lightingSession;
HouseLightState houseLights[6];
const char* statusIndicatorReason = "STARTING";
uint8_t statusRed = 0, statusGreen = 0, statusBlue = 255;

DHT dht(DHT_PIN, DHT22);
Servo rackServo;
WebServer server(80);

enum RackState { EXTENDED, RETRACTED, EXTENDING, RETRACTING, STOPPED, ERROR_STATE, UNKNOWN };
RackState rackState = UNKNOWN;
OperatingMode operatingMode = STARTUP_MODE;
int currentAngle = RETRACT_ANGLE;
int targetAngle = RETRACT_ANGLE;
bool moving = false;
bool stopLatched = false;
int motionStartAngle = RETRACT_ANGLE;
bool bootRetraction = false;
unsigned long motionStartedAt = 0;
unsigned long motionDuration = 0;

float temperatureC = NAN;
float humidityPercent = NAN;
bool dhtValid = false;
unsigned long lastSensorAt = 0;
unsigned long lastDhtSuccessAt = 0;
float rawTemperatureC = NAN;
float rawHumidityPercent = NAN;
uint32_t dhtReadFailures = 0;

int lastRainRaw = 0;
bool rainWet = false;
bool rainConfirmed = false;
uint8_t wetReadings = 0;
uint8_t clearReadings = 0;
unsigned long lastRainAt = 0;
bool previousRainConfirmed = false;
bool dryTimerRunning = false;
unsigned long dryStartedAt = 0;
bool buzzerAvailable = false;
bool buzzerActive = false;
bool buzzerOnPhase = false;
uint8_t buzzerPulsesRemaining = 0;
uint8_t buzzerPriority = 0;
uint16_t buzzerFrequency = 2400;
uint16_t buzzerOnMs = 250;
uint16_t buzzerOffMs = 180;
unsigned long buzzerPhaseAt = 0;

unsigned long lastServoStepAt = 0;
unsigned long lastWifiAttemptAt = 0;

struct CommandRecord {
  String requestId;
  String action;
  String response;
  int httpStatus;
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
  server.sendHeader("Cache-Control", "no-store");
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

bool parseBody(JsonDocument& doc) {
  if (!server.hasArg("plain") || server.arg("plain").length() > 1024) {
    sendError(400, "INVALID_REQUEST", "JSON object of at most 1024 bytes required");
    return false;
  }
  if (deserializeJson(doc, server.arg("plain")) || !doc.is<JsonObject>()) {
    sendError(400, "INVALID_JSON", "Valid JSON object required");
    return false;
  }
  return true;
}

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

CommandRecord* cachedResponse(const String& requestId) {
  for (uint8_t i = 0; i < COMMAND_CACHE_SIZE; ++i) {
    if (commandCache[i].requestId == requestId && millis() - commandCache[i].storedAt < 600000UL)
      return &commandCache[i];
  }
  return nullptr;
}

void cacheResponse(const String& requestId, const String& action, const String& response, int code) {
  commandCache[commandCacheNext] = { requestId, action, response, code, millis() };
  commandCacheNext = (commandCacheNext + 1) % COMMAND_CACHE_SIZE;
}

void stopBuzzer() {
  if (buzzerAvailable) ledcWriteTone(BUZZER_PIN, 0);
  buzzerActive = false;
  buzzerOnPhase = false;
  buzzerPulsesRemaining = 0;
  buzzerPriority = 0;
}

void startBuzzerPattern(uint16_t frequency, uint16_t onMs, uint16_t offMs,
                       uint8_t count, uint8_t priority) {
  if (!BUZZER_ENABLED || !buzzerAvailable || count == 0) return;
  if (buzzerActive && priority < buzzerPriority) return;
  buzzerFrequency = frequency;
  buzzerOnMs = onMs;
  buzzerOffMs = offMs;
  buzzerPulsesRemaining = count;
  buzzerPriority = priority;
  buzzerPhaseAt = millis();
  buzzerActive = true;
  buzzerOnPhase = true;
  ledcWriteTone(BUZZER_PIN, buzzerFrequency);
}

void beep(uint16_t frequency, uint16_t duration) {
  startBuzzerPattern(frequency, duration, 0, 1, 1);
}

void updateBuzzer() {
  if (!BUZZER_ENABLED) { stopBuzzer(); return; }
  if (!buzzerActive) return;
  unsigned long elapsed = millis() - buzzerPhaseAt;
  if (buzzerOnPhase && elapsed >= buzzerOnMs) {
    ledcWriteTone(BUZZER_PIN, 0);
    buzzerOnPhase = false;
    buzzerPhaseAt = millis();
    if (--buzzerPulsesRemaining == 0) stopBuzzer();
  } else if (!buzzerOnPhase && elapsed >= buzzerOffMs) {
    ledcWriteTone(BUZZER_PIN, buzzerFrequency);
    buzzerOnPhase = true;
    buzzerPhaseAt = millis();
  }
}

bool dhtReady() {
  return dhtValid && millis() - lastDhtSuccessAt <= DHT_MAX_AGE_MS;
}

void setServoError() {
  moving = false;
  bootRetraction = false;
  rackState = ERROR_STATE;
  stopBuzzer();
}

void beginMotion(bool retract) {
  dryTimerRunning = false;
  Serial.println(retract ? "Rack command: RETRACT" : "Rack command: EXTEND");
  targetAngle = retract ? RETRACT_ANGLE : EXTEND_ANGLE;
  targetAngle = constrain(targetAngle, 0, 180);
  int distance = abs(targetAngle - currentAngle);
  motionDuration = max(1000UL, (unsigned long)distance * 1000UL / max(1, SERVO_SPEED_DEG_PER_SECOND));
  motionStartAngle = currentAngle;
  lastServoStepAt = millis();
  motionStartedAt = millis();
  moving = true;
  bootRetraction = false;
  rackState = retract ? RETRACTING : EXTENDING;
  if (BUZZER_ON_MOVEMENT) beep(retract ? 1800 : 2400, 350);
}

void beginBootRetraction() {
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
  dryTimerRunning = false;
  stopLatched = true;
  moving = false;
  bootRetraction = false;
  rackState = STOPPED;
  stopBuzzer();
}

void updateServo() {
  if (!moving) return;
  const unsigned long now = millis();
  const unsigned long elapsed = now - motionStartedAt;
  if (bootRetraction) {
    if (elapsed >= BOOT_RETRACT_TIME_MS) {
      moving = false;
      bootRetraction = false;
      rackState = RETRACTED;  // Command estimate only.
    }
    return;
  }
  if (elapsed >= MAX_MOTION_TIME_MS && currentAngle != targetAngle) {
    setServoError();  // Hold last commanded angle; do not jump to endpoint.
    return;
  }
  if (now - lastServoStepAt < SERVO_STEP_PERIOD_MS) return;
  lastServoStepAt = now;
  const unsigned long travel = min((unsigned long)abs(targetAngle - motionStartAngle),
      elapsed * (unsigned long)SERVO_SPEED_DEG_PER_SECOND / 1000UL);
  currentAngle = motionStartAngle + (targetAngle >= motionStartAngle ? (int)travel : -(int)travel);
  rackServo.write(currentAngle);
  if (currentAngle == targetAngle) {
    moving = false;
    rackState = targetAngle == RETRACT_ANGLE ? RETRACTED : EXTENDED;
  }
}

void updateDht() {
  if (millis() - lastSensorAt < SENSOR_PERIOD_MS) return;
  lastSensorAt = millis();
  float h = dht.readHumidity();
  float t = dht.readTemperature();
  if (isfinite(h) && isfinite(t) && t >= -40.0f && t <= 80.0f && h >= 0.0f && h <= 100.0f) {
    rawTemperatureC = t;
    rawHumidityPercent = h;
    temperatureC = t + TEMPERATURE_OFFSET_C;
    humidityPercent = h + HUMIDITY_OFFSET_RH;
    dhtValid = isfinite(temperatureC) && isfinite(humidityPercent) &&
        temperatureC >= -40.0f && temperatureC <= 80.0f &&
        humidityPercent >= 0.0f && humidityPercent <= 100.0f;
    if (dhtValid) lastDhtSuccessAt = millis();
  } else {
    dhtValid = false;
  }
  if (!dhtReady()) ++dhtReadFailures;
}

bool readRainWet() {
  if (USE_RAIN_DIGITAL_OUTPUT) {
    bool active = digitalRead(RAIN_DO_PIN) == (RAIN_DIGITAL_ACTIVE_LOW ? LOW : HIGH);
    lastRainRaw = digitalRead(RAIN_DO_PIN);
    rainWet = active;
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
    dryTimerRunning = false;
    clearReadings = 0;
    if (wetReadings < RAIN_CONFIRM_READINGS) wetReadings++;
    if (wetReadings >= RAIN_CONFIRM_READINGS) rainConfirmed = true;
  } else {
    wetReadings = 0;
    if (clearReadings < RAIN_CLEAR_READINGS) clearReadings++;
    if (clearReadings >= RAIN_CLEAR_READINGS) rainConfirmed = false;
  }
  if (RAIN_PROTECTION_ENABLED && rainConfirmed && !previousRainConfirmed) {
    Serial.println("Rain CONFIRMED: protection active if enabled");
    startBuzzerPattern(2400, 300, 200, 3, 3);
    if (!stopLatched && (rackState == EXTENDED || rackState == EXTENDING || rackState == STOPPED)) beginMotion(true);
  }
  if (!rainConfirmed && previousRainConfirmed) Serial.println("Rain cleared: waiting for stable dry period");
  previousRainConfirmed = rainConfirmed;
}

void updateAutomation() {
  if (stopLatched || rackState == ERROR_STATE || !rackServo.attached()) {
    dryTimerRunning = false;
    return;
  }
  if (RAIN_PROTECTION_ENABLED && rainConfirmed) {
    dryTimerRunning = false;
    if (rackState == EXTENDED || rackState == EXTENDING || rackState == STOPPED) beginMotion(true);
    return;
  }
  // A single wet sample cancels dry readiness, before wet debounce completes.
  bool ready = operatingMode == MODE_AUTO && !rainWet && !rainConfirmed &&
      clearReadings >= RAIN_CLEAR_READINGS && dhtReady() &&
      rackState == RETRACTED && !moving;
  if (!ready) { dryTimerRunning = false; return; }
  if (!dryTimerRunning) {
    dryStartedAt = millis();
    dryTimerRunning = true;
    Serial.println("AUTO: dry confirmed; extending in 10 seconds if conditions remain valid");
  } else if (millis() - dryStartedAt >= AUTO_EXTEND_DRY_DELAY_MS) {
    beginMotion(false);
  }
}

void printDiagnostics() {
  static unsigned long lastReportAt = 0;
  if (millis() - lastReportAt < 5000UL) return;
  lastReportAt = millis();
  Serial.print("Rain raw="); Serial.print(lastRainRaw);
  Serial.print(" wet="); Serial.print(rainWet);
  Serial.print(" confirmed="); Serial.print(rainConfirmed);
  Serial.print(" WiFi="); Serial.print((int)WiFi.status());
  Serial.print(" mode="); Serial.print(modeName());
  Serial.print(" stop="); Serial.print(stopLatched);
  Serial.print(" DHT valid="); Serial.print(dhtReady());
  if (dhtReady()) {
    Serial.print(" T="); Serial.print(temperatureC, 1);
    Serial.print("C RH="); Serial.print(humidityPercent, 1);
  }
  Serial.print(" failures="); Serial.println(dhtReadFailures);
}

void updateWifi() {
  static bool wasConnected = false;
  if (WiFi.status() == WL_CONNECTED) {
    if (!wasConnected) {
      Serial.print("Device URL: http://");
      Serial.println(WiFi.localIP());
    }
    wasConnected = true;
    return;
  }
  wasConnected = false;
  if (millis() - lastWifiAttemptAt < WIFI_RETRY_PERIOD_MS) return;
  lastWifiAttemptAt = millis();
  WiFi.disconnect(false, false);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
}

void handleOptions() { sendJson(204, "{}"); }

void handleStatus() {
  // A failed DHT sample is deliberately unavailable rather than silently
  // presenting cached readings as current. Rain protection continues locally.
  if (!dhtReady()) {
    // Keep HTTP 503 and original error fields for API compatibility. Include
    // independent device information so the app can distinguish a sensor
    // failure from a lost network connection. Never invent sensor readings.
    JsonDocument doc;
    doc["error"] = "SENSOR_FAILURE";
    doc["message"] = "DHT22 reading unavailable";
    doc["state"] = stateName(rackState);
    doc["firmwareVersion"] = FIRMWARE_VERSION;
    doc["deviceResponding"] = true;
    doc["dhtValid"] = false;
    doc["mode"] = modeName();
    doc["stopLatched"] = stopLatched;
    doc["positionSource"] = "COMMAND_ESTIMATE";
    doc["positionMeasured"] = false;
    doc["rainProtectionEnabled"] = RAIN_PROTECTION_ENABLED;
    doc["rainRaw"] = lastRainRaw;
    JsonObject sensors = doc["sensors"].to<JsonObject>();
    sensors["temperature"] = nullptr;
    sensors["humidity"] = nullptr;
    sensors["rainDetected"] = rainConfirmed;
    sensors["clothesDryPercentage"] = nullptr;
    doc["diagnosticsPath"] = "/api/diagnostics";
    String failureBody;
    serializeJson(doc, failureBody);
    sendJson(503, failureBody);
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
  body += ",\"rainRaw\":" + String(lastRainRaw);
  body += ",\"dhtSampleAgeMs\":" + String(millis() - lastDhtSuccessAt);
  body += ",\"buzzerAvailable\":" + String(buzzerAvailable ? "true" : "false");
  body += ",\"buzzerEnabled\":" + String(BUZZER_ENABLED ? "true" : "false");
  body += ",\"stopLatched\":" + String(stopLatched ? "true" : "false");
  body += "}";
  sendJson(200, body);
}

void handleCommand() {
  if (!server.hasArg("plain")) {
    sendError(400, "INVALID_REQUEST", "JSON request body is required");
    return;
  }
  JsonDocument doc;
  if (!parseBody(doc)) return;
  if (!doc["requestId"].is<const char*>() || !doc["action"].is<const char*>()) {
    sendError(400, "INVALID_REQUEST", "action and requestId must be strings");
    return;
  }
  String requestId = doc["requestId"].as<String>();
  String action = doc["action"].as<String>();
  if (requestId.isEmpty() || requestId.length() > 128 || action.isEmpty()) {
    sendError(400, "INVALID_REQUEST", "action and requestId are required");
    return;
  }
  CommandRecord* previous = cachedResponse(requestId);
  if (previous != nullptr) {
    if (previous->action != action) {
      sendJson(409, statusJson(false, "REQUEST_ID_CONFLICT"));
    } else sendJson(previous->httpStatus, previous->response);
    return;
  }

  String response;
  if (action == "STOP") {
    stopMotion();
    response = statusJson(true);
  } else if (action == "RETRACT") {
    if (!rackServo.attached()) response = statusJson(false, "SERVO_UNAVAILABLE");
    else if (rackState == RETRACTED && !moving) response = statusJson(false, "ALREADY_RETRACTED");
    else {
      stopLatched = false;
      beginMotion(true);
      response = statusJson(true);
    }
  } else if (action == "EXTEND") {
    if (!rackServo.attached()) response = statusJson(false, "SERVO_UNAVAILABLE");
    else if (RAIN_PROTECTION_ENABLED && rainConfirmed) response = statusJson(false, "RAIN_PROTECTION");
    else if (!dhtReady()) response = statusJson(false, "SENSOR_UNAVAILABLE");
    else if ((rackState == EXTENDED) && !moving) response = statusJson(false, "ALREADY_EXTENDED");
    else {
      stopLatched = false;
      beginMotion(false);
      response = statusJson(true);
    }
  } else {
    response = statusJson(false, "UNKNOWN_ACTION");
  }
  int code = response.startsWith("{\"accepted\":true") ? 200 : 409;
  cacheResponse(requestId, action, response, code);
  sendJson(code, response);
}

// Additive API: requires mobile app integration; settings reset on restart.
void handleGetConfig() {
  JsonDocument doc;
  doc["mode"] = modeName();
  doc["rainProtectionEnabled"] = RAIN_PROTECTION_ENABLED;
  doc["buzzerEnabled"] = BUZZER_ENABLED;
  doc["buzzerOnMovement"] = BUZZER_ON_MOVEMENT;
  doc["rainThreshold"] = RAIN_THRESHOLD;
  String body;
  serializeJson(doc, body);
  sendJson(200, body);
}

void handleSetConfig() {
  JsonDocument doc;
  if (!parseBody(doc)) return;
  JsonObject obj = doc.as<JsonObject>();
  if (obj.size() == 0) {
    sendError(400, "INVALID_CONFIG", "At least one setting required");
    return;
  }
  // Validate all fields before applying any changes.
  for (JsonPair pair : obj) {
    String key = pair.key().c_str();
    JsonVariant value = pair.value();
    bool valid = false;
    if (key == "mode") {
      valid = value.is<const char*>() &&
          (String(value.as<const char*>()) == "AUTO" || String(value.as<const char*>()) == "MANUAL");
    } else if (key == "rainProtectionEnabled" || key == "buzzerEnabled" || key == "buzzerOnMovement") {
      valid = value.is<bool>();
    } else if (key == "rainThreshold") {
      valid = value.is<int>() && value.as<int>() >= RAIN_HYSTERESIS && value.as<int>() <= 4095 - RAIN_HYSTERESIS;
    }
    if (!valid) {
      sendError(400, "INVALID_CONFIG", "Unknown setting or invalid value");
      return;
    }
  }
  if (obj["mode"].is<const char*>()) operatingMode = String(obj["mode"].as<const char*>()) == "AUTO" ? MODE_AUTO : MODE_MANUAL;
  if (obj["rainProtectionEnabled"].is<bool>()) RAIN_PROTECTION_ENABLED = obj["rainProtectionEnabled"].as<bool>();
  if (obj["buzzerEnabled"].is<bool>()) BUZZER_ENABLED = obj["buzzerEnabled"].as<bool>();
  if (obj["buzzerOnMovement"].is<bool>()) BUZZER_ON_MOVEMENT = obj["buzzerOnMovement"].as<bool>();
  if (obj["rainThreshold"].is<int>()) {
    RAIN_THRESHOLD = obj["rainThreshold"].as<int>();
    wetReadings = clearReadings = 0;
  }
  dryTimerRunning = false;
  if (!BUZZER_ENABLED) stopBuzzer();
  handleGetConfig();
}

// Diagnostics remains available even if DHT22 has failed.
void handleDiagnostics() {
  JsonDocument doc;
  doc["rainRaw"] = lastRainRaw;
  doc["rainWet"] = rainWet;
  doc["rainDetected"] = rainConfirmed;
  doc["rainThreshold"] = RAIN_THRESHOLD;
  doc["mode"] = modeName();
  doc["state"] = stateName(rackState);
  doc["stopLatched"] = stopLatched;
  doc["dhtValid"] = dhtReady();
  doc["dhtReadFailures"] = dhtReadFailures;
  doc["temperatureOffsetC"] = TEMPERATURE_OFFSET_C;
  doc["humidityOffsetRH"] = HUMIDITY_OFFSET_RH;
  if (dhtReady()) {
    doc["rawTemperatureC"] = rawTemperatureC;
    doc["rawHumidityRH"] = rawHumidityPercent;
    doc["sampleAgeMs"] = millis() - lastDhtSuccessAt;
  } else {
    doc["rawTemperatureC"] = nullptr;
    doc["rawHumidityRH"] = nullptr;
    doc["sampleAgeMs"] = nullptr;
  }
  doc["buzzerAvailable"] = buzzerAvailable;
  doc["buzzerEnabled"] = BUZZER_ENABLED;
  doc["dryTimerRunning"] = dryTimerRunning;
  doc["dryDelayMs"] = AUTO_EXTEND_DRY_DELAY_MS;
  String body;
  serializeJson(doc, body);
  sendJson(200, body);
}

void handleBuzzerTest() {
  if (!buzzerAvailable || !BUZZER_ENABLED) {
    sendError(409, "BUZZER_UNAVAILABLE", "Check buzzer initialization and enabled setting");
    return;
  }
  if (buzzerActive && buzzerPriority > 2) {
    sendError(409, "BUZZER_BUSY", "Rain alert is playing");
    return;
  }
  startBuzzerPattern(2400, 400, 250, 3, 2);
  sendJson(200, statusJson(true));
}

// ---------------------------- Lighting --------------------------------------
uint8_t brightnessToDuty(uint8_t percent) {
  return (uint8_t)(((uint16_t)percent * 255U + 50U) / 100U);
}

int findHouseLight(const String& id) {
  for (uint8_t i = 0; i < 6; ++i) if (id == HOUSE_LIGHT_IDS[i]) return i;
  return -1;  // Includes "status": this indicator is never manually controllable.
}

bool applyHouseLight(uint8_t index, bool power, uint8_t brightness) {
  if (index >= 6 || brightness > 100 || !houseLights[index].available) return false;
  uint8_t duty = power ? brightnessToDuty(brightness) : 0;
  if (lightingVersion == MAX_LIGHT_VERSION) return false;
  if (!ledcWrite(HOUSE_LIGHT_PINS[index], duty)) {
    houseLights[index].available = false;
    houseLights[index].revision = ++lightingVersion;
    houseLights[index].lastRequestId = "";
    return false;
  }
  houseLights[index].revision = ++lightingVersion;
  houseLights[index].lastRequestId = ""; // Legacy writes invalidate prior app request identity.
  houseLights[index].power = power;
  houseLights[index].brightness = brightness;
  return true;
}

void initLighting() {
  for (uint8_t i = 0; i < 6; ++i) {
    pinMode(HOUSE_LIGHT_PINS[i], OUTPUT);
    digitalWrite(HOUSE_LIGHT_PINS[i], LOW);
    houseLights[i].power = false;
    houseLights[i].brightness = 100;
    houseLights[i].available = ledcAttachChannel(HOUSE_LIGHT_PINS[i],
        HOUSE_LIGHT_PWM_HZ, HOUSE_LIGHT_PWM_BITS, HOUSE_LIGHT_CHANNELS[i]);
    if (houseLights[i].available) {
      if (!ledcWrite(HOUSE_LIGHT_PINS[i], 0)) houseLights[i].available = false;
    }
    if (!houseLights[i].available) {
      Serial.print("House-light PWM initialization failed: ");
      Serial.println(HOUSE_LIGHT_IDS[i]);
    }
  }
  if (STATUS_RGB_ENABLED) {
    const uint8_t pins[3] = {STATUS_RGB_RED_PIN, STATUS_RGB_GREEN_PIN, STATUS_RGB_BLUE_PIN};
    for (uint8_t i = 0; i < 3; ++i) {
      digitalWrite(pins[i], STATUS_RGB_COMMON_ANODE ? HIGH : LOW);
      pinMode(pins[i], OUTPUT);
    }
  }
}

void updateStatusIndicator() {
  // Independent automatic indicator. No house-light request reaches these pins.
  // Priority: servo fault -> sensor fault -> rain -> STOP -> Wi-Fi -> starting
  // -> moving -> extended/dry -> retracted/idle.
  uint8_t red = 0, green = 0, blue = 0;
  const char* reason;
  if (rackState == ERROR_STATE || !rackServo.attached()) {
    reason = "SERVO_ERROR"; red = 255; green = 255;
  } else if (millis() >= SENSOR_PERIOD_MS && !dhtReady()) {
    reason = "SENSOR_ERROR"; red = 255; green = 255;
  } else if (rainConfirmed) {
    reason = "RAIN_DETECTED"; red = 255;
  } else if (stopLatched) {
    reason = "STOPPED"; red = 255; green = 255;
  } else if (WiFi.status() != WL_CONNECTED) {
    reason = "WIFI_CONNECTING"; blue = 255;
  } else if (rainWet) {
    // Moisture has been sampled, but rain debounce has not yet confirmed it.
    // Do not mislabel an extended rack as retracted during this transition.
    reason = "RAIN_PENDING"; red = 255; green = 255;
  } else if (moving || rackState == EXTENDING || rackState == RETRACTING) {
    reason = "RACK_MOVING"; green = 255; blue = 255;
  } else if (rackState == EXTENDED) {
    reason = "DRY_EXTENDED"; green = 255;
  } else if (rackState == RETRACTED) {
    reason = "RETRACTED_IDLE"; red = green = blue = 255;
  } else if (rackState == STOPPED) {
    reason = "STOPPED"; red = 255; green = 255;
  } else {
    reason = "UNKNOWN_POSITION"; red = 255; green = 255;
  }
  bool changed = red != statusRed || green != statusGreen || blue != statusBlue || strcmp(reason, statusIndicatorReason) != 0;
  if (changed && lightingVersion < MAX_LIGHT_VERSION) ++lightingVersion;
  statusRed = red; statusGreen = green; statusBlue = blue;
  statusIndicatorReason = reason;
  static bool written = false;
  if (STATUS_RGB_ENABLED && (changed || !written)) {
    const bool activeLow = STATUS_RGB_COMMON_ANODE;
    digitalWrite(STATUS_RGB_RED_PIN, (red > 0) != activeLow ? HIGH : LOW);
    digitalWrite(STATUS_RGB_GREEN_PIN, (green > 0) != activeLow ? HIGH : LOW);
    digitalWrite(STATUS_RGB_BLUE_PIN, (blue > 0) != activeLow ? HIGH : LOW);
    written = true;
  }
}

void handleGetLights() {
  updateStatusIndicator();
  JsonDocument doc;
  doc["firmwareVersion"] = FIRMWARE_VERSION;
  doc["settingsPersist"] = false;
  // Add schema-2 fields without removing the uploaded sketch's legacy fields.
  doc["schemaVersion"] = 2;
  doc["deviceSession"] = lightingSession;
  doc["stateVersion"] = lightingVersion;
  JsonArray zones = doc["zones"].to<JsonArray>();
  for (uint8_t i = 0; i < 6; ++i) {
    JsonObject zone = zones.add<JsonObject>();
    zone["id"] = HOUSE_LIGHT_IDS[i];
    zone["power"] = houseLights[i].power;
    zone["brightness"] = houseLights[i].brightness;
    zone["available"] = houseLights[i].available;
    zone["revision"] = houseLights[i].revision;
    if (houseLights[i].lastRequestId.length()) zone["lastRequestId"] = houseLights[i].lastRequestId;
    else zone["lastRequestId"] = nullptr;
  }
  if (STATUS_RGB_ENABLED) {
    JsonObject actual = doc["indicator"].to<JsonObject>();
    actual["id"] = "system_status";
    actual["mode"] = "AUTOMATIC";
    actual["power"] = true;
    actual["brightness"] = 100; // Uploaded RGB implementation is digital ON/OFF per color.
    actual["rgb"]["r"] = statusRed;
    actual["rgb"]["g"] = statusGreen;
    actual["rgb"]["b"] = statusBlue;
    actual["meaning"] = statusIndicatorReason;
    actual["description"] = statusIndicatorReason;
    actual["pattern"] = "solid";
  } else doc["indicator"] = nullptr; // Logical desired colors are not physical RGB state.
  JsonArray lights = doc["lights"].to<JsonArray>();
  for (uint8_t i = 0; i < 6; ++i) {
    JsonObject light = lights.add<JsonObject>();
    light["id"] = HOUSE_LIGHT_IDS[i];
    light["power"] = houseLights[i].power;
    light["brightness"] = houseLights[i].brightness;
    light["available"] = houseLights[i].available;
    light["type"] = "SINGLE_COLOR";
    light["outputDuty"] = houseLights[i].power ? brightnessToDuty(houseLights[i].brightness) : 0;
  }
  JsonObject indicator = doc["statusIndicator"].to<JsonObject>();
  indicator["automatic"] = true;
  indicator["hardwareEnabled"] = STATUS_RGB_ENABLED;
  indicator["reason"] = statusIndicatorReason;
  indicator["r"] = statusRed;
  indicator["g"] = statusGreen;
  indicator["b"] = statusBlue;
  String body;
  serializeJson(doc, body);
  sendJson(200, body);
}

void handleSetLight() {
  JsonDocument doc;
  if (!parseBody(doc)) return;
  JsonObject obj = doc.as<JsonObject>();
  // Reject unknown fields, including color and attempts to alter the RGB status.
  for (JsonPair pair : obj) {
    String key = pair.key().c_str();
    if (key != "id" && key != "power" && key != "brightness") {
      sendError(400, "INVALID_LIGHT", "Only id, power and brightness are accepted");
      return;
    }
  }
  if (!obj["id"].is<const char*>()) {
    sendError(400, "INVALID_LIGHT", "A house-light id is required");
    return;
  }
  int index = findHouseLight(obj["id"].as<const char*>());
  if (index < 0) {
    sendError(400, "INVALID_LIGHT", "Use front, left, right, back, cr or balcony; status is automatic");
    return;
  }
  bool hasPower = obj.containsKey("power");
  bool hasBrightness = obj.containsKey("brightness");
  if ((!hasPower && !hasBrightness) ||
      (hasPower && !obj["power"].is<bool>()) ||
      (hasBrightness && (!obj["brightness"].is<int>() ||
          obj["brightness"].as<int>() < 0 || obj["brightness"].as<int>() > 100))) {
    sendError(400, "INVALID_LIGHT", "Provide boolean power and/or integer brightness 0..100");
    return;
  }
  bool power = hasPower ? obj["power"].as<bool>() : houseLights[index].power;
  uint8_t brightness = hasBrightness ? obj["brightness"].as<int>() : houseLights[index].brightness;
  if (!applyHouseLight((uint8_t)index, power, brightness)) {
    sendError(503, "LIGHT_UNAVAILABLE", "House-light PWM initialization/write failed");
    return;
  }
  handleGetLights();
}


// App-compatible additive API. IDs, revisions and session prevent stale/replayed changes.
bool validLightToken(const char* value) {
  if (!value || !*value || strlen(value) > 96) return false;
  for (const char* c = value; *c; ++c) {
    if (!((*c >= 'a' && *c <= 'z') || (*c >= 'A' && *c <= 'Z') || (*c >= '0' && *c <= '9') ||
          *c == '.' || *c == '_' || *c == '-')) return false;
  }
  return true;
}
// ArduinoJson keeps the last duplicate key. Reject duplicates in these flat app schemas.
bool uniqueFlatFields(const String& body, size_t expected) {
  bool quoted = false, escaped = false;
  unsigned depth = 0;
  size_t fields = 0;
  for (unsigned i = 0; i < body.length(); ++i) {
    char c = body[i];
    if (quoted) {
      if (escaped) escaped = false;
      else if (c == '\\') escaped = true;
      else if (c == '"') quoted = false;
    } else if (c == '"') quoted = true;
    else if (c == '{') ++depth;
    else if (c == '}') --depth;
    else if (c == ':' && depth == 1) ++fields;
  }
  return fields == expected;
}
struct LightCommandRecord {
  String id, response;
  uint8_t zone = 0;
  bool power = false;
  uint8_t brightness = 0;
  uint64_t expectedRevision = 0;
};
LightCommandRecord lightCommands[32];
uint8_t nextLightCommand = 0;
void handleAppLight(uint8_t index) {
  JsonDocument doc;
  if (!parseBody(doc)) return;
  JsonObject obj = doc.as<JsonObject>();
  for (JsonPair pair : obj) {
    String key = pair.key().c_str();
    if (key != "requestId" && key != "deviceSession" && key != "expectedRevision" && key != "power" && key != "brightness") {
      sendError(400, "INVALID_LIGHT", "Only requestId, deviceSession, expectedRevision, power and brightness are accepted"); return;
    }
  }
  if (!uniqueFlatFields(server.arg("plain"), 5) || !obj["requestId"].is<const char*>() || !validLightToken(obj["requestId"].as<const char*>()) ||
      !obj["deviceSession"].is<const char*>() || !validLightToken(obj["deviceSession"].as<const char*>()) ||
      !obj["expectedRevision"].is<uint64_t>() || obj["expectedRevision"].as<uint64_t>() > MAX_LIGHT_VERSION ||
      !obj["power"].is<bool>() || !obj["brightness"].is<int>() ||
      obj["brightness"].as<int>() < 0 || obj["brightness"].as<int>() > 100) {
    sendError(400, "INVALID_LIGHT", "Strict tokens, revision, boolean power and integer brightness 0..100 required"); return;
  }
  if (lightingSession != obj["deviceSession"].as<const char*>()) {
    sendError(409, "SESSION_CHANGED", "Device restarted; fetch actual state"); return;
  }
  String id = obj["requestId"].as<const char*>();
  bool power = obj["power"].as<bool>();
  uint8_t brightness = obj["brightness"].as<int>();
  uint64_t expected = obj["expectedRevision"].as<uint64_t>();
  for (const auto& cached : lightCommands) {
    if (cached.id == id) {
      if (cached.zone != index || cached.power != power || cached.brightness != brightness || cached.expectedRevision != expected) {
        sendError(409, "REQUEST_ID_REUSE", "Request ID already used for another setting"); return;
      }
      sendJson(200, cached.response); return;
    }
  }
  if (houseLights[index].revision != expected) {
    sendError(409, "REVISION_CONFLICT", "Zone changed; fetch actual state"); return;
  }
  if (!applyHouseLight(index, power, brightness)) {
    sendError(503, "LIGHT_UNAVAILABLE", "PWM output unavailable; no success confirmed"); return;
  }
  houseLights[index].lastRequestId = id;
  JsonDocument ack;
  ack["accepted"] = true;
  ack["requestId"] = id;
  ack["zone"] = HOUSE_LIGHT_IDS[index];
  ack["deviceSession"] = lightingSession;
  ack["revision"] = houseLights[index].revision;
  String response; serializeJson(ack, response);
  auto& cached = lightCommands[nextLightCommand];
  cached.id = id; cached.zone = index; cached.power = power; cached.brightness = brightness;
  cached.expectedRevision = expected; cached.response = response;
  nextLightCommand = (nextLightCommand + 1) % 32;
  sendJson(200, response);
}
void handleReadOnlyIndicator() { sendError(403, "READ_ONLY_INDICATOR", "RGB status is automatic"); }

// Preserve uploaded /api/config and /api/buzzer/test; add the existing Android toggle contract.
void handleAppBuzzer() {
  JsonDocument doc;
  if (!parseBody(doc)) return;
  JsonObject obj = doc.as<JsonObject>();
  if (obj.size() != 2 || !uniqueFlatFields(server.arg("plain"), 2) || !obj["enabled"].is<bool>() || !obj["requestId"].is<const char*>() ||
      !validLightToken(obj["requestId"].as<const char*>())) {
    sendError(400, "INVALID_REQUEST", "enabled boolean and requestId required"); return;
  }
  if (!buzzerAvailable) { sendError(503, "BUZZER_UNAVAILABLE", "Buzzer PWM unavailable"); return; }
  String key = String("buzzer:") + obj["requestId"].as<const char*>();
  bool enabled = obj["enabled"].as<bool>();
  String action = enabled ? "ENABLE" : "DISABLE";
  CommandRecord* previous = cachedResponse(key);
  if (previous) {
    if (previous->action != action) { sendError(409, "REQUEST_ID_REUSE", "Request ID already used"); return; }
    sendJson(previous->httpStatus, previous->response); return;
  }
  BUZZER_ENABLED = enabled;
  if (!enabled) stopBuzzer();
  else startBuzzerPattern(2400, 150, 100, 1, 1);
  String response = "{\"accepted\":true,\"buzzerEnabled\":" + String(enabled ? "true" : "false") + "}";
  cacheResponse(key, action, response, 200);
  sendJson(200, response);
}

void handleNotFound() { sendError(404, "NOT_FOUND", "Use /api/status, /api/commands or /api/config"); }

void setup() {
  Serial.begin(115200);
  pinMode(BUZZER_PIN, OUTPUT);
  pinMode(RAIN_AO_PIN, INPUT);
  if (USE_RAIN_DIGITAL_OUTPUT) pinMode(RAIN_DO_PIN, INPUT);
  analogReadResolution(12);
  analogSetPinAttenuation(RAIN_AO_PIN, ADC_11db);

  dht.begin();
  ESP32PWM::allocateTimer(0);
  rackServo.setPeriodHertz(50);
  rackServo.attach(SERVO_PIN, 500, 2400);
  if (!rackServo.attached()) {
    setServoError();
    Serial.println("Servo PWM attach failed");
  }
  if (rackServo.attached()) beginBootRetraction();
  buzzerAvailable = ledcAttachChannel(BUZZER_PIN, 2400, 10, BUZZER_LEDC_CHANNEL);
  if (buzzerAvailable) {
    startBuzzerPattern(2400, 250, 200, 2, 1);
  } else Serial.println("Buzzer PWM attach failed");

  initLighting();
  char session[33];
  snprintf(session, sizeof(session), "%08lx%08lx%08lx%08lx", (unsigned long)esp_random(),
      (unsigned long)esp_random(), (unsigned long)esp_random(), (unsigned long)esp_random());
  lightingSession = session;

  WiFi.mode(WIFI_STA);
  WiFi.setHostname("smart-drying-rack");
  WiFi.setAutoReconnect(true);
  WiFi.persistent(false);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  lastWifiAttemptAt = millis();

  server.on("/api/status", HTTP_GET, handleStatus);
  server.on("/api/status", HTTP_OPTIONS, handleOptions);
  server.on("/api/commands", HTTP_POST, handleCommand);
  server.on("/api/commands", HTTP_OPTIONS, handleOptions);
  server.on("/api/config", HTTP_GET, handleGetConfig);
  server.on("/api/config", HTTP_POST, handleSetConfig);
  server.on("/api/config", HTTP_OPTIONS, handleOptions);
  server.on("/api/diagnostics", HTTP_GET, handleDiagnostics);
  server.on("/api/diagnostics", HTTP_OPTIONS, handleOptions);
  server.on("/api/buzzer/test", HTTP_POST, handleBuzzerTest);
  server.on("/api/buzzer/test", HTTP_OPTIONS, handleOptions);
  server.on("/api/lights", HTTP_GET, handleGetLights);
  server.on("/api/lights", HTTP_POST, handleSetLight);
  server.on("/api/lights", HTTP_OPTIONS, handleOptions);
  for (uint8_t i = 0; i < 6; ++i) {
    String path = String("/api/lights/zones/") + HOUSE_LIGHT_IDS[i];
    server.on(path.c_str(), HTTP_POST, [i]() { handleAppLight(i); });
    server.on(path.c_str(), HTTP_OPTIONS, handleOptions);
  }
  server.on("/api/lights/zones/system_status", HTTP_POST, handleReadOnlyIndicator);
  server.on("/api/buzzer", HTTP_POST, handleAppBuzzer);
  server.on("/api/buzzer", HTTP_OPTIONS, handleOptions);
  server.onNotFound(handleNotFound);
  server.begin();
  Serial.println("SMART RACK 1.1.2 - APP COMPATIBLE LIGHTS AND STATUS FIXES");
  Serial.println("Smart Drying Rack API started; waiting for Wi-Fi");
  if (!STATUS_RGB_ENABLED) Serial.println("RGB status physical output disabled: confirm LED type before enabling");
}

void loop() {
  server.handleClient();
  updateWifi();
  updateRain();
  updateDht();
  updateAutomation();
  updateServo();
  updateBuzzer();
  updateStatusIndicator();
  printDiagnostics();
  delay(1);
}
