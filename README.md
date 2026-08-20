# Glyph Composite Toy

A Nothing Phone (3) Glyph Toy for the 25x25 Glyph Matrix.

It combines several states in one toy:

- large centered clock when the phone is not charging;
- animated music visualizer at the bottom;
- side volume indicator when volume changes;
- battery level line under the clock;
- large animated battery icon while charging;
- 3x3 notification pulse, then a quiet notification dot;
- separate brightness sliders for the main objects;
- English and Russian interface.

## Install

The ready-to-test APK is here:

[`release/glyph-composite-toy-v1.3-debug.apk`](release/glyph-composite-toy-v1.3-debug.apk)

Download the APK on the phone, install it, then enable the Glyph Toy in Nothing's Glyph settings. For notifications, also allow notification access inside the app.

## Glyph Museum / Nothing Playground

Use the public GitHub repository link or the release link when submitting the toy. The APK is included in the `release/` folder so reviewers can test it directly.

The official Nothing Glyph Matrix SDK AAR is not included in this repository because it comes from Nothing's developer kit.

## Build From Source

Only needed if you want to rebuild the APK yourself.

1. Put the official `glyph-matrix-sdk-2.0.aar` in `app/libs/`.
2. Open the project in Android Studio.
3. Run `File -> Sync Project with Gradle Files`.
4. Run `Build -> Generate App Bundles or APKs -> Generate APKs`.

The debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

## Device

This toy registers `Glyph.DEVICE_23112`, the Nothing Phone (3) 25x25 Glyph Matrix.
