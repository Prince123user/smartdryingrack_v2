# Supplied ZIP design

The 17 original PNG references from `image.zip` are retained unchanged in `docs/design-reference/`.

The native Android app uses the supplied imagery directly. `ReferenceArtwork` renders selected artwork regions without baking sample readings, controls, phone status bars, or navigation into the UI. Text, actions, inputs, tabs, and settings remain native Compose components.

Applied screens: splash; introduction; monitoring and rain-protection onboarding; connection and validated connection success; home; manual controls; alerts; notifications; history; settings; device settings; notification settings; automation. References 16 and 17 establish that onboarding has no bottom navigation. Existing light theme and larger-text scrolling remain supported. The app now opens directly to Home; onboarding remains implemented as an optional flow. Settings keeps the primary tabs available, and its Back button returns to the screen that opened it.

Sample temperature/humidity values appear only on the monitoring tutorial and are explicitly marked illustrative. Dashboard telemetry, rack position, connection, firmware, history, and events remain real. System notification sound remains unavailable. The buzzer switch works with firmware 1.1.0+ and stays disabled on older firmware. Rain safety remains enabled; physical sensor calibration is displayed using device raw readings; forecast sensitivity remains in advanced settings. Physical Wi-Fi network naming, sound notifications, and ESP32 hardware verification are outside this visual change.

Validate from the repository root:

```bash
source /workspace/.cloud-tools/activate.sh
cloud_gradle :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The UI tests exercise optional onboarding, five repeated navigation loops, every ordered tab pair twice, Android Back, Settings/Notifications return paths, and primary navigation and export rendered screenshots to `app/build/outputs/ui-screenshots/`. The APK is `app/build/outputs/apk/debug/app-debug.apk`.
