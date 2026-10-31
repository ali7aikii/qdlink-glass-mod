package com.qdmod;

// HomeView.java - the new liquid-glass Home screen.
// It sits on top of QDLink's Navigation page (see layout_navi.xml) and shows four cards:
//   1. Clock (tap it to open the full-screen ClockScreen)
//   2. Weather (tap it to refresh, or to allow location the first time)
//   3. Now playing (song, cover, buttons; tap the song to open the music app)
//   4. App shortcuts (the apps you picked in QDLink's Apps screen)
// To keep the phone's CPU and memory use low, it only works while it is on screen,
// only changes text that really changed, and shrinks big album covers.

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class HomeView extends FrameLayout
        implements View.OnClickListener, NowPlaying.Listener, WeatherLoader.Listener {

    private static final String LOG_TAG = "QdMod";

    // How many app shortcuts fit on the card (3 across, 2 down)
    private static final int SHORTCUT_COLUMNS = 3;
    private static final int SHORTCUT_ROWS = 2;

    // Size of a shortcut icon and of the album cover (dp)
    private static final int SHORTCUT_ICON_DP = 60;
    private static final int COVER_DP = 96;

    // The big clock on the card grows or shrinks between these sizes to fill its space (sp)
    private static final int CLOCK_SMALLEST_SP = 32;
    private static final int CLOCK_LARGEST_SP = 120;

    // Load new weather after 30 minutes
    private static final long WEATHER_REFRESH_MS = 30L * 60L * 1000L;

    // Move the song progress bar once per second (only while music is playing)
    private static final long PROGRESS_TICK_MS = 1000L;

    // Cards slide up and fade in when Home appears
    private static final long ENTRANCE_MS = 280L;
    private static final long ENTRANCE_DELAY_STEP_MS = 50L;
    private static final float ENTRANCE_SLIDE_DP = 16f;

    // Our own number for the location permission request
    private static final int LOCATION_REQUEST_CODE = 7301;

    // Names of QDLink's views that we restyle (looked up by name, not changed in code)
    private static final String ROOT_VIEW_NAME = "cl_map_navi";
    private static final String DOCK_VIEW_NAME = "cl_menu";

    // ---- Cards ----
    private GlassCard clockCard;
    private GlassCard weatherCard;
    private GlassCard nowPlayingCard;
    private GlassCard shortcutsCard;

    // ---- Weather parts ----
    private GlyphView weatherIcon;
    private TextView temperatureText;
    private TextView weatherDescriptionText;
    private TextView highLowText;

    // ---- Now playing parts ----
    private LinearLayout nowPlayingContent;
    private TextView nowPlayingMessage;
    private LinearLayout songArea;
    private ImageView coverImage;
    private TextView songTitleText;
    private TextView songArtistText;
    private TextView playerNameText;
    private ProgressLine songProgress;
    private FrameLayout previousButton;
    private FrameLayout playPauseButton;
    private FrameLayout nextButton;
    private GlyphView playPauseIcon;

    // ---- Shortcut parts ----
    private LinearLayout[] shortcutRows;
    private LinearLayout[] shortcutCells;
    private ImageView[] shortcutIcons;
    private TextView[] shortcutLabels;
    private TextView shortcutsMessage;

    // ---- Helpers and state ----
    private NowPlaying nowPlaying;
    private Handler screenThread;
    private ProgressTicker progressTicker;
    private OneTimeLocation oneTimeLocation;
    private WeatherInfo lastWeather;
    private long lastWeatherTime;
    private boolean liveUpdatesRunning;
    private boolean waitingForLocation;
    private boolean tickerRunning;
    private List<String> shownShortcuts;
    private Bitmap lastCoverFromPlayer;


    // Android uses this one when it creates HomeView from a layout file
    public HomeView(Context context, AttributeSet attributes) {
        super(context, attributes);
        setUpHome();
    }

    public HomeView(Context context) {
        super(context);
        setUpHome();
    }

    // ---- Building the screen ----

    private void setUpHome() {
        Context context = getContext();
        screenThread = new Handler(Looper.getMainLooper());
        progressTicker = new ProgressTicker();
        oneTimeLocation = new OneTimeLocation();
        nowPlaying = new NowPlaying(context.getApplicationContext(), this);
        lastWeather = null;
        lastWeatherTime = 0;
        liveUpdatesRunning = false;
        waitingForLocation = false;
        tickerRunning = false;
        shownShortcuts = new ArrayList<String>();
        lastCoverFromPlayer = null;

        // Take all touches, so nothing under Home is pressed by mistake
        setClickable(true);

        int outerSpace = Ui.dp(context, 20);
        setPadding(outerSpace, outerSpace, outerSpace, outerSpace);

        // ---- Step 1: build the four cards ----
        clockCard = buildClockCard();
        weatherCard = buildWeatherCard();
        nowPlayingCard = buildNowPlayingCard();
        shortcutsCard = buildShortcutsCard();

        // ---- Step 2: the left column holds the clock (bigger) above the weather ----
        LinearLayout leftColumn = new LinearLayout(context);
        leftColumn.setOrientation(LinearLayout.VERTICAL);
        leftColumn.addView(clockCard, makeWeightParams(true, 0, 1.15f));
        leftColumn.addView(weatherCard, makeWeightParams(true, 16, 1f));

        // ---- Step 3: three columns side by side ----
        LinearLayout columns = new LinearLayout(context);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.addView(leftColumn, makeWeightParams(false, 0, 1f));
        columns.addView(nowPlayingCard, makeWeightParams(false, 16, 1f));
        columns.addView(shortcutsCard, makeWeightParams(false, 16, 1f));

        addView(columns, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    // Layout settings that share space by "weight"; "spaceBeforeDp" adds a gap before the view
    private LinearLayout.LayoutParams makeWeightParams(boolean vertical, int spaceBeforeDp, float weight) {
        LinearLayout.LayoutParams params;
        if (vertical) {
            params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, weight);
            params.topMargin = Ui.dp(getContext(), spaceBeforeDp);
        } else {
            params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
            params.leftMargin = Ui.dp(getContext(), spaceBeforeDp);
        }
        return params;
    }

    // Card 1: a big time that fills the card, and the date under it
    private GlassCard buildClockCard() {
        Context context = getContext();
        GlassCard card = new GlassCard(context);

        LiveClock time = new LiveClock(context);
        time.setTextColor(Ui.TEXT_MAIN);
        time.setTypeface(Typeface.create(Ui.FONT_LIGHT, Typeface.NORMAL));
        time.setIncludeFontPadding(false);
        time.setLetterSpacing(-0.02f);
        time.setMaxLines(1);
        time.setGravity(Gravity.START | Gravity.BOTTOM);

        // Android 8 and newer can grow the text to fill the space; older ones use a fixed size
        if (Build.VERSION.SDK_INT >= 26) {
            time.setAutoSizeTextTypeUniformWithConfiguration(
                    CLOCK_SMALLEST_SP, CLOCK_LARGEST_SP, 2, TypedValue.COMPLEX_UNIT_SP);
        } else {
            time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 64);
        }

        LiveDate date = new LiveDate(context);
        date.setTextColor(Ui.TEXT_SOFT);
        date.setTypeface(Typeface.create(Ui.FONT_MEDIUM, Typeface.NORMAL));
        date.setSingleLine(true);
        date.setEllipsize(TextUtils.TruncateAt.END);
        // Android 8 and newer shrink the date to fit the card; older ones use a fixed size
        if (Build.VERSION.SDK_INT >= 26) {
            date.setAutoSizeTextTypeUniformWithConfiguration(10, 17, 1, TypedValue.COMPLEX_UNIT_SP);
        } else {
            date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        }

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(time, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dateParams.topMargin = Ui.dp(context, 4);
        column.addView(date, dateParams);

        card.addView(column, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        card.setOnClickListener(this);
        Ui.addPressEffect(card);
        return card;
    }

    // Card 2: icon and temperature on top, then the description + city, then high/low
    private GlassCard buildWeatherCard() {
        Context context = getContext();
        GlassCard card = new GlassCard(context);

        weatherIcon = new GlyphView(context, GlyphView.CLOUD, Ui.TEXT_MAIN);
        temperatureText = Ui.makeText(context, 42, Ui.TEXT_MAIN, Ui.FONT_LIGHT);
        weatherDescriptionText = Ui.makeText(context, 15, Ui.TEXT_SOFT, Ui.FONT_NORMAL);
        highLowText = Ui.makeText(context, 13, Ui.TEXT_FAINT, Ui.FONT_NORMAL);

        temperatureText.setText("--°");
        weatherDescriptionText.setText("Loading weather…");

        LinearLayout topRow = new LinearLayout(context);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        int iconSize = Ui.dp(context, 48);
        topRow.addView(weatherIcon, new LinearLayout.LayoutParams(iconSize, iconSize));
        LinearLayout.LayoutParams temperatureParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        temperatureParams.leftMargin = Ui.dp(context, 12);
        topRow.addView(temperatureText, temperatureParams);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_VERTICAL);
        column.addView(topRow);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = Ui.dp(context, 8);
        column.addView(weatherDescriptionText, descriptionParams);
        LinearLayout.LayoutParams highLowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        highLowParams.topMargin = Ui.dp(context, 4);
        column.addView(highLowText, highLowParams);

        card.addView(column, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        card.setOnClickListener(this);
        Ui.addPressEffect(card);
        return card;
    }

    // Card 3: what is playing, with previous / play-pause / next buttons
    private GlassCard buildNowPlayingCard() {
        Context context = getContext();
        GlassCard card = new GlassCard(context);

        // The small heading
        TextView heading = Ui.makeText(context, 12, Ui.TEXT_FAINT, Ui.FONT_MEDIUM);
        heading.setText("NOW PLAYING");
        heading.setLetterSpacing(0.08f);

        // The cover picture in a rounded box (a music note shows when there is no cover)
        FrameLayout coverBox = new FrameLayout(context);
        GradientDrawable coverShape = new GradientDrawable();
        coverShape.setColor(0x24FFFFFF);
        coverShape.setCornerRadius(Ui.dp(context, 18));
        coverBox.setBackground(coverShape);
        coverBox.setClipToOutline(true);
        GlyphView coverPlaceholder = new GlyphView(context, GlyphView.MUSIC_NOTE, 0x99FFFFFF);
        int placeholderSize = Ui.dp(context, 40);
        coverBox.addView(coverPlaceholder, new FrameLayout.LayoutParams(
                placeholderSize, placeholderSize, Gravity.CENTER));
        coverImage = new ImageView(context);
        coverImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        coverBox.addView(coverImage, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // Song title, artist and the music app's name
        songTitleText = Ui.makeText(context, 18, Ui.TEXT_MAIN, Ui.FONT_MEDIUM);
        songArtistText = Ui.makeText(context, 14, Ui.TEXT_SOFT, Ui.FONT_NORMAL);
        playerNameText = Ui.makeText(context, 12, Ui.TEXT_FAINT, Ui.FONT_NORMAL);
        LinearLayout songTexts = new LinearLayout(context);
        songTexts.setOrientation(LinearLayout.VERTICAL);
        songTexts.addView(songTitleText);
        songTexts.addView(songArtistText);
        songTexts.addView(playerNameText);

        // Cover and texts side by side; tapping them opens the music app
        songArea = new LinearLayout(context);
        songArea.setOrientation(LinearLayout.HORIZONTAL);
        songArea.setGravity(Gravity.CENTER_VERTICAL);
        int coverSize = Ui.dp(context, COVER_DP);
        songArea.addView(coverBox, new LinearLayout.LayoutParams(coverSize, coverSize));
        LinearLayout.LayoutParams songTextsParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        songTextsParams.leftMargin = Ui.dp(context, 14);
        songArea.addView(songTexts, songTextsParams);
        songArea.setOnClickListener(this);

        // The progress bar
        songProgress = new ProgressLine(context);

        // The three buttons
        previousButton = buildRoundButton(GlyphView.PREVIOUS, 52, 0x00000000, Ui.TEXT_MAIN);
        playPauseButton = buildRoundButton(GlyphView.PLAY, 68, 0xEBFFFFFF, 0xFF000000);
        nextButton = buildRoundButton(GlyphView.NEXT, 52, 0x00000000, Ui.TEXT_MAIN);
        playPauseIcon = (GlyphView) playPauseButton.getChildAt(0);

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        buttons.addView(previousButton);
        LinearLayout.LayoutParams middleButtonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        middleButtonParams.leftMargin = Ui.dp(context, 28);
        middleButtonParams.rightMargin = Ui.dp(context, 28);
        buttons.addView(playPauseButton, middleButtonParams);
        buttons.addView(nextButton);

        // Put it all in one column
        nowPlayingContent = new LinearLayout(context);
        nowPlayingContent.setOrientation(LinearLayout.VERTICAL);
        nowPlayingContent.addView(heading);
        LinearLayout.LayoutParams songAreaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        songAreaParams.topMargin = Ui.dp(context, 14);
        nowPlayingContent.addView(songArea, songAreaParams);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(context, 5));
        progressParams.topMargin = Ui.dp(context, 16);
        nowPlayingContent.addView(songProgress, progressParams);
        nowPlayingContent.addView(buttons, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // The message shown instead, when nothing plays or access is missing
        nowPlayingMessage = new TextView(context);
        nowPlayingMessage.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        nowPlayingMessage.setTextColor(Ui.TEXT_SOFT);
        nowPlayingMessage.setGravity(Gravity.CENTER);
        nowPlayingMessage.setLineSpacing(0, 1.2f);
        nowPlayingMessage.setOnClickListener(this);

        card.addView(nowPlayingContent, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        card.addView(nowPlayingMessage, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        return card;
    }

    // A round button holding one GlyphView icon
    private FrameLayout buildRoundButton(int glyph, int sizeDp, int fillColor, int iconColor) {
        Context context = getContext();
        int size = Ui.dp(context, sizeDp);

        FrameLayout button = new FrameLayout(context);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(fillColor);
        button.setBackground(circle);
        button.setLayoutParams(new LinearLayout.LayoutParams(size, size));

        GlyphView icon = new GlyphView(context, glyph, iconColor);
        int iconSize = size / 2;
        button.addView(icon, new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER));

        button.setOnClickListener(this);
        Ui.addPressEffect(button);
        return button;
    }

    // Card 4: up to 3 x 2 app shortcuts; rows and cells that are not needed are hidden,
    // so a few apps sit nicely in the middle instead of in a corner
    private GlassCard buildShortcutsCard() {
        Context context = getContext();
        GlassCard card = new GlassCard(context);

        int cellCount = SHORTCUT_COLUMNS * SHORTCUT_ROWS;
        shortcutRows = new LinearLayout[SHORTCUT_ROWS];
        shortcutCells = new LinearLayout[cellCount];
        shortcutIcons = new ImageView[cellCount];
        shortcutLabels = new TextView[cellCount];

        LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setGravity(Gravity.CENTER_VERTICAL);
        int iconSize = Ui.dp(context, SHORTCUT_ICON_DP);

        for (int rowNumber = 0; rowNumber < SHORTCUT_ROWS; rowNumber++) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);

            for (int columnNumber = 0; columnNumber < SHORTCUT_COLUMNS; columnNumber++) {
                int cellNumber = rowNumber * SHORTCUT_COLUMNS + columnNumber;

                ImageView icon = new ImageView(context);
                TextView label = Ui.makeText(context, 13, Ui.TEXT_SOFT, Ui.FONT_MEDIUM);
                label.setGravity(Gravity.CENTER);

                LinearLayout cell = new LinearLayout(context);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                cell.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));
                LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                labelParams.topMargin = Ui.dp(context, 8);
                cell.addView(label, labelParams);
                cell.setOnClickListener(this);
                Ui.addPressEffect(cell);

                row.addView(cell, new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
                shortcutCells[cellNumber] = cell;
                shortcutIcons[cellNumber] = icon;
                shortcutLabels[cellNumber] = label;
            }
            grid.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            shortcutRows[rowNumber] = row;
        }

        shortcutsMessage = new TextView(context);
        shortcutsMessage.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        shortcutsMessage.setTextColor(Ui.TEXT_SOFT);
        shortcutsMessage.setGravity(Gravity.CENTER);
        shortcutsMessage.setLineSpacing(0, 1.2f);
        shortcutsMessage.setText("No shortcuts yet.\nAdd apps in QDLink's Apps screen\n(the grid button in the dock).");
        shortcutsMessage.setVisibility(View.GONE);

        card.addView(grid, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        card.addView(shortcutsMessage, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        return card;
    }

    // ---- Showing and hiding ----

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        applyWallpaperAndGlassDock();
        updateLiveState();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        updateLiveState();
    }

    // Called when Home (or one of its parents) is shown or hidden
    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateLiveState();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateLiveState();
    }

    // Start live updates when Home is on screen, stop them when it is not (saves battery)
    private void updateLiveState() {
        boolean onScreen = false;
        if (isAttachedToWindow() && isShown() && getWindowVisibility() == View.VISIBLE) {
            onScreen = true;
        }

        if (onScreen && !liveUpdatesRunning) {
            liveUpdatesRunning = true;
            nowPlaying.start();
            refreshShortcuts();
            refreshWeatherIfOld();
            playEntranceAnimation();
        } else if (!onScreen && liveUpdatesRunning) {
            liveUpdatesRunning = false;
            nowPlaying.stop();
            stopProgressTicker();
        }
    }

    // Cards fade in and slide up one after another.
    // withLayer() draws each card once into a ready-made picture while it moves,
    // which makes the animation smoother and cheaper.
    private void playEntranceAnimation() {
        View[] cards = new View[] { clockCard, weatherCard, nowPlayingCard, shortcutsCard };
        float slide = Ui.dp(getContext(), ENTRANCE_SLIDE_DP);
        for (int index = 0; index < cards.length; index++) {
            View card = cards[index];
            card.setAlpha(0f);
            card.setTranslationY(slide);
            card.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(index * ENTRANCE_DELAY_STEP_MS)
                    .setDuration(ENTRANCE_MS)
                    .setInterpolator(new DecelerateInterpolator())
                    .withLayer()
                    .start();
        }
    }

    // Paint the whole QDLink screen with our wallpaper and make the dock see-through glass
    private void applyWallpaperAndGlassDock() {
        Context context = getContext();
        String packageName = context.getPackageName();
        int rootId = getResources().getIdentifier(ROOT_VIEW_NAME, "id", packageName);
        int dockId = getResources().getIdentifier(DOCK_VIEW_NAME, "id", packageName);

        View rootView = findParentById(rootId);
        if (rootView == null) {
            // Not inside QDLink's main page: just paint our own background
            setBackground(new WallpaperDrawable());
            return;
        }

        // Only set the wallpaper once (making a new one each time would waste memory)
        Drawable currentBackground = rootView.getBackground();
        if (!(currentBackground instanceof WallpaperDrawable)) {
            rootView.setBackground(new WallpaperDrawable());
        }

        View dockView = rootView.findViewById(dockId);
        if (dockView != null) {
            dockView.setBackgroundColor(Ui.DOCK_GLASS);
        }
    }

    // Walk up the parents to find the view with this id (null if none)
    private View findParentById(int viewId) {
        if (viewId == 0) {
            return null;
        }
        ViewParent parent = getParent();
        while (parent instanceof View) {
            View parentView = (View) parent;
            if (parentView.getId() == viewId) {
                return parentView;
            }
            parent = parent.getParent();
        }
        return null;
    }

    // ---- Taps ----

    @Override
    public void onClick(View clickedView) {
        if (clickedView == clockCard) {
            openClockScreen();
        } else if (clickedView == weatherCard) {
            onWeatherCardTapped();
        } else if (clickedView == playPauseButton) {
            nowPlaying.playOrPause();
        } else if (clickedView == previousButton) {
            nowPlaying.previousSong();
        } else if (clickedView == nextButton) {
            nowPlaying.nextSong();
        } else if (clickedView == songArea) {
            openApp(nowPlaying.getPlayerPackage());
        } else if (clickedView == nowPlayingMessage) {
            onNowPlayingMessageTapped();
        } else {
            onShortcutTapped(clickedView);
        }
    }

    // Open the full-screen clock on the same screen Home is on (the car when connected)
    private void openClockScreen() {
        String weatherLine = "";
        if (lastWeather != null) {
            weatherLine = lastWeather.oneLine();
        }
        ClockScreen.show(this, weatherLine);
    }

    // A shortcut cell keeps its app's package name in its "tag"
    private void onShortcutTapped(View clickedView) {
        Object tag = clickedView.getTag();
        if (tag instanceof String) {
            openApp((String) tag);
        }
    }

    // Open an app the same way QDLink's own buttons do
    private void openApp(String packageName) {
        if (packageName == null || packageName.length() == 0) {
            return;
        }
        boolean opened = QdLink.openApp(this, packageName);
        if (!opened) {
            Toast.makeText(getContext(), "Open this app from QDLink's Apps screen", Toast.LENGTH_SHORT).show();
        }
    }

    // ---- App shortcuts ----

    // Fill the grid with the apps picked in QDLink's Apps screen
    // (skipped when the list is the same as last time, to save work)
    private void refreshShortcuts() {
        PackageManager packageManager = getContext().getPackageManager();
        List<String> savedPackages = QdLink.loadSavedAppPackages();

        // Step 1: keep only installed apps, at most one per cell
        List<String> appsToShow = new ArrayList<String>();
        for (String packageName : savedPackages) {
            if (appsToShow.size() >= shortcutCells.length) {
                break;
            }
            if (isInstalled(packageManager, packageName)) {
                appsToShow.add(packageName);
            }
        }

        // Step 2: same apps as already shown? Then there is nothing to do
        if (appsToShow.equals(shownShortcuts) && appsToShow.size() > 0) {
            return;
        }
        shownShortcuts = appsToShow;

        // Step 3: fill the cells; hide the ones that are not needed
        for (int cellNumber = 0; cellNumber < shortcutCells.length; cellNumber++) {
            if (cellNumber < appsToShow.size()) {
                showShortcut(packageManager, cellNumber, appsToShow.get(cellNumber));
            } else {
                shortcutCells[cellNumber].setTag(null);
                shortcutIcons[cellNumber].setImageDrawable(null);
                shortcutCells[cellNumber].setVisibility(View.GONE);
            }
        }

        // Step 4: hide rows that have no apps, so the others sit in the middle
        for (int rowNumber = 0; rowNumber < shortcutRows.length; rowNumber++) {
            int firstCellOfRow = rowNumber * SHORTCUT_COLUMNS;
            if (firstCellOfRow < appsToShow.size()) {
                shortcutRows[rowNumber].setVisibility(View.VISIBLE);
            } else {
                shortcutRows[rowNumber].setVisibility(View.GONE);
            }
        }

        if (appsToShow.size() == 0) {
            shortcutsMessage.setVisibility(View.VISIBLE);
        } else {
            shortcutsMessage.setVisibility(View.GONE);
        }
    }

    private boolean isInstalled(PackageManager packageManager, String packageName) {
        Intent launchIntent = packageManager.getLaunchIntentForPackage(packageName);
        return launchIntent != null;
    }

    // Put one app's icon and name into a cell
    private void showShortcut(PackageManager packageManager, int cellNumber, String packageName) {
        try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            Drawable icon = packageManager.getApplicationIcon(appInfo);
            CharSequence name = packageManager.getApplicationLabel(appInfo);
            shortcutIcons[cellNumber].setImageDrawable(icon);
            shortcutLabels[cellNumber].setText(name);
            shortcutCells[cellNumber].setTag(packageName);
            shortcutCells[cellNumber].setVisibility(View.VISIBLE);
        } catch (PackageManager.NameNotFoundException error) {
            shortcutCells[cellNumber].setVisibility(View.GONE);
        }
    }

    // ---- Now playing ----

    @Override
    public void onNowPlayingChanged() {
        if (!nowPlaying.hasAccess()) {
            showNowPlayingMessage("Tap here to allow Notification access\nso Home can show what's playing", true);
            return;
        }

        if (!nowPlaying.hasPlayer()) {
            showNowPlayingMessage("Nothing playing\nStart music in any app", false);
            return;
        }

        if (nowPlayingMessage.getVisibility() != View.GONE) {
            nowPlayingMessage.setVisibility(View.GONE);
        }
        if (nowPlayingContent.getVisibility() != View.VISIBLE) {
            nowPlayingContent.setVisibility(View.VISIBLE);
        }

        String title = nowPlaying.getTitle();
        if (title.length() == 0) {
            title = "Unknown song";
        }
        Ui.setTextIfChanged(songTitleText, title);
        Ui.setTextIfChanged(songArtistText, nowPlaying.getArtist());
        Ui.setTextIfChanged(playerNameText, nowPlaying.getPlayerName());
        showCover(nowPlaying.getCover());

        if (nowPlaying.isPlaying()) {
            playPauseIcon.setGlyph(GlyphView.PAUSE);
            startProgressTicker();
        } else {
            playPauseIcon.setGlyph(GlyphView.PLAY);
            stopProgressTicker();
        }
        songProgress.setProgress(nowPlaying.getProgress());
    }

    private void showNowPlayingMessage(String message, boolean tappable) {
        if (nowPlayingContent.getVisibility() != View.GONE) {
            nowPlayingContent.setVisibility(View.GONE);
        }
        if (nowPlayingMessage.getVisibility() != View.VISIBLE) {
            nowPlayingMessage.setVisibility(View.VISIBLE);
        }
        Ui.setTextIfChanged(nowPlayingMessage, message);
        nowPlayingMessage.setClickable(tappable);
        stopProgressTicker();
    }

    // Show the album cover. Music apps often send very large pictures (1000 x 1000 or more);
    // we shrink them to the size of the box, once per new cover, to save memory.
    private void showCover(Bitmap cover) {
        if (cover == lastCoverFromPlayer) {
            return;
        }
        lastCoverFromPlayer = cover;

        if (cover == null) {
            coverImage.setImageDrawable(null);
            coverImage.setVisibility(View.GONE);
            return;
        }

        int boxSize = Ui.dp(getContext(), COVER_DP);
        Bitmap coverToShow = cover;
        if (cover.getWidth() > boxSize || cover.getHeight() > boxSize) {
            coverToShow = Bitmap.createScaledBitmap(cover, boxSize, boxSize, true);
        }
        coverImage.setImageBitmap(coverToShow);
        coverImage.setVisibility(View.VISIBLE);
    }

    // Open Android's Notification access settings (straight to QDLink on Android 11 and newer)
    private void onNowPlayingMessageTapped() {
        if (nowPlaying.hasAccess()) {
            return;
        }
        Context context = getContext();
        Intent settingsIntent;
        if (Build.VERSION.SDK_INT >= 30) {
            settingsIntent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS);
            ComponentName ourListener = new ComponentName(context, MediaListenerService.class);
            settingsIntent.putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ourListener.flattenToString());
        } else {
            settingsIntent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        }
        settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(settingsIntent);
        } catch (Exception error) {
            Toast.makeText(context, "Open Settings > Notification access > QDLink", Toast.LENGTH_LONG).show();
        }
    }

    // The progress bar only moves while music is playing and Home is on screen
    private void startProgressTicker() {
        if (tickerRunning || !liveUpdatesRunning) {
            return;
        }
        tickerRunning = true;
        screenThread.postDelayed(progressTicker, PROGRESS_TICK_MS);
    }

    private void stopProgressTicker() {
        tickerRunning = false;
        screenThread.removeCallbacks(progressTicker);
    }

    // Moves the progress bar once per second
    private class ProgressTicker implements Runnable {
        @Override
        public void run() {
            if (!tickerRunning) {
                return;
            }
            songProgress.setProgress(nowPlaying.getProgress());
            screenThread.postDelayed(this, PROGRESS_TICK_MS);
        }
    }

    // ---- Weather ----

    private void onWeatherCardTapped() {
        if (!hasLocationPermission()) {
            askForLocationPermission();
            return;
        }
        loadWeather();
    }

    // Load the weather if we never did, or if it is older than 30 minutes
    private void refreshWeatherIfOld() {
        long now = SystemClock.elapsedRealtime();
        boolean neverLoaded = lastWeatherTime == 0;
        boolean tooOld = now - lastWeatherTime > WEATHER_REFRESH_MS;
        if (neverLoaded || tooOld) {
            loadWeather();
        }
    }

    private void loadWeather() {
        if (!hasLocationPermission()) {
            showWeatherProblem("Tap to allow location for weather");
            return;
        }

        Location location = findLastLocation();
        if (location == null) {
            showWeatherProblem("Waiting for your location…");
            askForOneLocation();
            return;
        }
        startWeatherDownload(location);
    }

    private void startWeatherDownload(Location location) {
        lastWeatherTime = SystemClock.elapsedRealtime();
        Context appContext = getContext().getApplicationContext();
        WeatherLoader loader = new WeatherLoader(appContext, location, this);
        Thread backgroundThread = new Thread(loader, "QdModWeather");
        backgroundThread.start();
    }

    @Override
    public void onWeatherLoaded(WeatherInfo weather) {
        if (weather.hasProblem()) {
            showWeatherProblem(weather.problem);
            // Try again next time Home appears
            lastWeatherTime = 0;
            return;
        }
        lastWeather = weather;

        weatherIcon.setGlyph(weather.pickGlyph());
        weatherIcon.setGlyphColor(weather.pickGlyphColor());
        Ui.setTextIfChanged(temperatureText, weather.temperature + "°");

        String description = weather.describe();
        if (weather.city.length() > 0) {
            description = description + " · " + weather.city;
        }
        Ui.setTextIfChanged(weatherDescriptionText, description);
        Ui.setTextIfChanged(highLowText, "H " + weather.high + "°   L " + weather.low + "°");
    }

    private void showWeatherProblem(String message) {
        weatherIcon.setGlyph(GlyphView.CLOUD);
        weatherIcon.setGlyphColor(Ui.TEXT_FAINT);
        Ui.setTextIfChanged(temperatureText, "--°");
        Ui.setTextIfChanged(weatherDescriptionText, message);
        Ui.setTextIfChanged(highLowText, "");
    }

    private boolean hasLocationPermission() {
        // Before Android 6, permissions were given when the app was installed
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        Context context = getContext();
        int coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION);
        int fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION);
        if (coarse == PackageManager.PERMISSION_GRANTED || fine == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        return false;
    }

    // Show Android's "allow location?" question (tap the weather card again afterwards)
    private void askForLocationPermission() {
        Context context = getContext();
        if (Build.VERSION.SDK_INT < 23 || !(context instanceof Activity)) {
            return;
        }
        String[] permissions = new String[] {
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
        };
        Activity activity = (Activity) context;
        activity.requestPermissions(permissions, LOCATION_REQUEST_CODE);
        showWeatherProblem("Allow location, then tap here again");
    }

    // The newest position Android already knows (null if it knows none)
    private Location findLastLocation() {
        LocationManager locationManager = (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            return null;
        }
        String[] providers = new String[] {
                LocationManager.NETWORK_PROVIDER,
                LocationManager.GPS_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
        };
        Location newest = null;
        for (String provider : providers) {
            try {
                Location location = locationManager.getLastKnownLocation(provider);
                if (location != null) {
                    if (newest == null || location.getTime() > newest.getTime()) {
                        newest = location;
                    }
                }
            } catch (Exception error) {
                Log.i(LOG_TAG, "Location provider not available: " + provider);
            }
        }
        return newest;
    }

    // Ask Android for one fresh position; OneTimeLocation loads the weather when it arrives
    @SuppressWarnings("deprecation")
    private void askForOneLocation() {
        if (waitingForLocation) {
            return;
        }
        LocationManager locationManager = (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            return;
        }
        try {
            locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, oneTimeLocation, Looper.getMainLooper());
            waitingForLocation = true;
        } catch (Exception error) {
            Log.i(LOG_TAG, "Could not ask for a fresh location", error);
        }
    }

    // Receives the one fresh position asked for above
    private class OneTimeLocation implements LocationListener {
        @Override
        public void onLocationChanged(Location location) {
            waitingForLocation = false;
            startWeatherDownload(location);
        }

        // Needed on older Android versions; nothing to do here
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }
    }
}
