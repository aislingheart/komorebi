# Komorebi project instructions

## Delivery

After every **release** build, install the APK on the Pixel 9 Pro over Tailscale:

```bash
scripts/send-to-pixel.sh
```

- Default target: `100.95.41.47:5555` (override with `KOMOREBI_PIXEL_TARGET`). If that port is closed, the script scans `100.95.41.47` for the phone's random wireless-debugging port and connects to it automatically.
- Default APK: `app/build/outputs/apk/release/app-release.apk`.
- `assembleRelease` is configured to finalize with the `deliverReleaseToPixel` task, which runs this script best-effort.
- If the script reports `not authorized`, unlock the phone and accept the USB debugging RSA prompt, then rerun.
- Do not send debug APKs to the release-signed install.
