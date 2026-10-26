#!/usr/bin/env bash
set -euo pipefail
repo_root=$(cd "$(dirname "$0")/../.." && pwd)
json_src=${ARDUINOJSON_SRC:-/workspace/.cloud-tools/arduino/user/libraries/ArduinoJson/src}
if [ ! -f "$json_src/ArduinoJson.hpp" ]; then
  echo 'Set ARDUINOJSON_SRC to the installed ArduinoJson 7.4.2 src directory.' >&2
  exit 1
fi
test_dir=$(mktemp -d /tmp/rack-host-tests.XXXXXX)
trap 'rm -rf "$test_dir"' EXIT
cd "$repo_root"
for polarity in 0 1; do
for test_name in firmware_test zone_matrix_test rain_pwm_test rgb_status_test buzzer_mode_test buzzer_failure_test contract_export_test; do
  g++ -std=c++17 -DHOUSE_LIGHT_ACTIVE_LOW="$polarity" -Ifirmware/tests/stubs -I"$json_src" "firmware/tests/$test_name.cpp" -o "$test_dir/$test_name"
  "$test_dir/$test_name"
done

done
