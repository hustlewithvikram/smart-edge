package com.abh80.smartedge.plugins.MediaSession;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
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

import androidx.annotation.NonNull;

import com.abh80.smartedge.utils.CallBack;
import com.abh80.smartedge.R;
import com.abh80.smartedge.plugins.BasePlugin;
import com.abh80.smartedge.services.NotiService;
import com.abh80.smartedge.services.OverlayService;
import com.abh80.smartedge.utils.SettingStruct;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.slider.Slider;

import org.apache.commons.lang3.time.DurationFormatUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class MediaSessionPlugin extends BasePlugin {

    private Slider slider;
    private TextView elapsedView;
    private TextView remainingView;
    public String current_package_name = "";
    public boolean expanded = false;
    public Map<String, MediaController.Callback> callbackMap = new HashMap<>();
    private boolean seekbar_dragging = false;
    public Instant last_played;
    OverlayService ctx;
    Handler mHandler;
    public MediaController mCurrent;

    private final Runnable r = new Runnable() {
        @Override
        public void run() {
            if (!expanded || mCurrent == null) return;

            try {
                PlaybackState playbackState = mCurrent.getPlaybackState();
                MediaMetadata metadata = mCurrent.getMetadata();

                if (playbackState == null || metadata == null) {
                    closeOverlay();
                    return;
                }

                long elapsed = playbackState.getPosition();
                if (elapsed < 0) {
                    closeOverlay();
                    return;
                }

                long total = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
                if (total <= 0) {
                    closeOverlay();
                    return;
                }

                elapsedView.setText(DurationFormatUtils.formatDuration(elapsed, "mm:ss", true));
                remainingView.setText("-" + DurationFormatUtils.formatDuration(Math.abs(total - elapsed), "mm:ss", true));

                // Use setValue() for Slider - ensure it's a multiple of stepSize
                if (!seekbar_dragging && total > 0) {
                    float progress = ((float) elapsed / total) * 100;
                    // Ensure value is valid for stepSize
                    if (progress < 0) progress = 0;
                    if (progress > 100) progress = 100;
                    // Round to handle floating point precision issues
                    float validValue = Math.round(progress * 10) / 10.0f;
                    slider.setValue(validValue);
                }

                // Schedule next update
                mHandler.postDelayed(this, 1000);
            } catch (Exception e) {
                // Handle errors gracefully
                closeOverlay();
            }
        }
    };

    private boolean overlayOpen = false;

    public boolean overlayOpen() {
        return overlayOpen;
    }

    public void closeOverlay() {
        animateChild(0, new CallBack() {
            @Override
            public void onFinish() {
                overlayOpen = false;
                shouldRemoveOverlay();
            }
        });
    }

    public void closeOverlay(CallBack callBack) {
        animateChild(0, new CallBack() {
            @Override
            public void onFinish() {
                overlayOpen = false;
                if (callBack != null) {
                    callBack.onFinish();
                }
                shouldRemoveOverlay();
            }
        });
    }

    private MaterialButton pause_play;

    public MediaController getActiveCurrent(List<MediaController> mediaControllers) {
        if (mediaControllers == null || mediaControllers.isEmpty()) return null;
        try {
            Optional<MediaController> controller = mediaControllers.stream()
                    .filter(x -> x.getPlaybackState() != null &&
                            x.getPlaybackState().getState() == PlaybackState.STATE_PLAYING)
                    .findFirst();
            return controller.orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isColorDark(int color) {
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return darkness >= 0.5;
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    public void onPlayerResume(boolean b) {
        if (mCurrent == null || pause_play == null) return;

        if (expanded && b) {
            try {
                // Set the correct icon based on state
                pause_play.setIconResource(R.drawable.pause);
                if (ctx != null && ctx.textColor != 0) {
                    pause_play.setIconTint(ColorStateList.valueOf(ctx.textColor));
                }
            } catch (Exception e) {
                // Handle error
            }
        }

        if (visualizer != null && mCurrent.getMetadata() != null) {
            int index = -1;
            try {
                List<MediaController> controllerList = mediaSessionManager.getActiveSessions(
                        new ComponentName(ctx.getBaseContext(), NotiService.class));

                for (int v = 0; v < controllerList.size(); v++) {
                    if (Objects.equals(controllerList.get(v).getPackageName(), mCurrent.getPackageName())) {
                        index = v;
                        break;
                    }
                }

                if (index != -1) {
                    visualizer.setPlayerId(index);

                    Bitmap bm = mCurrent.getMetadata().getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                    if (bm != null) {
                        int dc = getDominantColor(bm);
                        if (isColorDark(dc)) {
                            dc = lightenColor(dc);
                        }
                        visualizer.setColor(dc);
                    }
                }
            } catch (Exception e) {
                // Ignore errors
            }
        }

        // Start updates if expanded
        if (expanded) {
            mHandler.removeCallbacks(r);
            mHandler.post(r);
        }
    }

    private int lightenColor(int colorin) {
        float[] hsv = new float[3];
        Color.colorToHSV(colorin, hsv);
        hsv[2] = Math.min(1.0f, hsv[2] + 0.3f);
        return Color.HSVToColor(hsv);
    }

    private SongVisualizer visualizer;
    private MediaSessionManager mediaSessionManager;

    @SuppressLint("UseCompatLoadingForDrawables")
    public void onPlayerPaused(boolean b) {
        if (mCurrent == null || pause_play == null) return;

        if (expanded && b) {
            try {
                // Set the correct icon based on state
                pause_play.setIconResource(R.drawable.play);
                if (ctx != null && ctx.textColor != 0) {
                    pause_play.setIconTint(ColorStateList.valueOf(ctx.textColor));
                }
            } catch (Exception e) {
                // Handle error
            }
        }

        last_played = Instant.now();

        // Remove any existing delayed callbacks
        mHandler.removeCallbacks(r);

        // Schedule auto-close
        mHandler.postDelayed(() -> {
            if (last_played == null || mCurrent == null) return;

            long timeSinceLastPlayed = Instant.now().toEpochMilli() - last_played.toEpochMilli();
            if (timeSinceLastPlayed >= 60 * 1000) {
                try {
                    List<MediaController> activeSessions = mediaSessionManager.getActiveSessions(
                            new ComponentName(ctx, NotiService.class));

                    MediaController active = getActiveCurrent(activeSessions);
                    if (active == null || !Objects.equals(active.getPackageName(), current_package_name)) {
                        closeOverlay();
                    }
                } catch (Exception e) {
                    // Ignore errors
                }
            }
        }, 60 * 1000);
    }

    public static int getDominantColor(Bitmap bitmap) {
        if (bitmap == null) return Color.GRAY;
        Bitmap newBitmap = Bitmap.createScaledBitmap(bitmap, 1, 1, true);
        final int color = newBitmap.getPixel(0, 0);
        newBitmap.recycle();
        return color;
    }

    @Override
    public String getID() {
        return "MediaSessionPlugin";
    }

    private MediaSessionManager.OnActiveSessionsChangedListener listnerForActiveSessions = new MediaSessionManager.OnActiveSessionsChangedListener() {
        @Override
        public void onActiveSessionsChanged(List<MediaController> controllers) {
            if (controllers == null) return;
            for (MediaController controller : controllers) {
                if (callbackMap.get(controller.getPackageName()) != null) continue;
                MediaCallback c = new MediaCallback(controller, MediaSessionPlugin.this);
                callbackMap.put(controller.getPackageName(), c);
                controller.registerCallback(c);
            }
        }
    };

    @Override
    public void onCreate(OverlayService context) {
        ctx = context;
        mHandler = new Handler(Looper.getMainLooper());
        mView = LayoutInflater.from(context).inflate(R.layout.media_session_layout, null);
        if (mView != null) {
            mView.findViewById(R.id.blank_space).setVisibility(View.VISIBLE);
            init();
            updateTextColors();
        }

        mediaSessionManager = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
        if (mediaSessionManager != null) {
            try {
                mediaSessionManager.addOnActiveSessionsChangedListener(listnerForActiveSessions,
                        new ComponentName(ctx, NotiService.class));

                List<MediaController> controllers = mediaSessionManager.getActiveSessions(
                        new ComponentName(ctx, NotiService.class));
                if (controllers != null) {
                    for (MediaController controller : controllers) {
                        if (callbackMap.get(controller.getPackageName()) != null) continue;
                        MediaCallback c = new MediaCallback(controller, this);
                        callbackMap.put(controller.getPackageName(), c);
                        controller.registerCallback(c);
                    }
                }
            } catch (SecurityException e) {
                // Handle permission issues
            } catch (Exception e) {
                // Handle other exceptions
            }
        }
    }

    public void shouldRemoveOverlay() {
        if (mediaSessionManager == null || ctx == null) return;

        try {
            List<MediaController> controllers = mediaSessionManager.getActiveSessions(
                    new ComponentName(ctx, NotiService.class));

            MediaController active = getActiveCurrent(controllers);
            if (active == null || !Objects.equals(active.getPackageName(), current_package_name)) {
                ctx.dequeue(this);
            }
        } catch (Exception e) {
            // Ignore errors
        }
    }

    public void openOverlay(String pkg_name) {
        if (overlayOpen || ctx == null) return;
        overlayOpen = true;
        current_package_name = pkg_name;
        animateChild(ctx.dpToInt(ctx.minHeight / 4), new CallBack() {
            @Override
            public void onFinish() {
                // Overlay is now open
            }
        });
    }

    private View mView;
    private ShapeableImageView cover;
    private MaterialButton back;
    private MaterialButton next;

    private void init() {
        if (mView == null) return;

        slider = mView.findViewById(R.id.progressBar);
        elapsedView = mView.findViewById(R.id.elapsed);
        remainingView = mView.findViewById(R.id.remaining);
        pause_play = mView.findViewById(R.id.pause_play);
        next = mView.findViewById(R.id.next_play);
        back = mView.findViewById(R.id.back_play);
        cover = mView.findViewById(R.id.cover);
        coverHolder = mView.findViewById(R.id.relativeLayout);
        text_info = mView.findViewById(R.id.text_info);
        controls_holder = mView.findViewById(R.id.controls_holder);
        visualizer = mView.findViewById(R.id.visualizer);

        // IMPORTANT: Set initial icons for buttons
        if (back != null) {
            back.setIconResource(R.drawable.fast_rewind);
        }
        if (next != null) {
            next.setIconResource(R.drawable.fast_forward);
        }
        if (pause_play != null) {
            pause_play.setIconResource(R.drawable.pause);
        }

        // Configure Slider properly
        slider.setStepSize(0.1f);
        slider.setValue(0f);

        // Setup click listeners
        pause_play.setOnClickListener(l -> {
            if (mCurrent == null || mCurrent.getPlaybackState() == null) return;

            PlaybackState state = mCurrent.getPlaybackState();
            if (state.getState() == PlaybackState.STATE_PAUSED) {
                mCurrent.getTransportControls().play();
                // Update icon immediately
                pause_play.setIconResource(R.drawable.pause);
            } else {
                mCurrent.getTransportControls().pause();
                // Update icon immediately
                pause_play.setIconResource(R.drawable.play);
            }
            // Update icon tint
            if (ctx != null && ctx.textColor != 0) {
                pause_play.setIconTint(ColorStateList.valueOf(ctx.textColor));
            }
        });

        // Set initial colors
        updateTextColors();

        // Setup transport controls
        next.setOnClickListener(l -> {
            if (mCurrent != null) {
                mCurrent.getTransportControls().skipToNext();
            }
        });

        back.setOnClickListener(l -> {
            if (mCurrent != null) {
                mCurrent.getTransportControls().skipToPrevious();
            }
        });

        // Setup Slider listeners
        slider.addOnChangeListener(new Slider.OnChangeListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
                if (fromUser) {
                    // Update elapsed time while dragging
                    if (mCurrent != null && mCurrent.getMetadata() != null) {
                        long duration = mCurrent.getMetadata().getLong(MediaMetadata.METADATA_KEY_DURATION);
                        if (duration > 0) {
                            long elapsed = (long) (value / 100 * duration);
                            elapsedView.setText(DurationFormatUtils.formatDuration(elapsed, "mm:ss", true));
                            remainingView.setText("-" + DurationFormatUtils.formatDuration(
                                    Math.abs(duration - elapsed), "mm:ss", true));
                        }
                    }
                }
            }
        });

        slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
                seekbar_dragging = true;
                mHandler.removeCallbacks(r);
            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                seekbar_dragging = false;

                // Perform seek when user releases slider
                if (mCurrent != null && mCurrent.getMetadata() != null) {
                    long duration = mCurrent.getMetadata().getLong(MediaMetadata.METADATA_KEY_DURATION);
                    if (duration > 0) {
                        float progress = slider.getValue();
                        long position = (long) (progress / 100 * duration);
                        mCurrent.getTransportControls().seekTo(position);
                    }
                }

                // Resume updates if expanded
                if (expanded && mCurrent != null) {
                    mHandler.removeCallbacks(r);
                    mHandler.post(r);
                }
            }
        });
    }

    private void updateTextColors() {
        if (mView == null) return;

        TextView titleView = mView.findViewById(R.id.title);
        TextView artistView = mView.findViewById(R.id.artist_subtitle);

        if (elapsedView != null && ctx.textColor != 0) {
            elapsedView.setTextColor(ctx.textColor);
        }
        if (remainingView != null && ctx.textColor != 0) {
            remainingView.setTextColor(ctx.textColor);
        }
        if (titleView != null && ctx.textColor != 0) {
            titleView.setTextColor(ctx.textColor);
        }
        if (artistView != null && ctx.textColor != 0) {
            artistView.setTextColor(ctx.textColor);
        }

        // Apply tint to MaterialButtons
        if (back != null && ctx.textColor != 0) {
            back.setIconTint(ColorStateList.valueOf(ctx.textColor));
            back.setIconSize(ctx.dpToInt(24));
        }
        if (next != null && ctx.textColor != 0) {
            next.setIconTint(ColorStateList.valueOf(ctx.textColor));
            next.setIconSize(ctx.dpToInt(24));
        }
        if (pause_play != null && ctx.textColor != 0) {
            pause_play.setIconTint(ColorStateList.valueOf(ctx.textColor));
            pause_play.setIconSize(ctx.dpToInt(28));
        }

        // Update Slider colors
        if (slider != null && ctx.textColor != 0) {
            slider.setTrackActiveTintList(ColorStateList.valueOf(ctx.textColor));

            // Create semi-transparent version for inactive track
            int alpha = (int) (Color.alpha(ctx.textColor) * 0.3f);
            int inactiveColor = Color.argb(alpha,
                    Color.red(ctx.textColor),
                    Color.green(ctx.textColor),
                    Color.blue(ctx.textColor));
            slider.setTrackInactiveTintList(ColorStateList.valueOf(inactiveColor));
            slider.setThumbTintList(ColorStateList.valueOf(ctx.textColor));
        }
    }

    @Override
    public View onBind() {
        return mView;
    }

    @Override
    public void onUnbind() {
        mHandler.removeCallbacks(r);
    }

    @Override
    public void onDestroy() {
        mHandler.removeCallbacks(r);

        if (visualizer != null) {
            visualizer.release();
        }

        if (mediaSessionManager != null) {
            mediaSessionManager.removeOnActiveSessionsChangedListener(listnerForActiveSessions);
        }

        for (MediaController.Callback callback : callbackMap.values()) {
            if (mCurrent != null) {
                mCurrent.unregisterCallback(callback);
            }
        }
        callbackMap.clear();

        mCurrent = null;
        mView = null;
    }

    @Override
    public void onTextColorChange() {
        updateTextColors();
    }

    RelativeLayout coverHolder;
    private final CallBack onChange = new CallBack() {
        @Override
        public void onChange(float p) {
            if (mView == null || coverHolder == null) return;

            float f = expanded ? p : 1 - p;
            mView.setPadding(0, (int) (f * ctx.statusBarHeight), 0, 0);

            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) coverHolder.getLayoutParams();
            if (params != null) {
                params.leftMargin = (int) (f * ctx.dpToInt(20));
                coverHolder.setLayoutParams(params);
            }
        }
    };

    @Override
    public void onExpand() {
        if (expanded || mView == null) return;
        expanded = true;
        DisplayMetrics metrics = ctx.metrics;
        ctx.animateOverlay(ctx.dpToInt(210), metrics.widthPixels - ctx.dpToInt(15),
                expanded, OverLayCallBackStart, overLayCallBackEnd, onChange, false);
        animateChild(true, ctx.dpToInt(50));
    }

    LinearLayout text_info;
    LinearLayout controls_holder;

    private final CallBack OverLayCallBackStart = new CallBack() {
        @Override
        public void onFinish() {
            if (mView == null) return;

            if (expanded) {
                mView.findViewById(R.id.blank_space).setVisibility(View.GONE);

                if (text_info != null) text_info.setVisibility(View.VISIBLE);
                if (controls_holder != null) controls_holder.setVisibility(View.VISIBLE);
                if (slider != null) slider.setVisibility(View.VISIBLE);
                if (elapsedView != null) elapsedView.setVisibility(View.VISIBLE);
                if (remainingView != null) remainingView.setVisibility(View.VISIBLE);

                // Start marquee
                View titleView = mView.findViewById(R.id.title);
                View artistView = mView.findViewById(R.id.artist_subtitle);
                if (titleView != null) titleView.setSelected(true);
                if (artistView != null) artistView.setSelected(true);

                // Update cover holder layout
                if (coverHolder != null) {
                    RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) coverHolder.getLayoutParams();
                    params.removeRule(RelativeLayout.CENTER_VERTICAL);
                    coverHolder.setLayoutParams(params);
                }
            } else {
                mHandler.removeCallbacks(r);

                if (text_info != null) text_info.setVisibility(View.GONE);
                if (controls_holder != null) controls_holder.setVisibility(View.GONE);
                if (slider != null) slider.setVisibility(View.GONE);
                if (elapsedView != null) elapsedView.setVisibility(View.GONE);
                if (remainingView != null) remainingView.setVisibility(View.GONE);
                mView.findViewById(R.id.blank_space).setVisibility(View.VISIBLE);

                // Stop marquee
                View titleView = mView.findViewById(R.id.title);
                View artistView = mView.findViewById(R.id.artist_subtitle);
                if (titleView != null) titleView.setSelected(false);
                if (artistView != null) artistView.setSelected(false);

                // Update cover holder layout
                if (coverHolder != null) {
                    RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) coverHolder.getLayoutParams();
                    params.addRule(RelativeLayout.CENTER_VERTICAL);
                    coverHolder.setLayoutParams(params);
                }
            }
        }
    };

    private final CallBack overLayCallBackEnd = new CallBack() {
        @SuppressLint("UseCompatLoadingForDrawables")
        @Override
        public void onFinish() {
            if (mView == null) return;

            if (expanded) {
                // Set final padding and margin
                mView.setPadding(0, ctx.statusBarHeight, 0, 0);

                if (coverHolder != null) {
                    RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) coverHolder.getLayoutParams();
                    params.leftMargin = ctx.dpToInt(20);
                    coverHolder.setLayoutParams(params);
                }

                // Update play/pause button - FIX for MaterialButton
                if (pause_play != null && mCurrent != null && mCurrent.getPlaybackState() != null) {
                    int state = mCurrent.getPlaybackState().getState();
                    if (state == PlaybackState.STATE_PLAYING) {
                        pause_play.setIconResource(R.drawable.pause);
                    } else {
                        pause_play.setIconResource(R.drawable.play);
                    }
                    if (ctx.textColor != 0) {
                        pause_play.setIconTint(ColorStateList.valueOf(ctx.textColor));
                    }
                }

                // Start seekbar updates
                mHandler.removeCallbacks(r);
                if (mCurrent != null && mCurrent.getPlaybackState() != null &&
                        mCurrent.getPlaybackState().getState() == PlaybackState.STATE_PLAYING) {
                    mHandler.post(r);
                }

                // Fade in controls
                if (text_info != null) text_info.setAlpha(1);
                if (controls_holder != null) controls_holder.setAlpha(1);
                if (slider != null) slider.setAlpha(1);
                if (elapsedView != null) elapsedView.setAlpha(1);
                if (remainingView != null) remainingView.setAlpha(1);
            } else {
                // Reset layout for collapsed state
                mView.setPadding(0, 0, 0, 0);

                if (coverHolder != null) {
                    RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) coverHolder.getLayoutParams();
                    params.leftMargin = 0;
                    coverHolder.setLayoutParams(params);
                }
            }
        }
    };

    @Override
    public void onCollapse() {
        if (!expanded || mView == null) return;
        expanded = false;
        ctx.animateOverlay(ctx.minHeight, ViewGroup.LayoutParams.WRAP_CONTENT,
                expanded, OverLayCallBackStart, overLayCallBackEnd, onChange, false);
        animateChild(false, ctx.dpToInt(ctx.minHeight / 4));
    }

    @Override
    public void onClick() {
        if (!ctx.sharedPreferences.getBoolean("ms_enable_touch_expanded", false)) {
            // Default behavior: toggle expand/collapse
            if (expanded) {
                onCollapse();
            } else {
                onExpand();
            }
        } else if (mCurrent != null && mCurrent.getSessionActivity() != null) {
            // Open music app
            try {
                mCurrent.getSessionActivity().send();
            } catch (PendingIntent.CanceledException e) {
                // Ignore
            }
        }
    }

    @Override
    public ArrayList<SettingStruct> getSettings() {
        ArrayList<SettingStruct> s = new ArrayList<>();
        s.add(new SettingStruct("Open music app on touch when expanded", "Media Session", SettingStruct.TYPE_TOGGLE) {
            @Override
            public boolean onAttach(Context ctx) {
                return ctx.getSharedPreferences(ctx.getPackageName(), Context.MODE_PRIVATE)
                        .getBoolean("ms_enable_touch_expanded", false);
            }

            @Override
            public void onCheckChanged(boolean checked, Context ctx) {
                ctx.getSharedPreferences(ctx.getPackageName(), Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("ms_enable_touch_expanded", checked)
                        .apply();

                if (MediaSessionPlugin.this.ctx != null) {
                    MediaSessionPlugin.this.ctx.sharedPreferences.putBoolean("ms_enable_touch_expanded", checked);
                }
            }
        });
        return s;
    }

    public void queueUpdate(UpdateQueueStruct queueStruct) {
        if (ctx == null || mView == null) return;

        ctx.enqueue(this);

        TextView titleView = mView.findViewById(R.id.title);
        TextView artistView = mView.findViewById(R.id.artist_subtitle);
        ShapeableImageView imageView = cover;

        if (titleView != null) titleView.setText(queueStruct.getTitle());
        if (artistView != null) artistView.setText(queueStruct.getArtist());
        if (imageView != null && queueStruct.getCover() != null) {
            imageView.setImageBitmap(queueStruct.getCover());
        }
    }

    private void animateChild(boolean expanding, int targetHeight) {
        if (cover == null || visualizer == null) return;

        int startHeight = cover.getHeight();
        int startWidth = cover.getWidth();

        // Ensure we have valid starting dimensions
        if (startHeight <= 0) startHeight = ctx.dpToInt(40);
        if (startWidth <= 0) startWidth = ctx.dpToInt(40);

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(300);
        animator.setInterpolator(new OvershootInterpolator(0.5f));

        final int finalStartHeight = startHeight;
        final int finalStartWidth = startWidth;

        animator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();

            int currentHeight = (int) (finalStartHeight + (targetHeight - finalStartHeight) * fraction);
            int currentWidth = (int) (finalStartWidth + (targetHeight - finalStartWidth) * fraction);

            ViewGroup.LayoutParams params1 = cover.getLayoutParams();
            ViewGroup.LayoutParams params2 = visualizer.getLayoutParams();

            if (params1 != null && params2 != null) {
                params1.height = currentHeight;
                params2.height = currentHeight;
                params1.width = currentWidth;
                params2.width = currentWidth;

                cover.setLayoutParams(params1);
                visualizer.setLayoutParams(params2);
            }
        });

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                if (expanding) {
                    visualizer.setVisibility(View.GONE);
                    if (visualizer.paused) {
                        visualizer.paused = true;
                    }
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!expanding) {
                    visualizer.setVisibility(View.VISIBLE);
                    if (visualizer.paused) {
                        visualizer.paused = false;
                    }
                }
            }
        });

        animator.start();
    }

    private void animateChild(int targetScale, CallBack callback) {
        if (cover == null || visualizer == null) return;

        float startScale = cover.getScaleX();
        float endScale = targetScale != 0 ? 1f : 0f;

        ValueAnimator animator = ValueAnimator.ofFloat(startScale, endScale);
        animator.setDuration(300);

        animator.addUpdateListener(animation -> {
            float scale = (float) animation.getAnimatedValue();
            cover.setScaleX(scale);
            cover.setScaleY(scale);
            visualizer.setScaleX(scale);
            visualizer.setScaleY(scale);
        });

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (callback != null) {
                    callback.onFinish();
                }
            }
        });

        animator.start();
    }

    @Override
    public String[] permissionsRequired() {
        return new String[]{android.Manifest.permission.MEDIA_CONTENT_CONTROL};
    }

    @Override
    public String getName() {
        return "Media Session";
    }
}