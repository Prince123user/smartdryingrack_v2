#include "../SmartDryingRack/HouseLighting.h"
#include <cassert>
#include <iostream>
#include <vector>
using namespace house_lighting;
struct Driver : Output {
  struct Write { unsigned zone; bool power; uint8_t brightness; };
  std::vector<Write> writes;
  bool fail = false;
  bool write(unsigned zone, bool power, uint8_t brightness) override {
    writes.push_back({zone, power, brightness}); return !fail;
  }
};
int main() {
  Driver driver; Controller lights(driver);
  assert(!lights.ready()); assert(lights.begin("boot-1")); assert(driver.writes.size() == 6);
  for (const auto& zone : lights.zones()) { assert(!zone.power); assert(zone.brightness == 50); }
  for (unsigned i = 0; i < ZONE_COUNT; ++i) {
    Request request{zoneId(i), "boot-1", "zone-" + std::to_string(i), 0, true, static_cast<int>(i * 15)};
    auto reply = lights.update(request); assert(reply.accepted()); assert(reply.revision == i + 1);
    assert(lights.zones()[i].brightness == i * 15);
    assert(lights.zones()[i].lastRequestId == request.requestId);
    assert(driver.writes.back().zone == i);
  }
  const auto left = lights.zones()[1];
  Request off{"front", "boot-1", "front-off", lights.zones()[0].revision, false, 85};
  assert(lights.update(off).accepted()); assert(!lights.zones()[0].power); assert(lights.zones()[0].brightness == 85);
  const auto count = driver.writes.size();
  assert(lights.update(off).accepted()); assert(driver.writes.size() == count); // Duplicate does not drive output.
  auto conflict = off; conflict.power = true;
  assert(lights.update(conflict).result == Result::REQUEST_ID_REUSE);
  Request on{"front", "boot-1", "front-on", lights.zones()[0].revision, true, 85};
  assert(lights.update(on).accepted()); assert(lights.zones()[0].brightness == 85);
  assert(lights.zones()[1].revision == left.revision && lights.zones()[1].brightness == left.brightness);
  Request zero{"front", "boot-1", "zero", lights.zones()[0].revision, true, 0};
  assert(lights.update(zero).accepted()); assert(driver.writes.back().brightness == 0);
  Request bad = zero; bad.requestId = "bad"; bad.brightness = 101;
  assert(lights.update(bad).result == Result::INVALID_REQUEST);
  bad.brightness = -1; assert(lights.update(bad).result == Result::INVALID_REQUEST);
  bad.brightness = 50; bad.deviceSession = "old-boot";
  assert(lights.update(bad).result == Result::SESSION_CHANGED);
  bad.deviceSession = "boot-1"; assert(lights.update(bad).result == Result::REVISION_CONFLICT);
  bad.zone = "system_status"; assert(lights.update(bad).result == Result::READ_ONLY_INDICATOR);
  bad.zone = "unknown"; assert(lights.update(bad).result == Result::UNKNOWN_ZONE);
  const auto version = lights.version(); assert(lights.indicatorApplied()); assert(lights.version() == version + 1);
  // Eviction cannot replay a previously applied request: expectedRevision is stale.
  for (unsigned i = 0; i < RECENT_REQUESTS + 1; ++i) {
    Request next{"front", "boot-1", "next-" + std::to_string(i), lights.zones()[0].revision, true, 40};
    assert(lights.update(next).accepted());
  }
  assert(lights.update(off).result == Result::REVISION_CONFLICT);
  driver.fail = true;
  Request failed{"front", "boot-1", "driver-failed", lights.zones()[0].revision, true, 20};
  assert(lights.update(failed).result == Result::DRIVER_FAILURE); assert(!lights.ready());
  assert(lights.zones()[0].brightness == 40); // Failure is not published as success.
  driver.fail = false; assert(lights.begin("boot-2"));
  for (const auto& zone : lights.zones()) { assert(!zone.power); assert(zone.brightness == 50); assert(zone.revision == 0); }
  assert(lights.update(failed).result == Result::SESSION_CHANGED);
  assert(proposeIndicator(true, false, true).meaning == Meaning::ERROR);
  assert(proposeIndicator(false, false, true).meaning == Meaning::CONNECTING);
  assert(proposeIndicator(false, true, true).meaning == Meaning::RAIN);
  assert(proposeIndicator(false, true, false).meaning == Meaning::NORMAL);
  std::cout << "House-light model checks passed: isolation, brightness retention, reboot OFF, validation, deduplication, revisions, driver failure, proposed status priority.\n";
}
