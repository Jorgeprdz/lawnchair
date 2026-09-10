#!/usr/bin/env bash
# Preliminary host regressions, not the final seven-module/provider acceptance suite.
set -euo pipefail
mkdir -p oneui-emulator-results
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
trap 'adb logcat -d > oneui-emulator-results/logcat.txt 2>&1 || true; adb emu kill >/dev/null 2>&1 || true' EXIT
if [ ! -e /dev/kvm ]; then
    echo 'Remote runner has no KVM device' | tee oneui-emulator-results/environment-error.txt
    exit 1
fi
sudo chmod a+rw /dev/kvm
sdkmanager 'platform-tools' 'emulator' 'system-images;android-35;google_apis;x86_64'
avdmanager create avd --force --name lawnchair-oneui --package 'system-images;android-35;google_apis;x86_64' --device pixel_2 <<< no
emulator -avd lawnchair-oneui -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect > oneui-emulator-results/emulator.txt 2>&1 &
timeout 300 adb wait-for-device
booted=false
for attempt in $(seq 1 150); do
    if [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; then
        booted=true
        break
    fi
    sleep 2
done
if [ "$booted" != true ]; then
    echo 'Emulator boot timed out' > oneui-emulator-results/environment-error.txt
    exit 1
fi
adb shell input keyevent 82
mapfile -t app_apks < <(find oneui-apks -path '*/lawnWithQuickstepGithub/debug/*.apk' ! -path '*/androidTest/*')
mapfile -t test_apks < <(find oneui-apks -path '*/androidTest/lawnWithQuickstepGithub/debug/*.apk')
[ "${#app_apks[@]}" -eq 1 ]
[ "${#test_apks[@]}" -eq 1 ]
adb install -r "${app_apks[0]}"
adb install -r "${test_apks[0]}"
adb shell cmd package set-home-activity app.lawnchair.debug/app.lawnchair.LawnchairLauncher > oneui-emulator-results/home-role.txt 2>&1 || true
adb logcat -c
adb shell am instrument -w -r app.lawnchair.debug.test/androidx.test.runner.AndroidJUnitRunner | tee oneui-emulator-results/instrumentation.txt
grep -Eq 'OK \([1-9][0-9]* tests?\)' oneui-emulator-results/instrumentation.txt
! grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' oneui-emulator-results/instrumentation.txt
