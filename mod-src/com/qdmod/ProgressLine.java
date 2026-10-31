package com.qdmod;

// ProgressLine.java - a thin rounded bar that shows how far the song has played.

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

public class ProgressLine extends View {

    // The empty part of the bar (white at about 22%)
    private static final int TRACK_COLOR = 0x38FFFFFF;

    // The played part of the bar
    private static final int FILL_COLOR = 0xFFFFFFFF;

    private final Paint trackPaint;
    private final Paint fillPaint;
    private final RectF bar;

    // How much has played, from 0.0 (start) to 1.0 (end)
    private float progress;

    public ProgressLine(Context context) {
        super(context);
        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setColor(TRACK_COLOR);
        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setColor(FILL_COLOR);
        bar = new RectF();
        progress = 0f;
    }

    // Set how much has played (values outside 0..1 are pulled back into range)
    public void setProgress(float newProgress) {
        float safeProgress = newProgress;
        if (safeProgress < 0f) {
            safeProgress = 0f;
        }
        if (safeProgress > 1f) {
            safeProgress = 1f;
        }
        if (safeProgress != progress) {
            progress = safeProgress;
            invalidate();
        }
    }

    // Draw the whole track first, then the played part on top
    @Override
    protected void onDraw(Canvas canvas) {
        float height = getHeight();
        float corner = height / 2f;

        bar.set(0, 0, getWidth(), height);
        canvas.drawRoundRect(bar, corner, corner, trackPaint);

        float playedWidth = getWidth() * progress;
        if (playedWidth > 0f) {
            bar.set(0, 0, playedWidth, height);
            canvas.drawRoundRect(bar, corner, corner, fillPaint);
        }
    }
}
