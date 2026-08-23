# Glyph Composite Toy

A Nothing Phone (3) Glyph Toy that combines useful states on one 25×25 Glyph Matrix:

- centered clock, with optional large 7×3 digits when the phone is not charging;
- animated bottom music visualizer;
- charging battery icon with a separate full-charge breathing state;
- notification dot with a ten-second 3×3 breathing-circle animation, then a quiet dot until the notification is removed;
- separate component brightness controls plus an overall brightness control;
- English and Russian app interface;
- language selector with System Default enabled on first launch.

## Build

1. Put the official `glyph-matrix-sdk-2.0.aar` in `app/libs/`.
2. Open the project in Android Studio.
3. Run `File → Sync Project with Gradle Files`.
4. Run `Build → Generate App Bundles or APKs → Generate APKs`.

The debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

## Publish for Glyph Museum / Nothing Playground

1. Create a **public** GitHub repository named `glyph-composite-toy`.
2. Upload the project contents. Do not upload `local.properties`, `.gradle/`, `.gradle-user-home/`, `.idea/`, `build/`, `local-debug.keystore`, or the SDK AAR.
3. In Android Studio, build `app-debug.apk` or a signed release APK.
4. Open the repository's **Releases** page, create a release tag `v1.6`, and attach `release/glyph-composite-toy-v1.6.apk`.
5. Paste the public repository or release URL into the Glyph Toys submission form.

The repository and release must be accessible without signing in. The SDK AAR is intentionally excluded because it is supplied by Nothing's developer kit and may have redistribution restrictions.

## Included APK

The ready-to-test APK is included at [`release/glyph-composite-toy-v1.6.apk`](release/glyph-composite-toy-v1.6.apk). It is intended for personal installation and Glyph Toy testing. A separately signed release APK should be used for Play Store publication.

## Local checklist

- Confirm `app/libs/glyph-matrix-sdk-2.0.aar` exists locally before building.
- Confirm the app starts and the Glyph Toy service is enabled on the Phone (3).
- Confirm notification access is enabled if notification animation is required.
- Confirm the generated APK installs on the phone before publishing the release.
- Confirm the app language starts as **System Default** and can be changed to Russian or English.

## Device and SDK

The toy registers `Glyph.DEVICE_23112`, the Nothing Phone (3) 25×25 matrix. The SDK AAR is intentionally not committed to this repository; download it from Nothing's official developer kit and place it in `app/libs/` before building.
