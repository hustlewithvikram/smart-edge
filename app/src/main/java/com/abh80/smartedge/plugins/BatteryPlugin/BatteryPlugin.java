package com.abh80.smartedge.plugins.BatteryPlugin;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.abh80.smartedge.R;
import com.abh80.smartedge.plugins.BasePlugin;
import com.abh80.smartedge.services.OverlayService;
import com.abh80.smartedge.utils.CallBack;
import com.abh80.smartedge.utils.SettingStruct;
import com.abh80.smartedge.views.BatteryImageView;

import java.util.ArrayList;

public class BatteryPlugin extends BasePlugin {

    // ─── Identity ────────────────────────────────────────────────────────────────
    @Override public String getID()   { return "BatteryPlugin"; }
    @Override public String getName() { return "Battery"; }

    // ─── Design tokens ───────────────────────────────────────────────────────────
    private static final int COLOR_FULL     = 0xFF34D399; // Emerald 400
    private static final int COLOR_HIGH     = 0xFF6EE7B7; // Emerald 300
    private static final int COLOR_MID      = 0xFFFBBF24; // Amber 400
    private static final int COLOR_LOW      = 0xFFF97316; // Orange 500
    private static final int COLOR_CRITICAL = 0xFFEF4444; // Red 500
    private static final int COLOR_CHARGING = 0xFF60A5FA; // Blue 400
    private static final int COLOR_TEXT_DIM = 0xFFAAAAAA;

    private static final int DUR_FADE         = 220;
    private static final int DUR_STAGGER      = 45;
    private static final int DUR_PULSE_PERIOD = 1800;
    private static final float CHARGE_RATE    = 25f;
    private static final float DRAIN_RATE     = 12f;

    // ─── State ───────────────────────────────────────────────────────────────────
    private OverlayService ctx;
    private Handler        mHandler;

    public  boolean expanded           = false;
    private float   batteryPercent     = 0f;
    private boolean isCharging         = false;
    private int     batteryHealth      = BatteryManager.BATTERY_HEALTH_UNKNOWN;
    private int     batteryTemperature = 0;
    private int     batteryVoltage     = 0;
    private String  batteryTechnology  = null;
    private int     chargePlug         = 0;

    // ─── Collapsed views ─────────────────────────────────────────────────────────
    private View             mView;
    private TextView         percentText;
    private BatteryImageView batteryImageView;
    private RelativeLayout   visualizerArea;

    // ─── Expanded views ──────────────────────────────────────────────────────────
    private View             expandedContainer;
    private BatteryImageView expandedBatteryView;
    private TextView         detailedPercentText;
    private TextView         chargingStatusText;
    private TextView         chargingBadge;
    private TextView         timeRemainingText;
    private TextView         healthText;
    private TextView         temperatureText;
    private TextView         technologyText;
    private TextView         voltageText;
    private LinearLayout     quickActions;

    // ─── Layout ──────────────────────────────────────────────────────────────────
    private RelativeLayout coverHolder;
    private View           blank_space;

    // ─── Pulse ───────────────────────────────────────────────────────────────────
    private ValueAnimator pulseAnimator;

    // ════════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ════════════════════════════════════════════════════════════════════════════

    @Override
    public void onCreate(OverlayService context) {
        ctx      = context;
        mHandler = new Handler(Looper.getMainLooper());
        IntentFilter f = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        f.addAction(Intent.ACTION_POWER_CONNECTED);
        f.addAction(Intent.ACTION_POWER_DISCONNECTED);
        ctx.registerReceiver(mBroadcastReceiver, f);
    }

    @Override
    public View onBind() {
        mView = LayoutInflater.from(ctx).inflate(R.layout.battery_layout, null);
        init();
        return mView;
    }

    @Override public void onUnbind() { stopPulse(); mView = null; }

    @Override
    public void onDestroy() {
        stopPulse();
        try { ctx.unregisterReceiver(mBroadcastReceiver); }
        catch (IllegalArgumentException ignored) {}
        if (mHandler != null) mHandler.removeCallbacksAndMessages(null);
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Init
    // ════════════════════════════════════════════════════════════════════════════

    private void init() {
        if (mView == null) return;

        percentText          = mView.findViewById(R.id.text_percent);
        batteryImageView     = mView.findViewById(R.id.cover);
        visualizerArea       = mView.findViewById(R.id.relativeLayout4);
        expandedContainer    = mView.findViewById(R.id.expanded_container);
        expandedBatteryView  = mView.findViewById(R.id.expanded_battery);
        detailedPercentText  = mView.findViewById(R.id.detailed_percent);
        chargingStatusText   = mView.findViewById(R.id.charging_status);
        chargingBadge        = mView.findViewById(R.id.charging_badge);
        timeRemainingText    = mView.findViewById(R.id.time_remaining);
        healthText           = mView.findViewById(R.id.health_text);
        temperatureText      = mView.findViewById(R.id.temperature);
        technologyText       = mView.findViewById(R.id.technology);
        voltageText          = mView.findViewById(R.id.voltage_text);
        quickActions         = mView.findViewById(R.id.quick_actions);
        coverHolder          = mView.findViewById(R.id.relativeLayout);
        blank_space          = mView.findViewById(R.id.blank_space);

        setVis(expandedContainer, false);
        setVis(visualizerArea,    true);
        setVis(coverHolder,       true);
        setVis(blank_space,       true);

        wireButton(R.id.btn_battery_saver,    v -> toggleBatterySaver());
        wireButton(R.id.btn_battery_settings, v -> openBatterySettings());
        updateView();
    }

    private void wireButton(int id, View.OnClickListener l) {
        View btn = mView != null ? mView.findViewById(id) : null;
        if (btn != null) btn.setOnClickListener(l);
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Receiver
    // ════════════════════════════════════════════════════════════════════════════

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                isCharging         = status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL;
                chargePlug         = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                batteryHealth      = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN);
                batteryTemperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                batteryVoltage     = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
                batteryTechnology  = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) batteryPercent = level * 100f / scale;
            } else if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
                isCharging = true;
            } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
                isCharging = false;
            }

            mHandler.post(() -> {
                if (isCharging) ctx.enqueue(BatteryPlugin.this);
                else if (!expanded) ctx.dequeue(BatteryPlugin.this);
                updateView();
                managePulse();
            });
        }
    };

    // ════════════════════════════════════════════════════════════════════════════
    // View updates
    // ════════════════════════════════════════════════════════════════════════════

    private void updateView() {
        if (mView == null) return;
        if (expanded) updateExpandedView(); else updateCollapsedView();
    }

    private void updateCollapsedView() {
        int accent = getAccentColor();
        if (percentText != null) {
            percentText.setText(Math.round(batteryPercent) + "%");
            percentText.setTextColor(accent);
        }
        if (batteryImageView != null) {
            batteryImageView.updateBatteryPercent(batteryPercent);
            batteryImageView.setStrokeColor(accent);
        }
    }

    private void updateExpandedView() {
        if (expandedContainer == null) return;
        int accent = getAccentColor();

        if (expandedBatteryView != null) {
            expandedBatteryView.updateBatteryPercent(batteryPercent);
            expandedBatteryView.setStrokeColor(isCharging ? COLOR_CHARGING : accent);
        }
        if (detailedPercentText != null) {
            detailedPercentText.setText(Math.round(batteryPercent) + "%");
            detailedPercentText.setTextColor(accent);
        }
        if (chargingStatusText != null) {
            chargingStatusText.setText(isCharging ? resolveChargingLabel() : "Discharging");
            chargingStatusText.setTextColor(isCharging ? COLOR_CHARGING : COLOR_TEXT_DIM);
        }
        if (chargingBadge != null) {
            if (isCharging) {
                chargingBadge.setVisibility(View.VISIBLE);
                chargingBadge.setText(resolveChargingBadge());
                chargingBadge.setTextColor(COLOR_CHARGING);
                if (chargingBadge.getBackground() != null)
                    chargingBadge.getBackground().setTint(blendWithAlpha(COLOR_CHARGING, 0.18f));
            } else {
                chargingBadge.setVisibility(View.GONE);
            }
        }
        if (timeRemainingText != null) {
            timeRemainingText.setText(getFormattedTimeRemaining());
            timeRemainingText.setTextColor(COLOR_TEXT_DIM);
        }
        if (healthText != null) {
            healthText.setText(getHealthString(batteryHealth));
            healthText.setTextColor(batteryHealth == BatteryManager.BATTERY_HEALTH_GOOD
                    ? COLOR_FULL : COLOR_LOW);
        }
        if (temperatureText != null && batteryTemperature > 0) {
            float tc = batteryTemperature / 10f;
            temperatureText.setText(String.format("%.1f°C", tc));
            temperatureText.setTextColor(tc > 40f ? COLOR_LOW : COLOR_TEXT_DIM);
            setVis(mView.findViewById(R.id.temp_label), true);
            temperatureText.setVisibility(View.VISIBLE);
        }
        if (voltageText != null && batteryVoltage > 0) {
            voltageText.setText(String.format("%.2f V", batteryVoltage / 1000f));
            voltageText.setTextColor(COLOR_TEXT_DIM);
            setVis(mView.findViewById(R.id.voltage_label), true);
            voltageText.setVisibility(View.VISIBLE);
        }
        if (technologyText != null && batteryTechnology != null && !batteryTechnology.isEmpty()) {
            technologyText.setText(batteryTechnology);
            technologyText.setTextColor(COLOR_TEXT_DIM);
            setVis(mView.findViewById(R.id.tech_label), true);
            technologyText.setVisibility(View.VISIBLE);
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Expand / Collapse
    // ════════════════════════════════════════════════════════════════════════════

    @Override
    public void onExpand() {
        if (expanded || mView == null) return;
        expanded = true;

        // Fade out collapsed shell
        animateFade(coverHolder,    false, 130);
        animateFade(visualizerArea, false, 130);
        mHandler.postDelayed(() -> {
            setVis(coverHolder,    false);
            setVis(visualizerArea, false);
            setVis(blank_space,    false);
        }, 140);

        // Stage expanded container
        if (expandedContainer != null) {
            expandedContainer.setVisibility(View.VISIBLE);
            expandedContainer.setAlpha(0f);
            expandedContainer.setTranslationY(-14f);
            expandedContainer.setScaleX(0.96f);
            expandedContainer.setScaleY(0.96f);
        }

        updateExpandedView();

        DisplayMetrics metrics = ctx.metrics;
        ctx.animateOverlay(ctx.dpToInt(200), metrics.widthPixels - ctx.dpToInt(14),
                true, cbStart, cbEnd, cbChange, false);

        if (expandedBatteryView != null) {
            int s = ctx.dpToInt(52);
            ViewGroup.LayoutParams p = expandedBatteryView.getLayoutParams();
            p.width = s; p.height = s;
            expandedBatteryView.setLayoutParams(p);
        }
    }

    @Override
    public void onCollapse() {
        if (!expanded || mView == null) return;
        expanded = false;

        // Slide expanded panel away
        if (expandedContainer != null) {
            expandedContainer.animate()
                    .alpha(0f).translationY(-12f).scaleX(0.95f).scaleY(0.95f)
                    .setDuration(DUR_FADE)
                    .setInterpolator(new AccelerateInterpolator(1.4f))
                    .withEndAction(() -> setVis(expandedContainer, false))
                    .start();
        }

        mHandler.postDelayed(() -> {
            setVis(coverHolder,    true);
            setVis(visualizerArea, true);
            setVis(blank_space,    true);
            animateFade(coverHolder,    true, DUR_FADE);
            animateFade(visualizerArea, true, DUR_FADE);
            updateCollapsedView();
        }, DUR_FADE / 2);

        ctx.animateOverlay(ctx.minHeight, ViewGroup.LayoutParams.WRAP_CONTENT,
                false, cbStart, cbEnd, cbChange, false);

        if (batteryImageView != null) {
            int s = ctx.dpToInt(ctx.minHeight / 4);
            ViewGroup.LayoutParams p = batteryImageView.getLayoutParams();
            p.width = s; p.height = s;
            batteryImageView.setLayoutParams(p);
        }

        stopPulse();
        if (!isCharging) ctx.dequeue(this);
    }

    @Override
    public void onClick() { if (expanded) onCollapse(); else onExpand(); }

    // ════════════════════════════════════════════════════════════════════════════
    // onBindComplete — pop-in
    // ════════════════════════════════════════════════════════════════════════════

    @Override
    public void onBindComplete() {
        if (mView == null || batteryImageView == null) return;
        int target = ctx.dpToInt(ctx.minHeight / 4);

        ValueAnimator scaleAnim = ValueAnimator.ofInt(0, target);
        scaleAnim.setDuration(360);
        scaleAnim.setInterpolator(new OvershootInterpolator(1.4f));
        scaleAnim.addUpdateListener(a -> {
            int v = (int) a.getAnimatedValue();
            ViewGroup.LayoutParams p = batteryImageView.getLayoutParams();
            p.width = v; p.height = v;
            batteryImageView.setLayoutParams(p);
        });
        scaleAnim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                if (percentText      != null) percentText.setVisibility(View.VISIBLE);
                if (batteryImageView != null) {
                    batteryImageView.requestLayout();
                    batteryImageView.updateBatteryPercent(batteryPercent);
                }
                managePulse();
            }
        });

        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleAnim,
                ObjectAnimator.ofFloat(mView, View.ALPHA, 0f, 1f).setDuration(200));
        set.start();
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Pulse
    // ════════════════════════════════════════════════════════════════════════════

    private void managePulse() {
        if (isCharging) startPulse(); else stopPulse();
    }

    private void startPulse() {
        if (pulseAnimator != null && pulseAnimator.isRunning()) return;
        BatteryImageView target = expanded ? expandedBatteryView : batteryImageView;
        if (target == null) return;

        int bright = expanded ? COLOR_CHARGING : getAccentColor();
        int dim    = blendWithAlpha(bright, 0.4f);

        pulseAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), bright, dim);
        pulseAnimator.setDuration(DUR_PULSE_PERIOD);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.addUpdateListener(a -> {
            int c = (int) a.getAnimatedValue();
            if (batteryImageView    != null) batteryImageView.setStrokeColor(c);
            if (expandedBatteryView != null) expandedBatteryView.setStrokeColor(c);
        });
        pulseAnimator.start();
    }

    private void stopPulse() {
        if (pulseAnimator != null) { pulseAnimator.cancel(); pulseAnimator = null; }
        int solid = getAccentColor();
        if (batteryImageView    != null) batteryImageView.setStrokeColor(solid);
        if (expandedBatteryView != null) expandedBatteryView.setStrokeColor(solid);
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Overlay callbacks
    // ════════════════════════════════════════════════════════════════════════════

    private final CallBack cbChange = new CallBack() {
        @Override
        public void onChange(float p) {
            if (mView == null) return;
            float ef = expanded ? p : 1f - p;
            mView.setPadding(0, (int) (ef * ctx.statusBarHeight), 0, 0);
            applyLeftMargin(coverHolder, (int) (ef * ctx.dpToInt(18)));

            // Staggered cross-fade: collapsed fades out first, expanded fades in after 30%
            if (expanded) {
                setAlpha(coverHolder,       clamp(1f - p * 2.5f));
                setAlpha(visualizerArea,    clamp(1f - p * 2.5f));
                setAlpha(expandedContainer, clamp((p - 0.25f) / 0.75f));
            } else {
                setAlpha(coverHolder,       clamp((p - 0.25f) / 0.75f));
                setAlpha(visualizerArea,    clamp((p - 0.25f) / 0.75f));
                setAlpha(expandedContainer, clamp(1f - p * 2.5f));
            }
        }
    };

    private final CallBack cbStart = new CallBack() {
        @Override public void onFinish() {}
    };

    private final CallBack cbEnd = new CallBack() {
        @Override
        public void onFinish() {
            if (mView == null) return;
            if (expanded) {
                mView.setPadding(0, ctx.statusBarHeight, 0, 0);
                applyLeftMargin(coverHolder, ctx.dpToInt(18));
                revealExpanded();
            } else {
                mView.setPadding(0, 0, 0, 0);
                applyLeftMargin(coverHolder, 0);
                setAlpha(coverHolder,    1f);
                setAlpha(visualizerArea, 1f);
            }
        }
    };

    private void revealExpanded() {
        if (expandedContainer == null) return;
        expandedContainer.setAlpha(0f);
        expandedContainer.setTranslationY(-10f);
        expandedContainer.setScaleX(0.97f);
        expandedContainer.setScaleY(0.97f);
        expandedContainer.animate()
                .alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(DUR_FADE + 40)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();

        if (expandedContainer instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) expandedContainer;
            for (int i = 0; i < g.getChildCount(); i++) {
                View child = g.getChildAt(i);
                child.setAlpha(0f);
                child.setTranslationY(12f);
                child.animate()
                        .alpha(1f).translationY(0f)
                        .setStartDelay((long) i * DUR_STAGGER)
                        .setDuration(DUR_FADE)
                        .setInterpolator(new DecelerateInterpolator(1.3f))
                        .start();
            }
        }
        if (isCharging) startPulse();
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════════════════════

    private int getAccentColor() {
        if (isCharging)             return COLOR_CHARGING;
        if (batteryPercent > 80)    return COLOR_FULL;
        if (batteryPercent > 50)    return COLOR_HIGH;
        if (batteryPercent > 20)    return COLOR_MID;
        if (batteryPercent > 10)    return COLOR_LOW;
        return COLOR_CRITICAL;
    }

    private static int blendWithAlpha(int color, float alpha) {
        return Color.rgb(
                (int)(Color.red(color)   * alpha),
                (int)(Color.green(color) * alpha),
                (int)(Color.blue(color)  * alpha));
    }

    private String resolveChargingLabel() {
        switch (chargePlug) {
            case BatteryManager.BATTERY_PLUGGED_USB:      return "Charging via USB";
            case BatteryManager.BATTERY_PLUGGED_AC:       return "Charging via AC";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS: return "Wireless charging";
            default:                                       return "Charging";
        }
    }

    private String resolveChargingBadge() {
        switch (chargePlug) {
            case BatteryManager.BATTERY_PLUGGED_WIRELESS: return "⚡  Wireless";
            case BatteryManager.BATTERY_PLUGGED_USB:      return "⚡  USB";
            case BatteryManager.BATTERY_PLUGGED_AC:       return "⚡  Wired";
            default:                                       return "⚡  Charging";
        }
    }

    private String getFormattedTimeRemaining() {
        if (isCharging) {
            if (batteryPercent >= 99) return "Fully charged";
            return "~" + formatTime((100f - batteryPercent) / CHARGE_RATE) + " until full";
        }
        if (batteryPercent <= 5) return "Critically low";
        return "~" + formatTime(batteryPercent / DRAIN_RATE) + " remaining";
    }

    private static String formatTime(float hours) {
        int total = (int)(hours * 60), h = total / 60, m = total % 60;
        return h > 0 ? h + "h " + m + "m" : m + " min";
    }

    private static String getHealthString(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD:                return "Good";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT:            return "Overheating";
            case BatteryManager.BATTERY_HEALTH_DEAD:                return "Dead";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:        return "Over Voltage";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: return "Failure";
            case BatteryManager.BATTERY_HEALTH_COLD:                return "Cold";
            default:                                                 return "Unknown";
        }
    }

    private void toggleBatterySaver() {}

    private void openBatterySettings() {
        for (String a : new String[]{
                android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS,
                android.provider.Settings.ACTION_SETTINGS}) {
            try { Intent i = new Intent(a); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i); return; }
            catch (Exception ignored) {}
        }
    }

    private static void setVis(View v, boolean on) {
        if (v != null) v.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    private static void setAlpha(View v, float a) {
        if (v != null) v.setAlpha(clamp(a));
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }

    private static void applyLeftMargin(View v, int m) {
        if (v == null) return;
        ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
        if (p != null) { p.leftMargin = m; v.setLayoutParams(p); }
    }

    private static void animateFade(View v, boolean in, int dur) {
        if (v == null) return;
        v.animate().alpha(in ? 1f : 0f).setDuration(dur)
                .setInterpolator(in ? new DecelerateInterpolator() : new AccelerateInterpolator())
                .start();
    }

    @Override public String[] permissionsRequired() { return null; }
    @Override public ArrayList<SettingStruct> getSettings() { return new ArrayList<>(); }
}