package com.abh80.smartedge.plugins.BatteryPlugin;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
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
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.GridLayout;

import com.abh80.smartedge.R;
import com.abh80.smartedge.plugins.BasePlugin;
import com.abh80.smartedge.services.OverlayService;
import com.abh80.smartedge.utils.CallBack;
import com.abh80.smartedge.utils.SettingStruct;
import com.abh80.smartedge.views.BatteryImageView;

import java.util.ArrayList;

public class BatteryPlugin extends BasePlugin {

    @Override
    public String getID() {
        return "BatteryPlugin";
    }

    @Override
    public String getName() {
        return "Battery";
    }

    private OverlayService ctx;
    private Handler mHandler;

    // State variables
    public boolean expanded = false;
    private float batteryPercent = 0f;
    private boolean isCharging = false;
    private int batteryHealth = BatteryManager.BATTERY_HEALTH_UNKNOWN;
    private int batteryTemperature = 0;
    private int batteryVoltage = 0;

    // Views - collapsed state
    private View mView;
    private TextView percentText;
    private BatteryImageView batteryImageView;

    // Views - expanded state
    private View expandedContainer;
    private BatteryImageView expandedBatteryView;
    private TextView detailedPercentText;
    private TextView chargingStatusText;
    private TextView quickTimeText;
    private TextView healthText;
    private TextView timeRemainingText;
    private TextView temperatureText;
    private TextView technologyText;
    private LinearLayout quickActions;

    // Layout containers
    private RelativeLayout coverHolder;
    private View blank_space;
    private RelativeLayout visualizerArea; // For collapsed percentage text

    @Override
    public void onCreate(OverlayService context) {
        ctx = context;
        mHandler = new Handler(Looper.getMainLooper());
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        ctx.registerReceiver(mBroadcastReceiver, filter);
    }

    @Override
    public View onBind() {
        mView = LayoutInflater.from(ctx).inflate(R.layout.battery_layout, null);
        init();
        return mView;
    }

    private void init() {
        if (mView == null) return;

        // Find collapsed state views
        percentText = mView.findViewById(R.id.text_percent);
        batteryImageView = mView.findViewById(R.id.cover);
        visualizerArea = mView.findViewById(R.id.relativeLayout4); // Area for collapsed percentage

        // Find expanded container and views
        expandedContainer = mView.findViewById(R.id.expanded_container);
        expandedBatteryView = mView.findViewById(R.id.expanded_battery);
        detailedPercentText = mView.findViewById(R.id.detailed_percent);
        chargingStatusText = mView.findViewById(R.id.charging_status);
        quickTimeText = mView.findViewById(R.id.quick_time);
        healthText = mView.findViewById(R.id.health_text);
        timeRemainingText = mView.findViewById(R.id.time_remaining);
        temperatureText = mView.findViewById(R.id.temperature);
        technologyText = mView.findViewById(R.id.technology);
        quickActions = mView.findViewById(R.id.quick_actions);

        // Find layout containers
        coverHolder = mView.findViewById(R.id.relativeLayout);
        blank_space = mView.findViewById(R.id.blank_space);

        // Initially hide expanded content, show collapsed
        if (expandedContainer != null) expandedContainer.setVisibility(View.GONE);
        if (visualizerArea != null) visualizerArea.setVisibility(View.VISIBLE);
        if (coverHolder != null) coverHolder.setVisibility(View.VISIBLE);
        if (blank_space != null) blank_space.setVisibility(View.VISIBLE);

        // Set click listeners for quick actions
        if (quickActions != null && quickActions.getChildCount() > 0) {
            View btnBatterySaver = mView.findViewById(R.id.btn_battery_saver);
            View btnBatterySettings = mView.findViewById(R.id.btn_battery_settings);

            if (btnBatterySaver != null) {
                btnBatterySaver.setOnClickListener(v -> {
                    toggleBatterySaver();
                });
            }

            if (btnBatterySettings != null) {
                btnBatterySettings.setOnClickListener(v -> {
                    openBatterySettings();
                });
            }
        }

        updateView();
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                int status = intent.getExtras().getInt(BatteryManager.EXTRA_STATUS);
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING;

                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                batteryPercent = level * 100 / (float) scale;

                // Get additional battery info
                batteryHealth = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN);
                batteryTemperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                batteryVoltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
                String technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);

                mHandler.post(() -> {
                    if (isCharging) {
                        ctx.enqueue(BatteryPlugin.this);
                        updateView();

                        // Update expanded view if visible
                        if (expanded) {
                            updateExpandedView(technology);
                        }
                    } else {
                        // Only dequeue if not expanded
                        if (!expanded) {
                            ctx.dequeue(BatteryPlugin.this);
                        }
                        updateView();

                        // Update expanded view if visible
                        if (expanded) {
                            updateExpandedView(technology);
                        }
                    }
                });
            } else if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
                isCharging = true;
                mHandler.post(() -> {
                    ctx.enqueue(BatteryPlugin.this);
                    updateView();
                });
            } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
                isCharging = false;
                mHandler.post(() -> {
                    if (!expanded) {
                        ctx.dequeue(BatteryPlugin.this);
                    }
                    updateView();
                });
            }
        }
    };

    private void updateView() {
        if (mView == null) return;

        int color = getBatteryColor(batteryPercent);
        int roundedPercent = Math.round(batteryPercent);

        // Update collapsed view if visible
        if (!expanded) {
            if (percentText != null) {
                percentText.setText(roundedPercent + "%");
                percentText.setTextColor(color);
            }

            if (batteryImageView != null) {
                batteryImageView.updateBatteryPercent(batteryPercent);
                batteryImageView.setStrokeColor(color);
            }
        }

        // Update expanded view if visible
        if (expanded) {
            updateExpandedView(null);
        }
    }

    private void updateExpandedView(String technology) {
        if (expandedContainer == null) return;

        int color = getBatteryColor(batteryPercent);
        int roundedPercent = Math.round(batteryPercent);

        // Update expanded battery view
        if (expandedBatteryView != null) {
            expandedBatteryView.updateBatteryPercent(batteryPercent);
            expandedBatteryView.setStrokeColor(color);
        }

        // Update percentage text
        if (detailedPercentText != null) {
            detailedPercentText.setText(roundedPercent + "%");
            detailedPercentText.setTextColor(color);
        }

        // Update charging status
        if (chargingStatusText != null) {
            String status;
            int statusColor;

            if (isCharging) {
                int chargePlug = ctx.getSystemService(BatteryManager.class).getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
                status = getChargingStatus(chargePlug);
                statusColor = Color.GREEN;
            } else {
                status = "Not charging";
                statusColor = Color.GRAY;
            }

            chargingStatusText.setText(status);
            chargingStatusText.setTextColor(statusColor);
        }

        // Update quick time estimate
        if (quickTimeText != null) {
            String timeEstimate = getTimeEstimate();
            quickTimeText.setText(timeEstimate);
            quickTimeText.setVisibility(timeEstimate.isEmpty() ? View.GONE : View.VISIBLE);
        }

        // Update health
        if (healthText != null) {
            String healthStr = getHealthString(batteryHealth);
            healthText.setText(healthStr);
        }

        // Update time remaining
        if (timeRemainingText != null) {
            String timeRemaining = getFormattedTimeRemaining();
            timeRemainingText.setText(timeRemaining);
        }

        // Update temperature if available
        if (temperatureText != null && batteryTemperature > 0) {
            float tempCelsius = batteryTemperature / 10.0f;
            temperatureText.setText(String.format("%.1f°C", tempCelsius));

            // Show temperature label if we have temperature data
            TextView tempLabel = mView.findViewById(R.id.temp_label);
            if (tempLabel != null) {
                tempLabel.setVisibility(View.VISIBLE);
                temperatureText.setVisibility(View.VISIBLE);
            }
        }

        // Update technology if available
        if (technologyText != null && technology != null && !technology.isEmpty()) {
            technologyText.setText(technology);

            // Show technology label if we have technology data
            TextView techLabel = mView.findViewById(R.id.tech_label);
            if (techLabel != null) {
                techLabel.setVisibility(View.VISIBLE);
                technologyText.setVisibility(View.VISIBLE);
            }
        }
    }

    private String getChargingStatus(int chargePlug) {
        // Determine charging speed based on USB type or AC
        if (chargePlug == BatteryManager.BATTERY_PLUGGED_USB) {
            return "Charging (USB)";
        } else if (chargePlug == BatteryManager.BATTERY_PLUGGED_AC) {
            return "Charging (AC)";
        } else if (chargePlug == BatteryManager.BATTERY_PLUGGED_WIRELESS) {
            return "Charging (Wireless)";
        } else {
            return "Charging";
        }
    }

    private String getTimeEstimate() {
        if (isCharging) {
            if (batteryPercent >= 99) return "Full";
            float hours = (100 - batteryPercent) / 25; // Approx 25% per hour
            return formatTime(hours);
        } else {
            if (batteryPercent <= 5) return "Low";
            float hours = batteryPercent / 12; // Approx 12% per hour
            return formatTime(hours);
        }
    }

    private String getFormattedTimeRemaining() {
        if (isCharging) {
            if (batteryPercent >= 99) return "Fully charged";
            float hours = (100 - batteryPercent) / 25; // Approx 25% per hour
            return String.format("~%s until full", formatDetailedTime(hours));
        } else {
            if (batteryPercent <= 5) return "Battery critically low";
            float hours = batteryPercent / 12; // Approx 12% per hour
            return String.format("~%s remaining", formatDetailedTime(hours));
        }
    }

    private String formatTime(float hours) {
        int totalMinutes = (int) (hours * 60);
        int hrs = totalMinutes / 60;
        int mins = totalMinutes % 60;

        if (hrs > 0) {
            return String.format("~%dh %dm", hrs, mins);
        } else {
            return String.format("~%dm", mins);
        }
    }

    private String formatDetailedTime(float hours) {
        int totalMinutes = (int) (hours * 60);
        int hrs = totalMinutes / 60;
        int mins = totalMinutes % 60;

        if (hrs > 0) {
            return String.format("%d hours %d minutes", hrs, mins);
        } else {
            return String.format("%d minutes", mins);
        }
    }

    private String getHealthString(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "Good";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "Overheating";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "Dead";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "Over Voltage";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: return "Failed";
            case BatteryManager.BATTERY_HEALTH_COLD: return "Cold";
            default: return "Unknown";
        }
    }

    private int getBatteryColor(float percent) {
        if (percent > 80) return Color.GREEN;
        if (percent > 20) return Color.YELLOW;
        if (percent > 10) return Color.rgb(255, 165, 0); // Orange
        return Color.RED;
    }

    private void toggleBatterySaver() {
        // Implement battery saver toggle
        // This would require additional permissions and system APIs
    }

    private void openBatterySettings() {
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } catch (Exception e) {
            // Fallback to general settings
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(intent);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public void onUnbind() {
        mView = null;
    }

    @Override
    public void onBindComplete() {
        if (mView == null || batteryImageView == null) return;

        ValueAnimator valueAnimator = ValueAnimator.ofInt(0, ctx.dpToInt(ctx.minHeight / 4));
        valueAnimator.setDuration(300);
        valueAnimator.addUpdateListener(valueAnimator1 -> {
            ViewGroup.LayoutParams p = batteryImageView.getLayoutParams();
            p.width = (int) valueAnimator1.getAnimatedValue();
            p.height = (int) valueAnimator1.getAnimatedValue();
            batteryImageView.setLayoutParams(p);
        });
        valueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                if (percentText != null) {
                    percentText.setVisibility(View.VISIBLE);
                }
                if (batteryImageView != null) {
                    batteryImageView.requestLayout();
                    batteryImageView.updateBatteryPercent(batteryPercent);
                }
            }
        });
        valueAnimator.start();
    }

    @Override
    public void onDestroy() {
        try {
            ctx.unregisterReceiver(mBroadcastReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver was not registered, ignore
        }
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    public void onExpand() {
        if (expanded || mView == null) return;
        expanded = true;

        // Hide collapsed views
        if (coverHolder != null) coverHolder.setVisibility(View.INVISIBLE);
        if (visualizerArea != null) visualizerArea.setVisibility(View.INVISIBLE);
        if (blank_space != null) blank_space.setVisibility(View.GONE);

        // Show expanded container (will be faded in by animation)
        if (expandedContainer != null) {
            expandedContainer.setVisibility(View.VISIBLE);
            expandedContainer.setAlpha(0f);
        }

        // Update cover holder layout
        if (coverHolder != null) {
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) coverHolder.getLayoutParams();
            params.removeRule(RelativeLayout.CENTER_VERTICAL);
            coverHolder.setLayoutParams(params);
        }

        // Update expanded view with current data
        updateExpandedView(null);

        // Animate the overlay to expanded size
        DisplayMetrics metrics = ctx.metrics;
        int expandedHeight = ctx.dpToInt(220); // Slightly taller for new layout
        ctx.animateOverlay(expandedHeight, metrics.widthPixels - ctx.dpToInt(15),
                expanded, OverLayCallBackStart, overLayCallBackEnd, onChange, false);

        // Don't animate the collapsed battery view since we're hiding it
        // Instead, ensure expanded battery view is properly sized
        if (expandedBatteryView != null) {
            ViewGroup.LayoutParams params = expandedBatteryView.getLayoutParams();
            params.width = ctx.dpToInt(48);
            params.height = ctx.dpToInt(48);
            expandedBatteryView.setLayoutParams(params);
        }
    }

    @Override
    public void onCollapse() {
        if (!expanded || mView == null) return;
        expanded = false;

        // Hide expanded content immediately
        if (expandedContainer != null) {
            expandedContainer.setVisibility(View.GONE);
        }

        // Show collapsed views
        if (coverHolder != null) coverHolder.setVisibility(View.VISIBLE);
        if (visualizerArea != null) visualizerArea.setVisibility(View.VISIBLE);
        if (blank_space != null) blank_space.setVisibility(View.VISIBLE);

        // Update cover holder layout
        if (coverHolder != null) {
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) coverHolder.getLayoutParams();
            params.addRule(RelativeLayout.CENTER_VERTICAL);
            coverHolder.setLayoutParams(params);
        }

        // Update collapsed view
        updateView();

        // Animate back to collapsed size
        ctx.animateOverlay(ctx.minHeight, ViewGroup.LayoutParams.WRAP_CONTENT,
                expanded, OverLayCallBackStart, overLayCallBackEnd, onChange, false);

        // Ensure collapsed battery view is properly sized
        if (batteryImageView != null) {
            int targetSize = ctx.dpToInt(ctx.minHeight / 4);
            ViewGroup.LayoutParams params = batteryImageView.getLayoutParams();
            params.width = targetSize;
            params.height = targetSize;
            batteryImageView.setLayoutParams(params);
        }
    }

    @Override
    public void onClick() {
        // Toggle expand/collapse on click
        if (expanded) {
            onCollapse();
        } else {
            onExpand();
        }
    }

    private void animateChild(boolean expanding, int targetHeight) {
        if (batteryImageView == null) return;

        int startHeight = batteryImageView.getHeight();
        int startWidth = batteryImageView.getWidth();

        if (startHeight <= 0) startHeight = ctx.dpToInt(ctx.minHeight / 4);
        if (startWidth <= 0) startWidth = ctx.dpToInt(ctx.minHeight / 4);

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(300);
        animator.setInterpolator(new OvershootInterpolator(0.5f));

        final int finalStartHeight = startHeight;
        final int finalStartWidth = startWidth;

        animator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            int currentHeight = (int) (finalStartHeight + (targetHeight - finalStartHeight) * fraction);
            int currentWidth = (int) (finalStartWidth + (targetHeight - finalStartWidth) * fraction);

            ViewGroup.LayoutParams params = batteryImageView.getLayoutParams();
            if (params != null) {
                params.height = currentHeight;
                params.width = currentWidth;
                batteryImageView.setLayoutParams(params);
            }
        });

        animator.start();
    }

    // Animation callbacks
    private final CallBack onChange = new CallBack() {
        @Override
        public void onChange(float p) {
            if (mView == null || coverHolder == null) return;

            float f = expanded ? p : 1 - p;
            mView.setPadding(0, (int) (f * ctx.statusBarHeight), 0, 0);

            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) coverHolder.getLayoutParams();
            if (params != null) {
                params.leftMargin = (int) (f * ctx.dpToInt(20));
                coverHolder.setLayoutParams(params);
            }

            // Animate expanded container alpha
            if (expandedContainer != null) {
                expandedContainer.setAlpha(f);
            }

            // Animate collapsed views alpha
            if (!expanded) {
                if (coverHolder != null) coverHolder.setAlpha(1 - f);
                if (visualizerArea != null) visualizerArea.setAlpha(1 - f);
            } else {
                if (coverHolder != null) coverHolder.setAlpha(f);
                if (visualizerArea != null) visualizerArea.setAlpha(f);
            }
        }
    };

    private final CallBack OverLayCallBackStart = new CallBack() {
        @Override
        public void onFinish() {
            // Nothing needed at start
        }
    };

    private final CallBack overLayCallBackEnd = new CallBack() {
        @Override
        public void onFinish() {
            if (mView == null) return;

            if (expanded) {
                // Set final padding and margin
                mView.setPadding(0, ctx.statusBarHeight, 0, 0);

                if (coverHolder != null) {
                    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) coverHolder.getLayoutParams();
                    params.leftMargin = ctx.dpToInt(20);
                    coverHolder.setLayoutParams(params);
                }

                // Ensure expanded container is fully visible
                if (expandedContainer != null) {
                    expandedContainer.setAlpha(1f);
                }
            } else {
                // Reset layout for collapsed state
                mView.setPadding(0, 0, 0, 0);

                if (coverHolder != null) {
                    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) coverHolder.getLayoutParams();
                    params.leftMargin = 0;
                    coverHolder.setLayoutParams(params);
                }

                // Ensure collapsed views are fully visible
                if (coverHolder != null) coverHolder.setAlpha(1f);
                if (visualizerArea != null) visualizerArea.setAlpha(1f);
            }
        }
    };

    @Override
    public String[] permissionsRequired() {
        return null;
    }

    @Override
    public ArrayList<SettingStruct> getSettings() {
        ArrayList<SettingStruct> settings = new ArrayList<>();
        // Add settings for showing temperature, technology, quick actions, etc.
        return settings;
    }
}