#pragma once
// Use the real checksum-installed ArduinoJson library; adapt only Arduino String I/O.
#include "HardwareStubs.h"
#include <ArduinoJson.hpp>
using namespace ArduinoJson;
inline void convertFromJson(JsonVariantConst value, String& destination) { destination = String(value.as<const char*>()); }
inline void convertToJson(const String& value, JsonVariant destination) { destination.set(value.value); }
inline DeserializationError deserializeJson(JsonDocument& doc, const String& input) {
  return ArduinoJson::deserializeJson(doc, input.value);
}
inline DeserializationError deserializeJson(JsonDocument& doc, String& input) {
  return ArduinoJson::deserializeJson(doc, input.value);
}
inline DeserializationError deserializeJson(JsonDocument& doc, String&& input) {
  return ArduinoJson::deserializeJson(doc, input.value);
}
inline size_t serializeJson(const JsonDocument& doc, String& output) {
  std::string text;
  size_t count = ArduinoJson::serializeJson(doc, text);
  output = String(text);
  return count;
}
