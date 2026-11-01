#!/usr/bin/env bash
set +e
./gradlew --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g connectedDebugAndroidTest
result=$?
mkdir -p qa-screens
timeout 20 adb pull /sdcard/Android/data/com.agendalavanda/files/qa/ qa-screens/ || true
timeout 15 adb logcat -d > emulator-log.txt || true
exit "$result"
