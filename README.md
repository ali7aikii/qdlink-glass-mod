# QDLink glass redesign (personal mod)

A personal, unofficial redesign of **QDLink 1.9.7** (`com.neusoft.qdrivelink`), the app that shows an
Android phone on a Chery car screen. It adds a dark "liquid glass" look, a new Home screen
(clock, weather, now playing, app shortcuts) and a full-screen clock.

This repository holds **only my own code and notes**. It does not contain QDLink itself, its
resources, or any built APK, because QDLink belongs to its developer. I use this mod only on my own phone.

## What is here

| Path | What it is |
|---|---|
| `mod-src/com/qdmod/` | The new Java screens: Home, full-screen clock, live clock, weather, now playing |
| `scripts/build.sh` | Rebuilds QDLink with the new screens and signs it as one `.apk` |
| `CHANGES.md` | Every change made to QDLink's design files, round by round, so it can be repeated on another version |

## What is NOT here (and why it matters)

- `original/` and `decoded/`: QDLink's own app and its unpacked resources. The design edits (layouts, colors,
  icons) live in `decoded/`, so **this repository alone cannot rebuild the mod**. `CHANGES.md` lists each edit.
- Your signing key (`qdlink-mod.keystore`). `build.sh` creates one the first time you run it.
  Keep it and its password safe; a new key means uninstalling the app once.

## Building (on a Mac)

1. Get QDLink 1.9.7 yourself and unzip it into `original/` (the base APK plus the `config.*.apk` parts).
2. Install the tools: Java (`openjdk`), `apktool`, and the Android build tools 35 and Android 15 platform
   (see the paths at the top of `scripts/build.sh`).
3. Run `bash scripts/build.sh` and type a password for your signing key when asked.
4. The finished file is `out/QDLink-mod-<date>-<time>.apk`.

## Notes

- Built for 64-bit ARM phones only (arm64-v8a).
- Weather comes from open-meteo.com (free, no account); only your position, rounded to about 1 km, is sent.
- "Now playing" needs Notification access for QDLink, switched on by you in Android settings.
