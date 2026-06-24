## Launch Android Emulator (Docker)

### Prerequisites
- Docker image: `budtmo/docker-android:latest`
- KVM access: `/dev/kvm` exists, user in `kvm` group
- AVD in container: `nexus_5_11.0` (x86_64, API 30 = Android 11)

### Start Emulator (Docker)
**Important: run `xhost +` before starting the container — without it QEMU will crash.**

```bash
xhost +

docker stop android-emulator 2>/dev/null && docker rm android-emulator 2>/dev/null

docker run -d \
  --name android-emulator \
  --device /dev/kvm \
  -v /tmp/.X11-unix:/tmp/.X11-unix \
  -e DISPLAY=:0 \
  --memory=4g \
  --cpus=2 \
  -e EMULATOR_DEVICE="Nexus 5" \
  -e EMULATOR_SDK_API_LEVEL=30 \
  -e EMULATOR_HEADLESS=true \
  budtmo/docker-android
```

Wait **~90 seconds** for boot.

### Verify
```bash
docker exec android-emulator adb devices
# Expected: emulator-5554  device
```

### Build & Install APK
```bash
./gradlew assembleDebug --no-daemon

# APK must be copied INTO container first
docker cp app/build/outputs/apk/debug/app-debug.apk android-emulator:/tmp/
docker exec android-emulator adb install -r /tmp/app-debug.apk
```

### Launch App
```bash
docker exec android-emulator adb shell am start -n com.yadisksync/com.yadisksync.ui.MainActivity
```

### Check Logs
```bash
docker exec android-emulator adb logcat | grep -i yadisk
```

### Stop Emulator
```bash
docker stop android-emulator && docker rm android-emulator
```

### Troubleshooting
| Problem | Fix |
|---------|-----|
| `adb devices` shows empty list | Wait ~90s after container start |
| QEMU `<defunct>` in `ps aux` | Run `xhost +` **before** `docker run` |
| KVM not working | `kvm-ok`, ensure user in `kvm` group |
| APK "file not found" in container | `docker cp app/build/outputs/apk/debug/app-debug.apk android-emulator:/tmp/` |

### Important Notes
- `xhost +` is mandatory — QEMU emulator inside container connects to host X11 and will crash without it.
- `--device /dev/kvm` is mandatory for x86_64 AVD performance.
- APK files from host are **not** visible inside container — always use `docker cp` to copy them in.
- `EMULATOR_SDK_API_LEVEL=30` targets Android 11, suitable for testing scoped storage (API 29+).
