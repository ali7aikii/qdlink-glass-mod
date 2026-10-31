package com.qdmod;

// Ui.java - shared colors, sizes and small helpers used by all the new screens.
// Every other file calls these, so the whole look can be changed in one place.

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.StateListAnimator;
import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

public class Ui {

    // ---- Colors (written as 0xAARRGGBB: alpha, red, green, blue) ----

    // Main text: pure white
    public static final int TEXT_MAIN = 0xFFFFFFFF;

    // Softer text for dates and subtitles: white at about 78%
    public static final int TEXT_SOFT = 0xC7FFFFFF;

    // Small labels: white at about 70%
    public static final int TEXT_FAINT = 0xB3FFFFFF;

    // Accent blue (links, highlights)
    public static final int ACCENT_BLUE = 0xFF0A84FF;

    // Sun yellow for the weather icon
    public static final int SUN_YELLOW = 0xFFFFD60A;

    // Rain blue for the weather icon
    public static final int RAIN_BLUE = 0xFF64D2FF;

    // The dark, see-through dock color (black at about 55%)
    public static final int DOCK_GLASS = 0x8C0A0A0E;

    // ---- Font names that every Android phone has ----

    // Thin font for the big clock
    public static final String FONT_THIN = "sans-serif-thin";

    // Light font for large numbers
    public static final String FONT_LIGHT = "sans-serif-light";

    // Normal font for body text
    public static final String FONT_NORMAL = "sans-serif";

    // Medium (semi-bold) font for titles
    public static final String FONT_MEDIUM = "sans-serif-medium";

    // How long a pressed button takes to shrink or grow back (milliseconds)
    private static final long PRESS_ANIMATION_MS = 120;

    // How small a pressed button gets (1.0 = normal size)
    private static final float PRESSED_SCALE = 0.95f;


    // Turn a size in "dp" (screen-independent pixels) into real pixels for this screen
    public static int dp(Context context, float value) {
        float density = context.getResources().getDisplayMetrics().density;
        float pixels = value * density;
        int roundedPixels = Math.round(pixels);
        return roundedPixels;
    }

    // Create a one-line text label with the given size (in sp), color and font
    public static TextView makeText(Context context, float sizeSp, int color, String fontName) {
        TextView label = new TextView(context);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        label.setTextColor(color);
        label.setTypeface(Typeface.create(fontName, Typeface.NORMAL));
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setIncludeFontPadding(false);
        return label;
    }

    // Change a label's text only if it is different. Setting the same text again still
    // makes Android re-measure the screen, so skipping it saves CPU work.
    public static void setTextIfChanged(TextView label, CharSequence newText) {
        CharSequence currentText = label.getText();
        if (TextUtils.equals(currentText, newText)) {
            return;
        }
        label.setText(newText);
    }

    // Give a view a small "press" animation: it shrinks a little while touched
    public static void addPressEffect(View view) {
        // The animation used while the finger is down
        PropertyValuesHolder shrinkX = PropertyValuesHolder.ofFloat(View.SCALE_X, PRESSED_SCALE);
        PropertyValuesHolder shrinkY = PropertyValuesHolder.ofFloat(View.SCALE_Y, PRESSED_SCALE);
        ObjectAnimator pressedAnimation = ObjectAnimator.ofPropertyValuesHolder(view, shrinkX, shrinkY);
        pressedAnimation.setDuration(PRESS_ANIMATION_MS);

        // The animation used when the finger is lifted
        PropertyValuesHolder growX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f);
        PropertyValuesHolder growY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f);
        ObjectAnimator normalAnimation = ObjectAnimator.ofPropertyValuesHolder(view, growX, growY);
        normalAnimation.setDuration(PRESS_ANIMATION_MS);

        // Tell Android which animation belongs to which state
        StateListAnimator animator = new StateListAnimator();
        int[] pressedState = new int[] { android.R.attr.state_pressed };
        int[] anyOtherState = new int[0];
        animator.addState(pressedState, pressedAnimation);
        animator.addState(anyOtherState, normalAnimation);
        view.setStateListAnimator(animator);
    }
}
