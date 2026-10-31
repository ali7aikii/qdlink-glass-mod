package com.qdmod;

// GlyphView.java - draws simple icons with lines and shapes (no image files needed).
// Used for the weather icon and the music buttons. Set the icon with setGlyph(...).

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

public class GlyphView extends View {

    // ---- The icons this view can draw ----
    public static final int SUN = 1;
    public static final int MOON = 2;
    public static final int CLOUD = 3;
    public static final int PARTLY_CLOUDY = 4;
    public static final int RAIN = 5;
    public static final int SNOW = 6;
    public static final int FOG = 7;
    public static final int STORM = 8;
    public static final int PLAY = 20;
    public static final int PAUSE = 21;
    public static final int PREVIOUS = 22;
    public static final int NEXT = 23;
    public static final int MUSIC_NOTE = 24;

    // Line thickness as a part of the icon size
    private static final float LINE_PART = 0.07f;

    private int glyph;
    private int color;
    private final Paint linePaint;
    private final Paint fillPaint;
    private final Path path;
    private final RectF oval;

    public GlyphView(Context context, int startGlyph, int startColor) {
        super(context);
        glyph = startGlyph;
        color = startColor;
        path = new Path();
        oval = new RectF();

        // Paint for outlines (round line ends look softer)
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        // Paint for filled shapes (play triangle, pause bars)
        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);
    }

    // Change which icon is shown
    public void setGlyph(int newGlyph) {
        if (glyph != newGlyph) {
            glyph = newGlyph;
            invalidate();
        }
    }

    // Change the icon color
    public void setGlyphColor(int newColor) {
        color = newColor;
        invalidate();
    }

    // Draw the chosen icon inside a square in the middle of the view
    @Override
    protected void onDraw(Canvas canvas) {
        float size = Math.min(getWidth(), getHeight());
        float left = (getWidth() - size) / 2f;
        float top = (getHeight() - size) / 2f;

        linePaint.setColor(color);
        linePaint.setStrokeWidth(size * LINE_PART);
        fillPaint.setColor(color);

        // Move the drawing so (0,0) is the top-left of the square
        canvas.save();
        canvas.translate(left, top);

        if (glyph == SUN) {
            drawSun(canvas, size, size * 0.5f, size * 0.5f, size * 0.2f);
        } else if (glyph == MOON) {
            drawMoon(canvas, size);
        } else if (glyph == CLOUD) {
            drawCloud(canvas, size, 0f);
        } else if (glyph == PARTLY_CLOUDY) {
            drawSun(canvas, size, size * 0.36f, size * 0.34f, size * 0.15f);
            drawCloud(canvas, size, size * 0.1f);
        } else if (glyph == RAIN) {
            drawCloud(canvas, size, -size * 0.1f);
            drawRainLines(canvas, size);
        } else if (glyph == SNOW) {
            drawCloud(canvas, size, -size * 0.1f);
            drawSnowDots(canvas, size);
        } else if (glyph == FOG) {
            drawFogLines(canvas, size);
        } else if (glyph == STORM) {
            drawCloud(canvas, size, -size * 0.1f);
            drawLightning(canvas, size);
        } else if (glyph == PLAY) {
            drawPlay(canvas, size);
        } else if (glyph == PAUSE) {
            drawPause(canvas, size);
        } else if (glyph == PREVIOUS) {
            drawSkip(canvas, size, false);
        } else if (glyph == NEXT) {
            drawSkip(canvas, size, true);
        } else if (glyph == MUSIC_NOTE) {
            drawMusicNote(canvas, size);
        }

        canvas.restore();
    }

    // A circle with 8 short rays around it
    private void drawSun(Canvas canvas, float size, float centerX, float centerY, float radius) {
        canvas.drawCircle(centerX, centerY, radius, linePaint);
        float rayStart = radius * 1.5f;
        float rayEnd = radius * 2.1f;
        for (int rayNumber = 0; rayNumber < 8; rayNumber++) {
            double angle = Math.toRadians(rayNumber * 45);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            canvas.drawLine(centerX + cos * rayStart, centerY + sin * rayStart,
                    centerX + cos * rayEnd, centerY + sin * rayEnd, linePaint);
        }
    }

    // A crescent moon: a big arc with a smaller arc cut out
    private void drawMoon(Canvas canvas, float size) {
        path.reset();
        oval.set(size * 0.2f, size * 0.2f, size * 0.8f, size * 0.8f);
        path.addArc(oval, 60, 240);
        oval.set(size * 0.38f, size * 0.16f, size * 0.86f, size * 0.64f);
        path.arcTo(oval, 200, -200);
        canvas.drawPath(path, linePaint);
    }

    // A cloud made of two bumps and a flat bottom; shiftY moves it up or down
    private void drawCloud(Canvas canvas, float size, float shiftY) {
        path.reset();
        float bottom = size * 0.72f + shiftY;
        path.moveTo(size * 0.26f, bottom);
        oval.set(size * 0.14f, size * 0.46f + shiftY, size * 0.40f, size * 0.72f + shiftY);
        path.arcTo(oval, 90, 180);
        oval.set(size * 0.30f, size * 0.30f + shiftY, size * 0.66f, size * 0.66f + shiftY);
        path.arcTo(oval, 200, 150);
        oval.set(size * 0.56f, size * 0.44f + shiftY, size * 0.86f, size * 0.72f + shiftY);
        path.arcTo(oval, 270, 180);
        path.close();
        canvas.drawPath(path, linePaint);
    }

    // Three slanted rain lines under the cloud
    private void drawRainLines(Canvas canvas, float size) {
        for (int lineNumber = 0; lineNumber < 3; lineNumber++) {
            float x = size * (0.34f + lineNumber * 0.16f);
            canvas.drawLine(x, size * 0.72f, x - size * 0.05f, size * 0.88f, linePaint);
        }
    }

    // Three small snow dots under the cloud
    private void drawSnowDots(Canvas canvas, float size) {
        for (int dotNumber = 0; dotNumber < 3; dotNumber++) {
            float x = size * (0.34f + dotNumber * 0.16f);
            canvas.drawCircle(x, size * 0.82f, size * 0.035f, fillPaint);
        }
    }

    // Three horizontal fog lines
    private void drawFogLines(Canvas canvas, float size) {
        canvas.drawLine(size * 0.18f, size * 0.38f, size * 0.82f, size * 0.38f, linePaint);
        canvas.drawLine(size * 0.26f, size * 0.52f, size * 0.74f, size * 0.52f, linePaint);
        canvas.drawLine(size * 0.18f, size * 0.66f, size * 0.82f, size * 0.66f, linePaint);
    }

    // A small lightning bolt under the cloud
    private void drawLightning(Canvas canvas, float size) {
        path.reset();
        path.moveTo(size * 0.52f, size * 0.64f);
        path.lineTo(size * 0.42f, size * 0.80f);
        path.lineTo(size * 0.54f, size * 0.80f);
        path.lineTo(size * 0.46f, size * 0.95f);
        canvas.drawPath(path, linePaint);
    }

    // A filled triangle pointing right
    private void drawPlay(Canvas canvas, float size) {
        path.reset();
        path.moveTo(size * 0.32f, size * 0.2f);
        path.lineTo(size * 0.82f, size * 0.5f);
        path.lineTo(size * 0.32f, size * 0.8f);
        path.close();
        canvas.drawPath(path, fillPaint);
    }

    // Two filled rounded bars
    private void drawPause(Canvas canvas, float size) {
        float corner = size * 0.04f;
        oval.set(size * 0.26f, size * 0.2f, size * 0.42f, size * 0.8f);
        canvas.drawRoundRect(oval, corner, corner, fillPaint);
        oval.set(size * 0.58f, size * 0.2f, size * 0.74f, size * 0.8f);
        canvas.drawRoundRect(oval, corner, corner, fillPaint);
    }

    // A triangle plus a bar: "next" points right, "previous" points left
    private void drawSkip(Canvas canvas, float size, boolean pointsRight) {
        path.reset();
        if (pointsRight) {
            path.moveTo(size * 0.2f, size * 0.22f);
            path.lineTo(size * 0.66f, size * 0.5f);
            path.lineTo(size * 0.2f, size * 0.78f);
            path.close();
            oval.set(size * 0.68f, size * 0.22f, size * 0.78f, size * 0.78f);
        } else {
            path.moveTo(size * 0.8f, size * 0.22f);
            path.lineTo(size * 0.34f, size * 0.5f);
            path.lineTo(size * 0.8f, size * 0.78f);
            path.close();
            oval.set(size * 0.22f, size * 0.22f, size * 0.32f, size * 0.78f);
        }
        canvas.drawPath(path, fillPaint);
        canvas.drawRect(oval, fillPaint);
    }

    // A music note: two circles joined by stems and a top bar
    private void drawMusicNote(Canvas canvas, float size) {
        canvas.drawCircle(size * 0.3f, size * 0.72f, size * 0.1f, linePaint);
        canvas.drawCircle(size * 0.7f, size * 0.64f, size * 0.1f, linePaint);
        canvas.drawLine(size * 0.4f, size * 0.72f, size * 0.4f, size * 0.24f, linePaint);
        canvas.drawLine(size * 0.8f, size * 0.64f, size * 0.8f, size * 0.16f, linePaint);
        canvas.drawLine(size * 0.4f, size * 0.24f, size * 0.8f, size * 0.16f, linePaint);
    }
}
