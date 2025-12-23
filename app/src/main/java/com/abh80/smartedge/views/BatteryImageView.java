package com.abh80.smartedge.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.abh80.smartedge.R;

public class BatteryImageView extends View {
    // Paints
    private Paint arcPaint;
    private Paint backgroundPaint;
    private Paint glowPaint;
    private Paint textPaint;

    // Drawing variables
    private RectF arcRect;
    private RectF backgroundRect;

    // Customizable attributes
    private float batteryPercent = 0f;
    private int strokeColor = Color.GREEN;
    private int backgroundColor = Color.TRANSPARENT;
    private int textColor = Color.WHITE;
    private float strokeWidth = 8f;
    private float backgroundStrokeWidth = 2f;
    private boolean showBackground = true;
    private boolean showText = false;
    private boolean showGlow = true;
    private int animationDuration = 300; // ms
    private float animatedPercent = 0f;

    // Animation
    private android.animation.ValueAnimator percentAnimator;

    public BatteryImageView(Context context) {
        super(context);
        init(context, null);
    }

    public BatteryImageView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public BatteryImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        // Read custom attributes
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.BatteryImageView);
            strokeColor = a.getColor(R.styleable.BatteryImageView_strokeColor, Color.GREEN);
            backgroundColor = a.getColor(R.styleable.BatteryImageView_backgroundColor, Color.TRANSPARENT);
            textColor = a.getColor(R.styleable.BatteryImageView_textColor, Color.WHITE);
            strokeWidth = a.getDimension(R.styleable.BatteryImageView_strokeWidth, 8f);
            backgroundStrokeWidth = a.getDimension(R.styleable.BatteryImageView_backgroundStrokeWidth, 2f);
            showBackground = a.getBoolean(R.styleable.BatteryImageView_showBackground, true);
            showText = a.getBoolean(R.styleable.BatteryImageView_showText, false);
            showGlow = a.getBoolean(R.styleable.BatteryImageView_showGlow, true);
            animationDuration = a.getInteger(R.styleable.BatteryImageView_animationDuration, 300);
            a.recycle();
        }

        // Initialize paints
        arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        arcPaint.setStrokeWidth(strokeWidth);
        arcPaint.setColor(strokeColor);

        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setStyle(Paint.Style.STROKE);
        backgroundPaint.setStrokeWidth(backgroundStrokeWidth);
        backgroundPaint.setColor(adjustAlpha(backgroundColor, 0.5f));

        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setStrokeWidth(strokeWidth + 4);
        glowPaint.setColor(adjustAlpha(strokeColor, 0.3f));

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(textColor);
        textPaint.setTextAlign(Paint.Align.CENTER);

        // Initialize rects
        arcRect = new RectF();
        backgroundRect = new RectF();

        // Set layer type for hardware acceleration with software layer for glow
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateDrawingRects();
    }

    private void updateDrawingRects() {
        float padding = Math.max(strokeWidth, backgroundStrokeWidth) + 5;
        float left = padding;
        float top = padding;
        float right = getWidth() - padding;
        float bottom = getHeight() - padding;

        arcRect.set(left, top, right, bottom);
        backgroundRect.set(left, top, right, bottom);
    }

    public void updateBatteryPercent(float percent) {
        updateBatteryPercent(percent, true);
    }

    public void updateBatteryPercent(float percent, boolean animate) {
        // Clamp percentage between 0 and 100
        float newPercent = Math.max(0, Math.min(100, percent));

        if (animate) {
            animateToPercent(newPercent);
        } else {
            this.batteryPercent = newPercent;
            this.animatedPercent = newPercent;
            invalidate();
        }
    }

    private void animateToPercent(float targetPercent) {
        if (percentAnimator != null && percentAnimator.isRunning()) {
            percentAnimator.cancel();
        }

        percentAnimator = android.animation.ValueAnimator.ofFloat(animatedPercent, targetPercent);
        percentAnimator.setDuration(animationDuration);
        percentAnimator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());

        percentAnimator.addUpdateListener(animation -> {
            animatedPercent = (float) animation.getAnimatedValue();
            invalidate();
        });

        percentAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                batteryPercent = targetPercent;
            }
        });

        percentAnimator.start();
    }

    public void setStrokeColor(int color) {
        this.strokeColor = color;
        arcPaint.setColor(color);
        if (showGlow) {
            glowPaint.setColor(adjustAlpha(color, 0.3f));
        }
        invalidate();
    }

    public void setBackgroundColor(int color) {
        this.backgroundColor = color;
        backgroundPaint.setColor(adjustAlpha(color, 0.5f));
        invalidate();
    }

    public void setTextColor(int color) {
        this.textColor = color;
        textPaint.setColor(color);
        invalidate();
    }

    public void setStrokeWidth(float width) {
        this.strokeWidth = width;
        arcPaint.setStrokeWidth(width);
        glowPaint.setStrokeWidth(width + 4);
        updateDrawingRects();
        invalidate();
    }

    public void setShowBackground(boolean show) {
        this.showBackground = show;
        invalidate();
    }

    public void setShowText(boolean show) {
        this.showText = show;
        invalidate();
    }

    public void setShowGlow(boolean show) {
        this.showGlow = show;
        invalidate();
    }

    public float getBatteryPercent() {
        return batteryPercent;
    }

    public int getStrokeColor() {
        return strokeColor;
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (getWidth() == 0 || getHeight() == 0) return;

        // Draw background circle if enabled
        if (showBackground) {
            canvas.drawArc(backgroundRect, 0, 360, false, backgroundPaint);
        }

        // Draw glow effect if enabled
        if (showGlow && animatedPercent > 0) {
            canvas.drawArc(arcRect, 270f, (animatedPercent / 100f) * 360f, false, glowPaint);
        }

        // Draw main arc
        if (animatedPercent > 0) {
            canvas.drawArc(arcRect, 270f, (animatedPercent / 100f) * 360f, false, arcPaint);
        }

        // Draw percentage text if enabled
        if (showText) {
            updateTextSize();
            String text = String.format("%.0f%%", animatedPercent);
            float textY = getHeight() / 2f - (textPaint.descent() + textPaint.ascent()) / 2f;
            canvas.drawText(text, getWidth() / 2f, textY, textPaint);
        }
    }

    private void updateTextSize() {
        // Calculate optimal text size based on view dimensions
        float textSize = Math.min(getWidth(), getHeight()) * 0.3f;
        textPaint.setTextSize(textSize);
    }

    private int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return Color.argb(alpha, red, green, blue);
    }

    private int getColorWithBrightness(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= factor; // value component
        return Color.HSVToColor(hsv);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (percentAnimator != null && percentAnimator.isRunning()) {
            percentAnimator.cancel();
        }
    }
}