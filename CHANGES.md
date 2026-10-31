# QDLink redesign: list of changes

This lists every change we made, so it can be made again on a future QDLink version.
QDLink's own code files (`classes.dex`, `classes2.dex`) are never edited. Our new screens are
separate code (`mod-src/`), compiled by `scripts/build.sh` into `classes3.dex` and added to the app.

## Base version
- QDLink 1.9.7 (version code 107), package `com.neusoft.qdrivelink`, from the APKPure `.xapk`.

## 1. Build fix: images that only exist in the `config.xhdpi` split
When apktool unpacks only the main APK, 38 small AppCompat images are missing, because they live in
the `config.xhdpi` split. That caused two problems:
- apktool replaced 10 style links that pointed at 6 of them with `@null`. That would have broken
  switch tracks, list dividers and popup backgrounds.
- New resources could be given the same ID numbers those 38 images use, which would show the wrong picture.

The fix:
1. Copied all 38 images from the decoded `config.xhdpi` split into `decoded/res/drawable-xhdpi/`.
2. Pinned all 38 to their original IDs in `decoded/res/values/public.xml`.
3. In `decoded/res/values/styles.xml`, changed the 10 `@null` values back to the right images.
   They are in `Base.V7.Theme.AppCompat` and its `.Light` version (dividerHorizontal, dividerVertical,
   panelBackground), `Base.V7.Widget.AppCompat.AutoCompleteTextView` (popupBackground),
   `Base.Widget.AppCompat.ActivityChooserView` (background),
   `Base.Widget.AppCompat.CompoundButton.Switch` (track) and `Base.Widget.AppCompat.SearchView` (commitIcon).

**Checked:** all 2948 original IDs and all split IDs mean the same thing after rebuilding, and no new
resource uses an ID a split uses.

## 2. New code: `mod-src/com/qdmod/`
| File | What it does |
|---|---|
| `HomeView.java` | The new liquid-glass Home: clock, weather, now playing, and 6 app shortcuts |
| `ClockScreen.java` | The full-screen clock (tap the clock card on Home; tap anywhere to close) |
| `GlassCard.java` | The glass card look: see-through fill, top shine, bright edge |
| `WallpaperDrawable.java` | The soft blue, purple and teal background, drawn with gradients (no image, low memory) |
| `NowPlaying.java` | Reads the playing song from any music app through Android's media sessions |
| `MediaListenerService.java` | Empty notification listener; Android requires one before sharing "now playing" |
| `WeatherLoader.java`, `WeatherInfo.java` | Weather from open-meteo.com (free, no key); sends only your position, rounded to about 1 km |
| `QdLink.java` | Calls 2 existing QDLink functions: the saved app list and the Navigation page's open-app function |
| `GlyphView.java`, `ProgressLine.java`, `Ui.java` | Drawn icons, the song progress bar, shared colors and sizes |

**QDLink names used by `QdLink.java` (check these after any QDLink update):**
- `com.neusoft.qdrivelink.otherapp.b.c()`: returns the apps picked in QDLink's Apps screen.
- `com.neusoft.qdrivelink.mapnavi.MapNaviView.f(String)`: opens an app the same way QDLink's own
  buttons do (phone-lock check, wake screen, car handover). App shortcuts always go through it.

## 3. Changed design files in `decoded/`
- `AndroidManifest.xml`: added the `com.qdmod.MediaListenerService` notification-listener service.
- `res/layout/layout_navi.xml`: added `<com.qdmod.HomeView android:id="@+id/qdmod_home">` on top of
  the Navigation page. QDLink's own map buttons stay underneath, untouched.
- **Dock** (`layout_main_page.xml`, `layout_main_page_right.xml`, `layout_main_page_right_single.xml`):
  - New pictures: `@drawable/qdmod_dock_home`, `qdmod_dock_music`, `qdmod_dock_phone` and `qdmod_dock_apps`.
  - See-through glass background `@color/qdmod_dock_glass`.
  - Added a live `TextClock` (`@+id/qdmod_dock_clock`).
  - QDLink's 2x2 arrangement is kept, because QDLink sets the dock's width itself for each car model.
- `res/drawable/selector_btn_setting.xml`: new gear icons (`qdmod_gear_normal`, `_pressed`, `_selected`).
- `res/values/dimens.xml`: dock spacing 10→14dp and 5→7dp; single-column dock 70→88dp with 56dp icons.
- `res/values/colors.xml`: background `#05060a`; new `qdmod_dock_glass`; song list `litletransparent_color`
  is now see-through `#b30e0e10`; divider lines `#1fffffff`.
- **Music:**
  - Selectors `qd_selector_music_play_land`, `_stop_land`, `_pre_land` and `_next_land` point to new
    `qdmod_music_*` vector icons. The names stay the same, so QDLink's code keeps switching them.
  - `music_play_meun.xml` (both `layout-ldltr` and `layout-ldrtl`): a 64dp play button and 48dp
    previous/next, all centered vertically.
  - `qd_frag_music_playdetail.xml`: title 26sp, artist 18sp, and 220dp album art with rounded corners
    (`qdmod_cover_shape`).
  - `seekbar_progress_drawable.xml`, `seekbar_rounded.xml`: thin white progress bar with a small round handle.
  - `qd_music_song_item.xml`: 64dp rows with 17sp names.
- `res/anim*/right_in.xml`, `right_out.xml` (playlist panel): eased 260/220 ms slide with a fade.

## 4. Round 2 fixes (after the first car test)
- **Old map buttons showed through the glass Home:** `layout_navi.xml` now sets
  `android:visibility="invisible"` on the three `CustomNavigationAppView`s. QDLink's code only changes
  their transparency, never their visibility, so they stay hidden. They still exist for QDLink's code.
- **Clock screen appeared on the phone, not the car:** when connected, QDLink moves its main layout
  into a `Presentation` window on the car's virtual display (`com.neu.ssp.mirror.screencap.managers.d`)
  and sends car touches straight to that layout. `ClockScreen.show(view, ...)` now adds the clock inside
  that layout (the last parent before `android.R.id.content`), so it shows and takes taps on the car.
- **Bigger clocks:** the Home clock card fills its card (auto-size 32–120sp on Android 8+). The dock
  clock is now 36sp light with a date line (`@+id/qdmod_dock_date`) in all three dock layouts.
- **Weather card:** icon and temperature on top, then description · city and high/low on full-width lines.
- **Shortcuts:** unused rows and cells are hidden, so a few apps sit centered; icons are 60dp.
- **Lower CPU and memory:**
  - The wallpaper is painted once into a quarter-size picture and stretched, instead of 3 full-screen
    gradients every frame.
  - Text is only set when it changed.
  - The progress timer only runs while music plays.
  - Big album covers are shrunk to the 96dp box once per new cover.
  - The shortcut list is only rebuilt when it changes.
  - The wallpaper is created only once.
  - Card and clock fades use hardware layers (`withLayer()`).
- `build.sh` names each bundle with its build time (`QDLink-mod-YYYYMMDD-HHMM.apks`).

## 5. Round 3 fix
- **Full-screen clock showed only the date, not the time (worse with a replaced system font):**
  the huge time overflowed above the top edge. `ClockScreen.java` now puts the time in a fixed-height
  box (42% of the screen) and auto-shrinks it to fit (Android 8+), with a safe fixed size on older
  phones. The column fills the screen and centres, so the time can never be pushed off-screen,
  whatever font is installed.

## 6. Round 4: clock sync, music title, Phone and Apps screens
- **Clock not synced with the real clock:** every `TextClock` was replaced by our own `LiveClock`,
  `LiveDate` and `LiveDateShort` (`mod-src/com/qdmod/LiveText.java` and subclasses). They read the
  system time and time zone again on every update, update at the start of each minute, and also
  listen for `TIME_SET`, `TIMEZONE_CHANGED`, `DATE_CHANGED` and `TIME_TICK`. They follow the phone's
  12/24-hour setting and date order. Replaced in `HomeView`, `ClockScreen` and all three dock layouts.
  (The root cause on the car was not reproduced; if it is still off, note by how much and in which
  clock: Home card, full-screen, or dock.)
- **Music title showed one word:** my round 1 edit made the title a fixed 340dp next to a 220dp album box,
  which squeezed it. In `qd_frag_music_playdetail.xml` the text column is now `0dp` + weight and the title
  and artist are `match_parent`; the album box is a fixed 180dp with weight 0; the title is 24sp.
- **Phone screen** (colors and sizes only, no layout or code edits): `item_header` is now transparent so
  the wallpaper shows; `item_divider` is white 12%; `btn_blue_n`/`btn_blue_p` are `#0a84ff`/`#0764c2`;
  `text_color_item_sub_title` is white 70%; `shape_corners_radius_btn` is 14dp (was 6dp).
  The keypad pictures are unchanged (white digits).
- **Apps screen:** the tiles got the press-shrink effect via the new `res/animator/qdmod_press.xml`
  and `android:stateListAnimator` on `cl_app_adapter` in `item_app_list_adapter.xml`.
  The page was already transparent, so the wallpaper already showed there.

## 7. Round 5: builds that really rebuild
- **apktool reused a cached build** (`AndroidManifest.xml and resources have not changed`), so a changed
  `versionName` never reached the app and the installer kept showing `1.9.7 (107)`. `build.sh` now deletes
  `decoded/build` and runs `apktool b -f` (force all), and prints `Built version name: ...`.
- `versionName` is now `1.9.7-mod6` (in `decoded/apktool.yml`). **Do not change `versionCode`** (107): the
  19 config splits must have the same code as the base. Bump only the name for each new build.
- **Bug I introduced:** my TextClock replacement turned the dock date line into a second `LiveClock`
  (it mentions `qdmod_dock_clock` in its constraints). It is now `LiveDateShort` in all three dock layouts.

## 8. Round 6 (version name 1.9.7-mod7), after mod6 was confirmed running on the phone
- **Full-screen clock had no time and cut-off text:** `ClockScreen` used to size its text and time box in
  `onSizeChanged`, a second layout pass that does not reliably happen inside QDLink's layout on the car.
  The sizes are now worked out from the real screen size before the clock is added (time = smaller of
  36% of height and 20% of width), text is single-line with `…` if too long, `clipChildren` is off, and
  nothing depends on a later resize.
- **Home clock card date cut off at the right:** `LiveDate` there auto-sizes 10–17sp (Android 8+) and ellipsizes.
- **App tiles:** `item_app_list_adapter.xml` root now has `@drawable/qdmod_tile_glass` (rounded glass card, 4dp gap).
- **Music list:** `shape_corner_blue_musiclistitem` / `shape_corner_press_musiclistitem` are now glass inset
  shapes (white 8% / 24%, 14dp corners); `qd_music_song_item.xml` outer layer is transparent;
  `background_gray` (#383838 -> #1fffffff) makes the music headers, search, help and QQ sync cards glass.

## 9. Round 7 (1.9.7-mod8): one standalone `.apk` instead of `.apks`
The 19 "config" splits only held three things, now merged into the main app:
- `config.arm64_v8a`: `lib/arm64-v8a/libsspLib.so` -> `decoded/lib/arm64-v8a/` (byte-identical). `so` was added to
  `doNotCompress` in `apktool.yml`, because the manifest has `extractNativeLibs="false"` and Android reads the
  library straight from the file, so it must be stored uncompressed. This makes the APK **arm64-only**, as before.
- `config.xhdpi`: the density images were already merged in section 1.
- 17 language splits: their 28 `values-*` folders (strings; Turkish also 2 dimens) were copied into
  `decoded/res/`. First checked: 2210 IDs match, no placeholder names, no folder is overwritten.
- `AndroidManifest.xml`: removed `requiredSplitTypes`, `splitTypes` and the two
  `com.android.vending.splits*` meta-data lines (a single-file install fails if they remain).
  `res/xml/splits0.xml` is left in place so resource IDs do not shift.
- `build.sh`: no split signing and no `.apks` bundle. It now checks the library is inside and uncompressed, and
  that the manifest asks for no splits, then signs one file named `QDLink-mod-YYYYMMDD-HHMM.apk`.
- **Checked on a test build:** 2948 original IDs and the 246 split-defined IDs all present and unchanged;
  `classes.dex` / `classes2.dex` identical; `native-code: arm64-v8a`; all 28 locales present.

## Not done yet
- Phone and Apps screens (same look, next round).
- Inter font: the plan named it, but the system fonts (`sans-serif-light`, `-medium`, `-thin`) are used
  instead, so nothing extra has to be downloaded.
