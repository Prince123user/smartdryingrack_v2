# Supplied ZIP design

The 17 original PNG references from `image.zip` are retained unchanged in `docs/design-reference/`.

The native Android app uses the supplied imagery directly. `ReferenceArtwork` renders selected artwork regions without baking sample readings, controls, phone status bars, or navigation into the UI. Text, actions, inputs, tabs, and settings remain native Compose components.

Applied screens: splash; introduction; monitoring and rain-protection onboarding; connection and validated connection success; home; manual controls; alerts; notifications; history; settings; device settings; notification settings; automation. References 16 and 17 establish that onboarding has no bottom navigation. Existing light theme and larger-text scrolling remain supported.

Sample temperature/humidity values appear only on the monitoring tutorial and are explicitly marked illustrative. Dashboard telemetry, rack position, connection, firmware, history, and events remain real. Unsupported sound/buzzer controls are shown disabled. Rain safety remains enabled; its sensitivity slider uses the existing persisted automation setting. Physical Wi-Fi network naming, sound notifications, and ESP32 hardware verification are outside this visual change.

Validate from the repository root:

```bash
source /workspace/.cloud-tools/activate.sh
cloud_gradle :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The UI tests exercise onboarding and primary navigation and export rendered screenshots to `app/build/outputs/ui-screenshots/`. The APK is `app/build/outputs/apk/debug/app-debug.apk`.
