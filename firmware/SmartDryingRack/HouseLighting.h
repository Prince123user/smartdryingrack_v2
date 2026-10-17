#pragma once
// Hardware-independent integration component, NOT enabled by the repository sketch.
// Include/adapt only after inspecting the user's working firmware and actual hardware.
#include <array>
#include <cstdint>
#include <string>

namespace house_lighting {
constexpr uint64_t MAX_VERSION = 9007199254740991ULL;
constexpr unsigned ZONE_COUNT = 6;
constexpr unsigned RECENT_REQUESTS = 32;
inline const char* zoneId(unsigned index) {
  static const char* const ids[ZONE_COUNT] = {"front", "left", "right", "back", "cr", "balcony"};
  return index < ZONE_COUNT ? ids[index] : "";
}
inline int zoneIndex(const std::string& id) {
  for (unsigned i = 0; i < ZONE_COUNT; ++i) if (id == zoneId(i)) return static_cast<int>(i);
  return -1;
}
struct Zone {
  bool power = false;
  uint8_t brightness = 50; // Percent, retained while OFF; RAM only, resets after boot.
  uint64_t revision = 0;
  std::string lastRequestId;
};
struct Request {
  std::string zone, deviceSession, requestId;
  uint64_t expectedRevision;
  bool power;
  int brightness;
};
enum class Result {
  ACCEPTED, NOT_READY, UNKNOWN_ZONE, READ_ONLY_INDICATOR, INVALID_REQUEST,
  SESSION_CHANGED, REVISION_CONFLICT, REQUEST_ID_REUSE, VERSION_EXHAUSTED, DRIVER_FAILURE
};
struct Reply { Result result; uint64_t revision; bool accepted() const { return result == Result::ACCEPTED; } };

// Implement this against the confirmed LED driver, not against servo/buzzer PWM.
// Return true only after the addressed output has been applied. No delays, waits,
// retry loops or changes to other zones; use a bounded driver transaction.
struct Output {
  virtual ~Output() = default;
  virtual bool write(unsigned zone, bool power, uint8_t brightnessPercent) = 0;
};

class Controller {
 public:
  explicit Controller(Output& output) : output_(output) {}
  bool begin(const std::string& uniqueBootSession) {
    ready_ = false;
    if (!validToken(uniqueBootSession) || (!session_.empty() && session_ == uniqueBootSession)) return false;
    for (unsigned i = 0; i < ZONE_COUNT; ++i) {
      if (!output_.write(i, false, 0)) return false; // Confirm OFF before exposing boot state.
    }
    session_ = uniqueBootSession;
    version_ = 0;
    zones_ = {};
    cache_ = {};
    next_ = 0;
    ready_ = true;
    return true;
  }
  bool ready() const { return ready_; }
  const std::string& session() const { return session_; }
  uint64_t version() const { return version_; }
  const std::array<Zone, ZONE_COUNT>& zones() const { return zones_; }

  Reply update(const Request& request) {
    if (request.zone == "system_status") return {Result::READ_ONLY_INDICATOR, 0};
    const int index = zoneIndex(request.zone);
    if (index < 0) return {Result::UNKNOWN_ZONE, 0};
    if (!validToken(request.requestId) || !validToken(request.deviceSession) ||
        request.brightness < 0 || request.brightness > 100 || request.expectedRevision > MAX_VERSION)
      return {Result::INVALID_REQUEST, 0};
    if (!ready_) return {Result::NOT_READY, 0};
    if (request.deviceSession != session_) return {Result::SESSION_CHANGED, 0};
    for (const auto& item : cache_) {
      if (item.id == request.requestId) {
        if (item.zone != index || item.power != request.power || item.brightness != request.brightness ||
            item.expectedRevision != request.expectedRevision) return {Result::REQUEST_ID_REUSE, 0};
        return {Result::ACCEPTED, item.revision}; // Idempotent; never reapply an earlier request.
      }
    }
    Zone& zone = zones_[index];
    if (request.expectedRevision != zone.revision) return {Result::REVISION_CONFLICT, 0};
    if (version_ == MAX_VERSION) return {Result::VERSION_EXHAUSTED, 0};
    // Even when power is OFF, retain request.brightness independently in software.
    if (!output_.write(static_cast<unsigned>(index), request.power, static_cast<uint8_t>(request.brightness))) {
      ready_ = false; // A partially failed bus transaction has unknown physical outcome.
      return {Result::DRIVER_FAILURE, 0};
    }
    zone.power = request.power;
    zone.brightness = static_cast<uint8_t>(request.brightness);
    zone.revision = ++version_;
    zone.lastRequestId = request.requestId;
    cache_[next_] = {request.requestId, index, request.power, request.brightness, request.expectedRevision, zone.revision};
    next_ = (next_ + 1) % RECENT_REQUESTS;
    return {Result::ACCEPTED, zone.revision};
  }
  // Invoke only after the actual automatic indicator driver applies a new state.
  // The controller never exposes any manual status-light write method.
  bool indicatorApplied() {
    if (!ready_ || version_ == MAX_VERSION) return false;
    ++version_;
    return true;
  }
 private:
  struct Cached {
    std::string id;
    int zone = -1;
    bool power = false;
    int brightness = 0;
    uint64_t expectedRevision = 0, revision = 0;
  };
  static bool validToken(const std::string& value) {
    if (value.empty() || value.size() > 96) return false;
    for (const char c : value) {
      if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') ||
            c == '-' || c == '_' || c == '.')) return false;
    }
    return true;
  }
  Output& output_;
  bool ready_ = false;
  std::string session_;
  uint64_t version_ = 0;
  std::array<Zone, ZONE_COUNT> zones_{};
  std::array<Cached, RECENT_REQUESTS> cache_{};
  unsigned next_ = 0;
};

// Proposed mapping only; this function does not drive or claim a physical RGB LED.
enum class Meaning { ERROR, CONNECTING, RAIN, NORMAL };
struct ProposedIndicator { Meaning meaning; uint8_t r, g, b; };
inline ProposedIndicator proposeIndicator(bool deviceError, bool wifiConnected, bool rainDetected) {
  if (deviceError) return {Meaning::ERROR, 255, 255, 0}; // Highest priority: yellow.
  if (!wifiConnected) return {Meaning::CONNECTING, 0, 0, 255};
  if (rainDetected) return {Meaning::RAIN, 255, 0, 0};
  return {Meaning::NORMAL, 0, 255, 0};
}
} // namespace house_lighting
