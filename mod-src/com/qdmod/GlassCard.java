package com.qdmod;

// GlassCard.java - a rounded "liquid glass" card that other views sit inside.
// It draws: a see-through white fill, a soft shine at the top, and a thin bright edge.
// Real-time blur is not possible for normal Android views, so the glass look comes from
// these layers sitting on top of the soft, blurry-looking wallpaper (WallpaperDrawable).

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.widget.FrameLayout;

public class GlassCard extends FrameLayout {

    // Corner roundness of every card, in dp
    private static final float CORNER_RADIUS_DP = 26f;

    // Width of the bright edge line, in dp
    private static final float EDGE_WIDTH_DP = 1f;

    // See-through white fill (white at about 10%)
    private static final int FILL_COLOR = 0x1AFFFFFF;

    // The shine starts at white 18% at the top and fades out
    private static final int SHINE_TOP_COLOR = 0x2EFFFFFF;
    private static final int SHINE_END_COLOR = 0x00FFFFFF;

    // The edge is brighter at the top (white 36%) than at the bottom (white 8%)
    private static final int EDGE_TOP_COLOR = 0x5CFFFFFF;
    private static final int EDGE_BOTTOM_COLOR = 0x14FFFFFF;

    // How far down (as part of the height) the shine reaches
    private static final float SHINE_HEIGHT_PART = 0.45f;

    private final Paint fillPaint;
    private final Paint shinePaint;
    private final Paint edgePaint;
    private final RectF cardShape;
    private final float cornerRadius;
    private final float edgeWidth;

    public GlassCard(Context context) {
        super(context);

        // A FrameLayout normally skips drawing itself; we need onDraw to run
        setWillNotDraw(false);

        cornerRadius = Ui.dp(context, CORNER_RADIUS_DP);
        edgeWidth = Ui.dp(context, EDGE_WIDTH_DP);
        cardShape = new RectF();

        // Paint for the see-through fill
        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setColor(FILL_COLOR);

        // Paint for the shine (its gradient is set once we know the card size)
        shinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Paint for the thin bright edge
        edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(edgeWidth);

        // Keep the content away from the rounded corners
        int padding = Ui.dp(context, 20);
        setPadding(padding, padding, padding, padding);
    }

    // Called when the card gets its size: rebuild the shapes and gradients
    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);

        // Move the shape in by half the edge width so the edge line is not cut off
        float half = edgeWidth / 2f;
        cardShape.set(half, half, width - half, height - half);

        // The shine fades from the top down to SHINE_HEIGHT_PART of the height
        float shineBottom = height * SHINE_HEIGHT_PART;
        LinearGradient shine = new LinearGradient(0, 0, 0, shineBottom,
                SHINE_TOP_COLOR, SHINE_END_COLOR, Shader.TileMode.CLAMP);
        shinePaint.setShader(shine);

        // The edge goes from bright at the top to faint at the bottom
        LinearGradient edge = new LinearGradient(0, 0, 0, height,
                EDGE_TOP_COLOR, EDGE_BOTTOM_COLOR, Shader.TileMode.CLAMP);
        edgePaint.setShader(edge);
    }

    // Draw the three glass layers, then let FrameLayout draw the children on top
    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRoundRect(cardShape, cornerRadius, cornerRadius, fillPaint);
        canvas.drawRoundRect(cardShape, cornerRadius, cornerRadius, shinePaint);
        canvas.drawRoundRect(cardShape, cornerRadius, cornerRadius, edgePaint);
        super.onDraw(canvas);
    }
}
