package com.qdmod;

// WallpaperDrawable.java - the soft, colorful background behind the glass cards.
// It is a near-black base with three big blurry color glows (blue, purple, teal).
// To save work, the glows are painted ONCE into a small picture (a quarter of the
// screen size) and that picture is stretched over the screen on every frame.
// The glows are so soft that the stretched picture looks the same, but drawing
// one picture is much cheaper than drawing three full-screen gradients each frame.

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

public class WallpaperDrawable extends Drawable {

    // The near-black base color
    private static final int BASE_COLOR = 0xFF05060A;

    // The small picture is this many times smaller than the screen (in each direction)
    private static final int SHRINK_FACTOR = 4;

    // The three glows: where each one sits (as part of the width and height),
    // how big it is (as part of the width), and its color with see-through alpha
    private static final float[] GLOW_X = { 0.18f, 0.62f, 0.92f };
    private static final float[] GLOW_Y = { 0.22f, 0.78f, 0.18f };
    private static final float[] GLOW_SIZE = { 0.42f, 0.45f, 0.36f };
    private static final int[] GLOW_COLOR = { 0x8C4060FF, 0x73B040FF, 0x6600C8BE };

    // The outer edge of each glow fades to fully see-through
    private static final int CLEAR = 0x00000000;

    // Paint that smooths the picture when it is stretched (and dithers to avoid color bands)
    private final Paint stretchPaint;

    // The small pre-painted picture (null until we know the size)
    private Bitmap smallPicture;

    public WallpaperDrawable() {
        stretchPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        smallPicture = null;
    }

    // Called when the wallpaper gets its size: paint the small picture once
    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);

        int smallWidth = bounds.width() / SHRINK_FACTOR;
        int smallHeight = bounds.height() / SHRINK_FACTOR;
        if (smallWidth < 1 || smallHeight < 1) {
            return;
        }

        // Same size as before: nothing to do
        if (smallPicture != null) {
            boolean sameWidth = smallPicture.getWidth() == smallWidth;
            boolean sameHeight = smallPicture.getHeight() == smallHeight;
            if (sameWidth && sameHeight) {
                return;
            }
            smallPicture.recycle();
        }

        smallPicture = Bitmap.createBitmap(smallWidth, smallHeight, Bitmap.Config.ARGB_8888);
        Canvas pictureCanvas = new Canvas(smallPicture);
        paintGlows(pictureCanvas, smallWidth, smallHeight);
    }

    // Paint the base color and the three glows into the small picture
    private void paintGlows(Canvas canvas, int width, int height) {
        canvas.drawColor(BASE_COLOR);
        Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setDither(true);

        for (int index = 0; index < GLOW_COLOR.length; index++) {
            float centerX = width * GLOW_X[index];
            float centerY = height * GLOW_Y[index];
            float radius = width * GLOW_SIZE[index];
            if (radius < 1f) {
                radius = 1f;
            }
            RadialGradient glow = new RadialGradient(centerX, centerY, radius,
                    GLOW_COLOR[index], CLEAR, Shader.TileMode.CLAMP);
            glowPaint.setShader(glow);
            canvas.drawRect(0, 0, width, height, glowPaint);
        }
    }

    // Stretch the small picture over the whole area (or just the base color if not ready)
    @Override
    public void draw(Canvas canvas) {
        if (smallPicture == null) {
            canvas.drawColor(BASE_COLOR);
            return;
        }
        canvas.drawBitmap(smallPicture, null, getBounds(), stretchPaint);
    }

    // The wallpaper always covers everything behind it
    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }

    // Not used, but every Drawable must have these two methods
    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }
}
