package com.abh80.smartedge.plugins.BatteryPlugin;

import android.animation.*;
import android.content.*;
import android.graphics.Color;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.*;
import android.view.animation.*;
import android.widget.*;

import com.abh80.smartedge.R;
import com.abh80.smartedge.plugins.BasePlugin;
import com.abh80.smartedge.services.OverlayService;
import com.abh80.smartedge.utils.CallBack;
import com.abh80.smartedge.utils.SettingStruct;
import com.abh80.smartedge.views.BatteryImageView;

import java.util.ArrayList;

public class BatteryPlugin extends BasePlugin {

    @Override public String getID() { return "BatteryPlugin"; }
    @Override public String getName() { return "Battery"; }

    private static final int COLOR_FULL = 0xFF34D399;
    private static final int COLOR_HIGH = 0xFF6EE7B7;
    private static final int COLOR_MID = 0xFFFBBF24;
    private static final int COLOR_LOW = 0xFFF97316;
    private static final int COLOR_CRITICAL = 0xFFEF4444;
    private static final int COLOR_CHARGING = 0xFF60A5FA;
    private static final int COLOR_TEXT_DIM = 0xFFAAAAAA;

    private OverlayService ctx;
    private Handler mHandler;

    private boolean expanded = false;
    private float batteryPercent = 0f;
    private boolean isCharging = false;
    private int batteryHealth;
    private int batteryTemperature;
    private int batteryVoltage;
    private String batteryTechnology;
    private int chargePlug;

    private View mView;

    // Collapsed
    private LinearLayout collapsedContainer;
    private BatteryImageView collapsedBattery;
    private TextView collapsedPercent;

    // Expanded
    private LinearLayout expandedContainer;
    private BatteryImageView expandedBattery;
    private TextView detailedPercent;
    private TextView chargingStatus;
    private TextView healthText;
    private TextView temperatureText;
    private TextView voltageText;
    private TextView timeRemainingText;

    private ValueAnimator pulseAnimator;

    // ─────────────────────────────────────────────────────────────

    @Override
    public void onCreate(OverlayService context) {
        ctx = context;
        mHandler = new Handler(Looper.getMainLooper());

        IntentFilter f = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        f.addAction(Intent.ACTION_POWER_CONNECTED);
        f.addAction(Intent.ACTION_POWER_DISCONNECTED);
        ctx.registerReceiver(receiver, f);
    }

    @Override
    public View onBind() {
        mView = LayoutInflater.from(ctx).inflate(R.layout.battery_layout, null);
        init();
        return mView;
    }

    @Override
    public void onUnbind() {
        stopPulse();
        mView = null;
    }

    @Override
    public void onDestroy() {
        stopPulse();
        try { ctx.unregisterReceiver(receiver); } catch (Exception ignored) {}
        if (mHandler != null) mHandler.removeCallbacksAndMessages(null);
    }

    // ─────────────────────────────────────────────────────────────

    private void init() {

        collapsedContainer = mView.findViewById(R.id.collapsed_container);
        collapsedBattery = mView.findViewById(R.id.collapsed_battery);
        collapsedPercent = mView.findViewById(R.id.collapsed_percent);

        expandedContainer = mView.findViewById(R.id.expanded_container);
        expandedBattery = mView.findViewById(R.id.expanded_battery);
        detailedPercent = mView.findViewById(R.id.detailed_percent);
        chargingStatus = mView.findViewById(R.id.charging_status);
        healthText = mView.findViewById(R.id.health_text);
        temperatureText = mView.findViewById(R.id.temperature);
        voltageText = mView.findViewById(R.id.voltage_text);
        timeRemainingText = mView.findViewById(R.id.time_remaining);

        setVis(expandedContainer, false);

        updateView();
    }

    // ─────────────────────────────────────────────────────────────
    // RECEIVER
    // ─────────────────────────────────────────────────────────────

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

                if (level >= 0 && scale > 0)
                    batteryPercent = level * 100f / scale;

                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL;

                batteryHealth = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
                batteryTemperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                batteryVoltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
                batteryTechnology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
                chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            }

            mHandler.post(() -> {
                if (isCharging || batteryPercent <= 15f)
                    ctx.enqueue(BatteryPlugin.this);
                else if (!expanded)
                    ctx.dequeue(BatteryPlugin.this);

                updateView();
                managePulse();
            });
        }
    };

    // ─────────────────────────────────────────────────────────────
    // UI UPDATE
    // ─────────────────────────────────────────────────────────────

    private void updateView() {
        if (expanded) updateExpanded();
        else updateCollapsed();
    }

    private void updateCollapsed() {
        int accent = getAccentColor();

        collapsedPercent.setText(Math.round(batteryPercent) + "%");
        collapsedPercent.setTextColor(accent);

        collapsedBattery.updateBatteryPercent(batteryPercent);
        collapsedBattery.setStrokeColor(accent);

    }

    private void updateExpanded() {
        int accent = getAccentColor();

        detailedPercent.setText(Math.round(batteryPercent) + "%");
        detailedPercent.setTextColor(accent);

        expandedBattery.updateBatteryPercent(batteryPercent);
        expandedBattery.setStrokeColor(isCharging ? COLOR_CHARGING : accent);

        chargingStatus.setText(isCharging ? resolveChargingLabel() : "Discharging");

        healthText.setText(getHealthString(batteryHealth));

        if (batteryTemperature > 0)
            temperatureText.setText((batteryTemperature / 10f) + "°C");

        if (batteryVoltage > 0)
            voltageText.setText((batteryVoltage / 1000f) + "V");

        timeRemainingText.setText(getFormattedTimeRemaining());
    }

    // ─────────────────────────────────────────────────────────────
    // EXPAND / COLLAPSE
    // ─────────────────────────────────────────────────────────────

    @Override
    public void onExpand() {
        if (expanded) return;
        expanded = true;

        setVis(collapsedContainer, false);
        setVis(expandedContainer, true);

        DisplayMetrics metrics = ctx.metrics;

        ctx.animateOverlay(
                ctx.dpToInt(220),
                metrics.widthPixels - ctx.dpToInt(16),
                true,
                new CallBack(),
                new CallBack(),
                false
        );
    }

    @Override
    public void onCollapse() {
        if (!expanded) return;
        expanded = false;

        setVis(expandedContainer, false);
        setVis(collapsedContainer, true);

        ctx.animateOverlay(
                ctx.minHeight,
                ctx.minWidth,
                false,
                new CallBack(),
                new CallBack(),
                false
        );

        if (!isCharging)
            ctx.dequeue(this);
    }

    @Override
    public void onClick() {
        if (expanded) onCollapse();
        else onExpand();
    }

    // ─────────────────────────────────────────────────────────────
    // PULSE
    // ─────────────────────────────────────────────────────────────

    private void managePulse() {
        if (isCharging) startPulse();
        else stopPulse();
    }

    private void startPulse() {
        if (pulseAnimator != null && pulseAnimator.isRunning()) return;

        BatteryImageView target = expanded ? expandedBattery : collapsedBattery;

        int bright = COLOR_CHARGING;
        int dim = blendWithAlpha(bright, 0.4f);

        pulseAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), bright, dim);
        pulseAnimator.setDuration(1600);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.setInterpolator(new DecelerateInterpolator());

        pulseAnimator.addUpdateListener(a -> {
            int c = (int) a.getAnimatedValue();
            target.setStrokeColor(c);
        });

        pulseAnimator.start();
    }

    private void stopPulse() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }

        int solid = getAccentColor();
        collapsedBattery.setStrokeColor(solid);
        expandedBattery.setStrokeColor(solid);
    }

    // ─────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────

    private int getAccentColor() {
        if (isCharging) return COLOR_CHARGING;
        if (batteryPercent > 80) return COLOR_FULL;
        if (batteryPercent > 50) return COLOR_HIGH;
        if (batteryPercent > 20) return COLOR_MID;
        if (batteryPercent > 10) return COLOR_LOW;
        return COLOR_CRITICAL;
    }

    private static int blendWithAlpha(int color, float alpha) {
        return Color.rgb(
                (int)(Color.red(color) * alpha),
                (int)(Color.green(color) * alpha),
                (int)(Color.blue(color) * alpha));
    }

    private String resolveChargingLabel() {
        switch (chargePlug) {
            case BatteryManager.BATTERY_PLUGGED_USB: return "Charging via USB";
            case BatteryManager.BATTERY_PLUGGED_AC: return "Charging via AC";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS: return "Wireless charging";
            default: return "Charging";
        }
    }

    private String getFormattedTimeRemaining() {
        if (isCharging)
            return "~" + formatTime((100f - batteryPercent) / 25f) + " until full";
        return "~" + formatTime(batteryPercent / 12f) + " remaining";
    }

    private static String formatTime(float hours) {
        int total = (int)(hours * 60);
        int h = total / 60;
        int m = total % 60;
        return h > 0 ? h + "h " + m + "m" : m + " min";
    }

    private static String getHealthString(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "Good";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "Overheating";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "Dead";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "Over Voltage";
            default: return "Unknown";
        }
    }

    private static void setVis(View v, boolean on) {
        if (v != null) v.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    @Override public String[] permissionsRequired() { return null; }
    @Override public ArrayList<SettingStruct> getSettings() { return new ArrayList<>(); }
}