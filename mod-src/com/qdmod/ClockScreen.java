package com.qdmod;

// ClockScreen.java - a full-screen clock for the car dashboard.
// It opens when you tap the clock card on the Home screen and covers everything,
// including the dock. Tap anywhere to close it.
// How to use:  ClockScreen.show(homeView, "24° · Clear · Riyadh");
//
// Why it is added to QDLink's main layout and not to the phone's window:
// when the car is connected, QDLink moves its whole main layout into a separate
// window on the car's screen, and sends car touches straight to that layout.
// So the clock must live inside that layout to be seen (and tapped) on the car.
//
// Why every size is worked out BEFORE the clock is added:
// inside QDLink's layout a second layout pass after the first one does not always
// happen, which left the time in an empty box and cut off the date and weather.
// So the sizes are fixed from the real screen size when the clock is created,
// and nothing depends on a later resize.

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ClockScreen extends FrameLayout implements View.OnClickListener {

    // How long the fade in and fade out take (milliseconds)
    private static final long FADE_MS = 250;

    // Text sizes: the time is the smaller of these two (part of height, part of width)
    private static final float TIME_HEIGHT_PART = 0.36f;
    private static final float TIME_WIDTH_PART = 0.20f;

    // Date and weather text sizes as a part of the screen height
    private static final float DATE_SIZE_PART = 0.055f;
    private static final float WEATHER_SIZE_PART = 0.04f;

    // Text never gets wider than this part of the screen (longer text ends with "...")
    private static final float MAX_TEXT_WIDTH_PART = 0.9f;

    // How far above everything else the clock sits (so nothing is drawn over it)
    private static final float RAISE_ABOVE_OTHERS = 1000f;

    // A soft blue glow rising from the bottom edge (blue at about 28%)
    private static final int GLOW_COLOR = 0x474060FF;
    private static final int CLEAR = 0x00000000;

    private final Paint glowPaint;


    // Open the clock screen on top of the screen that "fromView" is on (phone or car)
    public static void show(View fromView, String weatherLine) {
        ViewGroup screenRoot = findMainLayout(fromView);
        if (screenRoot == null) {
            return;
        }

        // Step 1: find out how big the screen is right now
        int width = screenRoot.getWidth();
        int height = screenRoot.getHeight();
        if (width <= 0 || height <= 0) {
            DisplayMetrics metrics = fromView.getResources().getDisplayMetrics();
            width = metrics.widthPixels;
            height = metrics.heightPixels;
        }

        // Step 2: build the clock with those exact sizes
        ClockScreen clockScreen = new ClockScreen(fromView.getContext(), weatherLine, width, height);
        clockScreen.setId(View.generateViewId());
        clockScreen.setTranslationZ(RAISE_ABOVE_OTHERS);

        // Step 3: add it on top, covering the whole screen
        ViewGroup.LayoutParams fullScreen = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        screenRoot.addView(clockScreen, fullScreen);

        // Step 4: start see-through, then fade in (withLayer makes the fade smoother)
        clockScreen.setAlpha(0f);
        clockScreen.animate().alpha(1f).setDuration(FADE_MS).withLayer().start();
    }

    // Walk up from a view to the top of QDLink's main layout: the last parent before
    // the window's "content" frame. On the car this is the layout QDLink moved there.
    private static ViewGroup findMainLayout(View fromView) {
        View current = fromView;
        ViewParent parent = fromView.getParent();
        while (parent instanceof View) {
            View parentView = (View) parent;
            if (parentView.getId() == android.R.id.content) {
                break;
            }
            current = parentView;
            parent = parentView.getParent();
        }
        if (current instanceof ViewGroup) {
            return (ViewGroup) current;
        }
        return null;
    }

    public ClockScreen(Context context, String weatherLine, int screenWidth, int screenHeight) {
        super(context);
        setBackgroundColor(0xFF000000);
        setWillNotDraw(false);

        // Never cut text at the edge of its box, whatever font is installed
        setClipChildren(false);
        setClipToPadding(false);

        // Keep the phone screen on while the clock is showing
        setKeepScreenOn(true);

        // Any tap closes the clock
        setClickable(true);
        setOnClickListener(this);

        // ---- Work out all sizes now ----
        float timeFromHeight = screenHeight * TIME_HEIGHT_PART;
        float timeFromWidth = screenWidth * TIME_WIDTH_PART;
        float timeSize = Math.min(timeFromHeight, timeFromWidth);
        float dateSize = screenHeight * DATE_SIZE_PART;
        float weatherSize = screenHeight * WEATHER_SIZE_PART;
        int maxTextWidth = Math.round(screenWidth * MAX_TEXT_WIDTH_PART);

        // ---- The big time ----
        LiveClock timeText = new LiveClock(context);
        timeText.setTextSize(TypedValue.COMPLEX_UNIT_PX, timeSize);
        timeText.setTextColor(Ui.TEXT_MAIN);
        timeText.setTypeface(Typeface.create(Ui.FONT_LIGHT, Typeface.NORMAL));
        timeText.setLetterSpacing(-0.02f);
        timeText.setSingleLine(true);
        timeText.setGravity(Gravity.CENTER);
        timeText.setMaxWidth(maxTextWidth);

        // ---- The date under it ----
        LiveDate dateText = new LiveDate(context);
        dateText.setTextSize(TypedValue.COMPLEX_UNIT_PX, dateSize);
        dateText.setTextColor(Ui.TEXT_SOFT);
        dateText.setTypeface(Typeface.create(Ui.FONT_NORMAL, Typeface.NORMAL));
        dateText.setSingleLine(true);
        dateText.setEllipsize(TextUtils.TruncateAt.END);
        dateText.setGravity(Gravity.CENTER);
        dateText.setMaxWidth(maxTextWidth);

        // ---- The weather line ----
        TextView weatherText = new TextView(context);
        weatherText.setTextSize(TypedValue.COMPLEX_UNIT_PX, weatherSize);
        weatherText.setTextColor(Ui.TEXT_FAINT);
        weatherText.setTypeface(Typeface.create(Ui.FONT_NORMAL, Typeface.NORMAL));
        weatherText.setSingleLine(true);
        weatherText.setEllipsize(TextUtils.TruncateAt.END);
        weatherText.setGravity(Gravity.CENTER);
        weatherText.setMaxWidth(maxTextWidth);
        weatherText.setText(weatherLine);
        if (weatherLine == null || weatherLine.length() == 0) {
            weatherText.setVisibility(View.GONE);
        }

        // ---- Stack the three lines and centre them on the screen ----
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        column.setClipChildren(false);
        column.setClipToPadding(false);

        column.addView(timeText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        column.addView(dateText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams weatherParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        weatherParams.topMargin = Math.round(screenHeight * 0.03f);
        column.addView(weatherText, weatherParams);

        addView(column, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));

        // ---- The soft glow behind the text (only needs the screen size, not a layout pass) ----
        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setDither(true);
        float glowCenterX = screenWidth / 2f;
        float glowCenterY = screenHeight * 1.2f;
        float glowRadius = screenHeight * 0.9f;
        RadialGradient glow = new RadialGradient(glowCenterX, glowCenterY, glowRadius,
                GLOW_COLOR, CLEAR, Shader.TileMode.CLAMP);
        glowPaint.setShader(glow);
    }

    // Draw the glow behind the text
    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), glowPaint);
        super.onDraw(canvas);
    }

    // A tap anywhere fades the clock out and removes it
    @Override
    public void onClick(View clickedView) {
        setClickable(false);
        animate().alpha(0f).setDuration(FADE_MS).withLayer().withEndAction(new RemoveFromScreen()).start();
    }

    // Takes the clock screen off the screen after the fade-out
    private class RemoveFromScreen implements Runnable {
        @Override
        public void run() {
            ViewParent parent = getParent();
            if (parent instanceof ViewGroup) {
                ViewGroup parentGroup = (ViewGroup) parent;
                parentGroup.removeView(ClockScreen.this);
            }
        }
    }
}
