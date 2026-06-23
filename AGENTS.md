# YaDisk Sync — Agent Quick Reference

## Docker Android Emulator

### Start (requires `xhost +` before)
```bash
xhost +

docker stop android-emulator 2>/dev/null && docker rm android-emulator 2>/dev/null

docker run -d \
  --name android-emulator \
  --device /dev/kvm \
  -v /tmp/.X11-unix:/tmp/.X11-unix \
  -e DISPLAY=:0 \
  -e EMULATOR_DEVICE="Nexus 5" \
  -e EMULATOR_SDK_API_LEVEL=30 \
  -e EMULATOR_HEADLESS=true \
  --memory=4g \
  --cpus=2 \
  budtmo/docker-android
```

Wait **~90 seconds** for boot.

### Verify
```bash
docker exec android-emulator adb devices
# emulator-5554  device
```

### Install & Launch
```bash
./gradlew assembleDebug --no-daemon

docker cp app/build/outputs/apk/debug/app-debug.apk android-emulator:/tmp/
docker exec android-emulator adb install -r /tmp/app-debug.apk
docker exec android-emulator adb shell am start -n com.yadisksync/com.yadisksync.ui.MainActivity
```

### Common Commands
```bash
# Logs
docker exec android-emulator adb logcat | grep -i yadisk

# Screenshot
docker exec android-emulator adb exec-out screencap -p > /tmp/screenshot.png

# Stop
docker stop android-emulator && docker rm android-emulator
```

### Troubleshooting
| Problem | Fix |
|---------|-----|
| `adb devices` empty | Wait 90s after container start |
| QEMU `<defunct>` | Run `xhost +` before docker run |
| KVM not found | Check `/dev/kvm` exists, user in `kvm` group |
| APK not in container | `docker cp` APK into container first |
