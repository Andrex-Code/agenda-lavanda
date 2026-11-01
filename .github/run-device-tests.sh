#!/usr/bin/env bash
set +e
adb shell mkdir -p /sdcard/Download
./gradlew --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g connectedDebugAndroidTest
result=$?
mkdir -p qa-screens
for image in last-screen calendar-landscape goals ink-page; do
  timeout 15 adb pull "/sdcard/Download/agenda-qa-$image.png" "qa-screens/$image.png" || true
done
timeout 15 adb logcat -d > emulator-log.txt || true
exit "$result"
