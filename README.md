# SpotLED Editor for Android

A native Android editor for animations on SpotLED 48×12 LED boards, controlled over Bluetooth LE.

![Screenshot](docs/screenshot.png)

> **Disclaimer:** This is an independent, unofficial hobby project. The author is **not** affiliated with, endorsed by, or connected to the manufacturer of the SpotLED device. Product names are used only to describe compatibility. The software is provided as is, use at your own risk.

## Features

- Draw animations pixel by pixel with up to **20 frames**, with undo/redo and a preview of the previous frame.
- **Text tab** with built-in fonts and custom `.slf` fonts (for example, Amstrad CPC).
- **Gallery** of ready-made animations.
- **PNG import** and text placement with drag-and-drop positioning.
- Save and load projects as **JSON** files.
- **Send animations to the board** over BLE.
- **12 UI languages**: English, Polish, German, French, Spanish, Italian, Czech, Hungarian, Ukrainian, Russian, Chinese, Japanese.
- Dark and light themes.
- Landscape, full-screen interface.

## Requirements

- Android 5.0 (API 21) or newer (tested on Android 6 and 12). On Android 6–11 the app asks for the location permission, which the system requires for BLE scanning; on Android 12+ it asks for the Bluetooth permissions instead.
- A phone with Bluetooth LE.
- A SpotLED board.

> **Note:** Sending animations from the board view has been tested on hardware. The Text tab and custom fonts have **not yet been thoroughly tested** on the board.

## Build

Requirements: JDK 17 and the Android SDK.

1. Create `local.properties` in the project root:

   ```properties
   sdk.dir=/path/to/android-sdk
   ```

2. Create a debug keystore in the project root (the system `~/.android/debug.keystore` does not contain the `AndroidDebugKey` alias the project expects):

   ```bash
   keytool -genkeypair -keystore spotled-debug.keystore -storepass android -keypass android \
     -alias spotled -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=SpotLED Debug"
   ```

3. Build the debug APK:

   ```bash
   ./gradlew :app:assembleDebug
   ```

   The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

4. Run the unit tests:

   ```bash
   ./gradlew :app:testDebugUnitTest
   ```

`local.properties` and the keystore are local files and are ignored by Git.

## Repository layout

| Path | Contents |
|---|---|
| `app/` | Android application (Kotlin, Jetpack Compose). Source in `app/src/main/java/app/spotled/gui/`, fonts and translations in `app/src/main/assets/`, tests in `app/src/test/`. |
| `examples/` | Project JSON files. Projects with exactly 20 frames appear in the gallery. |
| `tools/` | Python 3 (standard library only) generators for example animations. |

## License

Not decided yet.
