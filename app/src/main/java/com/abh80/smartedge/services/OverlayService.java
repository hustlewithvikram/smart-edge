package com.abh80.smartedge.services;

import android.accessibilityservice.AccessibilityService;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Toast;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import com.abh80.smartedge.R;
import com.abh80.smartedge.plugins.BasePlugin;
import com.abh80.smartedge.plugins.ExportedPlugins;
import com.abh80.smartedge.utils.CallBack;
import com.google.android.material.color.DynamicColors;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class OverlayService extends AccessibilityService {

    // ─── Constants ───────────────────────────────────────────────────────────────
    private static final String TAG                  = "OverlayService";
    private static final int    ANIM_DURATION_EXPAND = 420;
    private static final int    ANIM_DURATION_SHRINK = 340;
    private static final int    ANIM_DURATION_X      = 320;
    private static final int    MIN_SWIPE_DISTANCE   = 50;
    private static final float  OVERSHOOT_TENSION    = 0.8f;

    // ─── Plugin state ────────────────────────────────────────────────────────────
    private final ArrayList<BasePlugin> plugins = ExportedPlugins.getPlugins();
    private final ArrayList<String>     queued  = new ArrayList<>();
    private BasePlugin binded_plugin;

    // ─── Window state ────────────────────────────────────────────────────────────
    public  WindowManager  mWindowManager;
    public  DisplayMetrics metrics        = new DisplayMetrics();
    public  int            statusBarHeight = 0;
    public  int            minHeight;
    public  int            x, y;
    public  int            textColor;
    public  Bundle         sharedPreferences = new Bundle();

    private View    mView;
    private Context ctx;
    private int     color;
    private int     minWidth;
    private int     gap;
    private int     lastKnownWidth;

    // ─── Touch tracking ──────────────────────────────────────────────────────────
    public final Handler     mHandler   = new Handler(Looper.getMainLooper());
    private final AtomicLong pressStart = new AtomicLong();
    private float touchX1, touchX2, touchY1, touchY2;

    // ─── Active animator ─────────────────────────────────────────────────────────
    private AnimatorSet activeAnimSet;

    // ════════════════════════════════════════════════════════════════════════════
    // AccessibilityService
    // ════════════════════════════════════════════════════════════════════════════

    @Override public void onAccessibilityEvent(AccessibilityEvent e) { plugins.forEach(p -> p.onEvent(e)); }
    @Override public void onInterrupt() {}
    @Override public int  onStartCommand(Intent i, int f, int id) { return START_STICKY; }

    // ════════════════════════════════════════════════════════════════════════════
    // onServiceConnected
    // ════════════════════════════════════════════════════════════════════════════

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            throwable.printStackTrace();
            if (sharedPreferences.getBoolean("clip_copy_enabled", true)) {
                ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cb.setPrimaryClip(ClipData.newPlainText("smart edge error log",
                        throwable.getMessage() + " : " + Arrays.toString(throwable.getStackTrace())));
                Toast.makeText(this, "Smart Edge crashed – log copied to clipboard", Toast.LENGTH_SHORT).show();
            }
            Runtime.getRuntime().exit(0);
        });

        IntentFilter filter = new IntentFilter(getPackageName() + ".SETTINGS_CHANGED");
        filter.addAction(getPackageName() + ".OVERLAY_LAYOUT_CHANGE");
        filter.addAction(getPackageName() + ".COLOR_CHANGED");
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(broadcastReceiver, filter);

        SharedPreferences sp2 = getSharedPreferences(getPackageName(), MODE_PRIVATE);
        sp2.getAll().forEach((key, value) -> {
            if      (value instanceof Boolean) sharedPreferences.putBoolean(key, (boolean) value);
            else if (value instanceof Float)   sharedPreferences.putFloat(key,   (float)   value);
            else if (value instanceof String)  sharedPreferences.putString(key,  (String)  value);
            else if (value instanceof Integer) sharedPreferences.putInt(key,     (int)     value);
        });

        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mWindowManager.getDefaultDisplay().getMetrics(metrics);
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) statusBarHeight = getResources().getDimensionPixelSize(resId);

        init();
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Broadcast receiver
    // ════════════════════════════════════════════════════════════════════════════

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action == null) return;
            if      (action.equals(Intent.ACTION_USER_PRESENT))                  handleScreenOn();
            else if (action.equals(Intent.ACTION_SCREEN_OFF))                   handleScreenOff();
            else if (action.equals(getPackageName() + ".OVERLAY_LAYOUT_CHANGE")) onLayoutChange(intent);
            else if (action.equals(getPackageName() + ".COLOR_CHANGED"))         onColorChange(intent);
            else if (action.equals(getPackageName() + ".SETTINGS_CHANGED"))      onSettingsChange(intent);
        }
    };

    private void handleScreenOn() {
        if (!sharedPreferences.getBoolean("enable_on_lockscreen", false) && mView != null)
            mView.setVisibility(View.VISIBLE);
    }

    private void handleScreenOff() {
        if (!sharedPreferences.getBoolean("enable_on_lockscreen", false) && mView != null)
            mView.setVisibility(View.INVISIBLE);
    }

    private void onLayoutChange(Intent intent) {
        Bundle settings = intent.getBundleExtra("settings");
        if (settings == null || mView == null) return;
        for (String s : settings.keySet()) {
            if (settings.get(s) instanceof Float) sharedPreferences.putFloat(s, settings.getFloat(s));
        }
        WindowManager.LayoutParams p = (WindowManager.LayoutParams) mView.getLayoutParams();
        minWidth  = dpToInt((int) sharedPreferences.getFloat("overlay_w",   83));
        minHeight = dpToInt((int) sharedPreferences.getFloat("overlay_h",   40));
        gap       = dpToInt((int) sharedPreferences.getFloat("overlay_gap", 50));
        y = (int) (sharedPreferences.getFloat("overlay_y", 0.67f) * 0.01f * metrics.heightPixels);
        x = (int) (sharedPreferences.getFloat("overlay_x", 0f)   * 0.01f * metrics.widthPixels);
        p.y = y; p.x = x; p.height = minHeight;
        setGapWidth(gap);
        mWindowManager.updateViewLayout(mView, p);
    }

    private void onColorChange(Intent intent) {
        color     = intent.getIntExtra("color", color);
        textColor = isColorDark(color) ? getColor(R.color.white) : getColor(R.color.black);
        if (mView != null) mView.setBackgroundTintList(ColorStateList.valueOf(color));
        if (binded_plugin != null) binded_plugin.onTextColorChange();
    }

    private void onSettingsChange(Intent intent) {
        Bundle settings = intent.getBundleExtra("settings");
        if (settings == null) return;
        for (String s : settings.keySet()) {
            if (settings.get(s) instanceof Boolean) sharedPreferences.putBoolean(s, settings.getBoolean(s));
        }
        plugins.forEach(BasePlugin::onDestroy);
        queued.clear();
        if (mView != null) mWindowManager.removeViewImmediate(mView);
        init();
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Init
    // ════════════════════════════════════════════════════════════════════════════

    @SuppressLint("ClickableViewAccessibility")
    private void init() {
        binded_plugin = null;
        int flags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;

        if (minWidth  == 0) minWidth  = dpToInt(110);   // better collapsed width
        if (minHeight == 0) minHeight = dpToInt(42);    // better collapsed height
        if (gap       == 0) gap       = dpToInt((int) sharedPreferences.getFloat("overlay_gap", 50));

        color          = sharedPreferences.getInt("color", getColor(R.color.black));
        textColor      = isColorDark(color) ? getColor(R.color.white) : getColor(R.color.black);
        lastKnownWidth = minWidth;

        WindowManager.LayoutParams mParams = getParams(minWidth, minHeight, flags);
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        getBaseContext().setTheme(R.style.Theme_SmartEdge);
        mView = inflater.inflate(R.layout.overlay_layout, null);
        mView.setBackgroundTintList(ColorStateList.valueOf(color));

        ctx = DynamicColors.wrapContextIfAvailable(getBaseContext(),
                com.google.android.material.R.style.ThemeOverlay_Material3_DynamicColors_DayNight);

        mParams.gravity = Gravity.TOP | Gravity.CENTER;
        if (y == 0) y = (int) (sharedPreferences.getFloat("overlay_y", 0.67f) * 0.01f * metrics.heightPixels);
        if (x == 0) x = (int) (sharedPreferences.getFloat("overlay_x", 0f)   * 0.01f * metrics.widthPixels);
        mParams.y = y;
        mParams.x = x;

        try {
            if (mView.getWindowToken() == null && mView.getParent() == null)
                mWindowManager.addView(mView, mParams);
        } catch (Exception e) {
            Log.e(TAG, "addView failed: " + e);
        }

        mView.setOnTouchListener(this::onTouch);
        plugins.forEach(p -> { if (sharedPreferences.getBoolean(p.getID() + "_enabled", true)) p.onCreate(this); });
        binded_plugin = null;
        bindPlugin();
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Touch
    // ════════════════════════════════════════════════════════════════════════════

    private final Runnable longPressAction = this::expandOverlay;

    @SuppressLint("ClickableViewAccessibility")
    private boolean onTouch(View view, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mHandler.postDelayed(longPressAction, ViewConfiguration.getLongPressTimeout());
                pressStart.set(Instant.now().toEpochMilli());
                touchX1 = event.getX(); touchY1 = event.getY();
                break;
            case MotionEvent.ACTION_OUTSIDE:
                shrinkOverlay();
                break;
            case MotionEvent.ACTION_UP:
                mHandler.removeCallbacks(longPressAction);
                touchX2 = event.getX(); touchY2 = event.getY();
                handleTouchUp();
                break;
        }
        return false;
    }

    private void handleTouchUp() {
        float dx = touchX2 - touchX1;
        float dy = touchY2 - touchY1;

        if (Math.abs(dx) >= MIN_SWIPE_DISTANCE && binded_plugin != null) {
            if (dx < 0) binded_plugin.onLeftSwipe(); else binded_plugin.onRightSwipe();
            return;
        }
        if (-dy >= MIN_SWIPE_DISTANCE) { shrinkOverlay(); return; }

        boolean withinLongPress = pressStart.get() + ViewConfiguration.getLongPressTimeout()
                > Instant.now().toEpochMilli();
        if (Math.abs(dx) < MIN_SWIPE_DISTANCE && -dy < MIN_SWIPE_DISTANCE && withinLongPress) {
            if (binded_plugin != null) {
                if (sharedPreferences.getBoolean("invert_click", false)) binded_plugin.onExpand();
                else binded_plugin.onClick();
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Expand / Shrink
    // ════════════════════════════════════════════════════════════════════════════

    private void expandOverlay() {
        if (binded_plugin != null) {
            if (sharedPreferences.getBoolean("invert_click", false)) binded_plugin.onClick();
            else binded_plugin.onExpand();
        } else {
            animateOverlay(minHeight + dpToInt(20), minWidth + dpToInt(20), false,
                    new CallBack(), new CallBack() {
                        @Override public void onFinish() {
                            animateOverlay(minHeight, minWidth, false, new CallBack(), new CallBack(), false);
                        }
                    }, false);
        }
    }

    private void shrinkOverlay() { if (binded_plugin != null) binded_plugin.onCollapse(); }

    // ════════════════════════════════════════════════════════════════════════════
    // animateOverlay
    // ════════════════════════════════════════════════════════════════════════════

    public void animateOverlay(int h, int w, boolean expanding,
                               CallBack onStart, CallBack onEnd, boolean expandedPrev) {
        animateOverlay(h, w, expanding, onStart, onEnd, null, expandedPrev);
    }

    public void animateOverlay(int h, int w, boolean expanding,
                               CallBack onStart, CallBack onEnd,
                               CallBack onChange, boolean expandedPrev) {

        if (mView == null) return;
        if (activeAnimSet != null && activeAnimSet.isRunning())
            activeAnimSet.cancel();

        // Proper dynamic island widths
        int collapsedWidth = dpToInt(110);
        int expandedWidth  = (int) (metrics.widthPixels * 0.96f);

        WindowManager.LayoutParams params =
                (WindowManager.LayoutParams) mView.getLayoutParams();

        int targetWidth = expanding ? expandedWidth : collapsedWidth;
        int targetHeight = h;

        ValueAnimator heightAnim =
                ValueAnimator.ofInt(params.height, targetHeight);

        heightAnim.addUpdateListener(a -> {
            params.height = (int) a.getAnimatedValue();
            mWindowManager.updateViewLayout(mView, params);
        });

        ValueAnimator widthAnim =
                ValueAnimator.ofInt(mView.getMeasuredWidth(), targetWidth);

        widthAnim.addUpdateListener(a -> {
            params.width = (int) a.getAnimatedValue();
            if (onChange != null)
                onChange.onChange(a.getAnimatedFraction());
            mWindowManager.updateViewLayout(mView, params);
        });

        widthAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                if (onStart != null) onStart.onFinish();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (onEnd != null) onEnd.onFinish();
            }
        });

        widthAnim.setInterpolator(
                expanding
                        ? new OvershootInterpolator(0.6f)
                        : new DecelerateInterpolator(1.6f)
        );

        heightAnim.setInterpolator(
                expanding
                        ? new OvershootInterpolator(0.6f)
                        : new DecelerateInterpolator(1.6f)
        );

        activeAnimSet = new AnimatorSet();
        activeAnimSet.setDuration(expanding ? 380 : 300);
        activeAnimSet.playTogether(widthAnim, heightAnim);
        activeAnimSet.start();
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Plugin management
    // ════════════════════════════════════════════════════════════════════════════

    public void enqueue(BasePlugin plugin) {
        if (!queued.contains(plugin.getID())) {
            boolean insertFront = binded_plugin != null
                    && plugins.indexOf(plugin) < plugins.indexOf(binded_plugin);
            if (insertFront) queued.add(0, plugin.getID()); else queued.add(plugin.getID());
        }
        bindPlugin();
    }

    public void dequeue(BasePlugin plugin) {
        if (!queued.contains(plugin.getID())) return;
        queued.remove(plugin.getID());
        if (binded_plugin != null && binded_plugin.getID().equals(plugin.getID())) binded_plugin = null;
        bindPlugin();
    }

    private void bindPlugin() {
        if (queued.isEmpty()) {
            if (binded_plugin != null) binded_plugin.onUnbind();
            binded_plugin = null;
            closeOverlay();
            return;
        }
        if (binded_plugin != null && Objects.equals(queued.get(0), binded_plugin.getID())) return;
        if (binded_plugin != null) binded_plugin.onUnbind();

        Optional<BasePlugin> found = plugins.stream()
                .filter(p -> p.getID().equals(queued.get(0))).findFirst();
        if (!found.isPresent()) return;
        binded_plugin = found.get();
        attachPluginView(binded_plugin.onBind());
    }

    private void attachPluginView(View pluginView) {
        if (mView == null) return;
        View existing = mView.findViewById(R.id.binded);
        if (existing != null) { ViewGroup ep = (ViewGroup) existing.getParent(); if (ep != null) ep.removeView(existing); }

        ((ViewGroup) mView).addView(pluginView);
        ConstraintSet cs = new ConstraintSet();
        cs.clone((ConstraintLayout) mView);
        cs.connect(pluginView.getId(), ConstraintSet.TOP,    mView.getId(), ConstraintSet.TOP,    0);
        cs.connect(pluginView.getId(), ConstraintSet.BOTTOM, mView.getId(), ConstraintSet.BOTTOM, 0);
        cs.applyTo((ConstraintLayout) mView);

        mView.getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;
        setGapWidth(gap);
        mWindowManager.updateViewLayout(mView, mView.getLayoutParams());
        if (binded_plugin != null) binded_plugin.onBindComplete();
    }

    private void closeOverlay() {
        if (mView == null) return;
        animateOverlay(minHeight, minWidth, false, new CallBack(), new CallBack() {
            @Override public void onFinish() {
                if (mView == null) return;
                View bound = mView.findViewById(R.id.binded);
                if (bound != null) ((ViewGroup) mView).removeView(bound);
                mView.getLayoutParams().width = minWidth;
                mWindowManager.updateViewLayout(mView, mView.getLayoutParams());
            }
        }, false);
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════════════════════

    private void setGapWidth(int width) {
        if (mView == null) return;
        View v = mView.findViewById(R.id.blank_space);
        if (v != null) v.setMinimumWidth(width);
    }

    private WindowManager.LayoutParams getParams(int w, int h, int flags) {
        return new WindowManager.LayoutParams(w, h,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT);
    }

    private boolean isColorDark(int color) {
        double darkness = 1 - (0.299 * Color.red(color)
                + 0.587 * Color.green(color)
                + 0.114 * Color.blue(color)) / 255.0;
        return darkness >= 0.5;
    }

    public int dpToInt(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    public int getAttr(int attr) {
        final TypedValue value = new TypedValue();
        ctx.getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, value, true);
        return value.data;
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ════════════════════════════════════════════════════════════════════════════

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mView != null)
            mView.setVisibility(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
                    ? View.INVISIBLE : View.VISIBLE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(broadcastReceiver);
        if (mView != null) mWindowManager.removeView(mView);
        plugins.forEach(BasePlugin::onDestroy);
        Runtime.getRuntime().exit(0);
    }
}