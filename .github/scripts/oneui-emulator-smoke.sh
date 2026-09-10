#!/usr/bin/env bash
# Preliminary host regressions, not the final seven-module/provider acceptance suite.
set -euo pipefail
export ANDROID_USER_HOME="$RUNNER_TEMP/lawnchair-oneui-user"
export ANDROID_AVD_HOME="$ANDROID_USER_HOME/avd"
mkdir -p oneui-emulator-results "$ANDROID_AVD_HOME"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
trap 'timeout 15 adb logcat -d > oneui-emulator-results/logcat.txt 2>&1 || true; timeout 10 adb emu kill >/dev/null 2>&1 || true' EXIT
if [ ! -e /dev/kvm ]; then
    echo 'Remote runner has no KVM device' | tee oneui-emulator-results/environment-error.txt
    exit 1
fi
sudo chmod a+rw /dev/kvm
timeout 600 sdkmanager 'platform-tools' 'emulator' 'system-images;android-35;google_apis;x86_64'
timeout 60 avdmanager create avd --force --name lawnchair-oneui --path "$ANDROID_AVD_HOME/lawnchair-oneui.avd" --package 'system-images;android-35;google_apis;x86_64' --device pixel_2 <<< no
test -s "$ANDROID_AVD_HOME/lawnchair-oneui.ini"
emulator -list-avds | tee oneui-emulator-results/avds.txt
grep -Fxq lawnchair-oneui oneui-emulator-results/avds.txt
emulator -avd lawnchair-oneui -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect > oneui-emulator-results/emulator.txt 2>&1 &
timeout 300 adb wait-for-device
booted=false
for attempt in $(seq 1 150); do
    if [ "$(timeout 5 adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; then
        booted=true
        break
    fi
    sleep 2
done
if [ "$booted" != true ]; then
    echo 'Emulator boot timed out' > oneui-emulator-results/environment-error.txt
    exit 1
fi
timeout 15 adb shell input keyevent 82
mapfile -t app_apks < <(find oneui-apks -path '*/lawnWithQuickstepGithub/debug/*.apk' ! -path '*/androidTest/*')
mapfile -t test_apks < <(find oneui-apks -path '*/androidTest/lawnWithQuickstepGithub/debug/*.apk')
[ "${#app_apks[@]}" -eq 1 ]
[ "${#test_apks[@]}" -eq 1 ]
timeout 120 adb install -r "${app_apks[0]}"
timeout 120 adb install -r -t "${test_apks[0]}"
timeout 30 adb shell cmd package set-home-activity app.lawnchair.debug/app.lawnchair.LawnchairLauncher > oneui-emulator-results/home-role.txt 2>&1 || true
timeout 15 adb logcat -c
python3 .github/scripts/oneui-screenshots.py > oneui-emulator-results/screenshots.txt 2>&1 &
screenshot_pid=$!
# Allow the ADB log stream to connect before instrumentation publishes capture requests.
sleep 1
timeout 600 adb shell am instrument -w -r -e oneuiScreenshots true -e notClass com.android.launcher3.WidgetStackProcessTest app.lawnchair.debug.test/androidx.test.runner.AndroidJUnitRunner | tee oneui-emulator-results/instrumentation.txt
kill "$screenshot_pid" 2>/dev/null || true
timeout 180 adb shell am instrument -w -r -e class com.android.launcher3.WidgetStackProcessTest -e oneuiPhase seed app.lawnchair.debug.test/androidx.test.runner.AndroidJUnitRunner | tee oneui-emulator-results/process-seed.txt
grep -Eq 'OK \([1-9][0-9]* tests?\)' oneui-emulator-results/process-seed.txt
timeout 15 adb shell am force-stop app.lawnchair.debug
timeout 180 adb shell am instrument -w -r -e class com.android.launcher3.WidgetStackProcessTest -e oneuiPhase verify app.lawnchair.debug.test/androidx.test.runner.AndroidJUnitRunner | tee oneui-emulator-results/process-verify.txt
grep -Eq 'OK \([1-9][0-9]* tests?\)' oneui-emulator-results/process-verify.txt

grep -Eq 'OK \([1-9][0-9]* tests?\)' oneui-emulator-results/instrumentation.txt
! grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' oneui-emulator-results/instrumentation.txt
