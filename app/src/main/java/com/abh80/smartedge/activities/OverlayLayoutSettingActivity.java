package com.abh80.smartedge.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.abh80.smartedge.R;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.slider.Slider;

import java.math.RoundingMode;
import java.text.DecimalFormat;

public class OverlayLayoutSettingActivity extends AppCompatActivity {

    private SharedPreferences sharedPreferences;

    private Slider h, gap, x, w, y;
    private TextView val_h, val_gap, val_x, val_w, val_y;
    private ShapeableImageView add_h, sub_h, add_gap, sub_gap,
            add_x, sub_x, add_w, sub_w, add_y, sub_y;

    @SuppressLint("RestrictedApi")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.overlay_layout_setting_activity);

        setSupportActionBar(findViewById(R.id.toolbar));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDefaultDisplayHomeAsUpEnabled(true);
        }

        sharedPreferences = getSharedPreferences(getPackageName(), MODE_PRIVATE);

        initViews();
        setupButtons();
        setupSliderListeners();
        loadValues();
    }

    private void initViews() {

        h = findViewById(R.id.seekbar_h);
        gap = findViewById(R.id.seekbar_gap);
        x = findViewById(R.id.seekbar_x);
        w = findViewById(R.id.seekbar_w);
        y = findViewById(R.id.seekbar_y);

        val_h = findViewById(R.id.val_h);
        val_gap = findViewById(R.id.val_gap);
        val_x = findViewById(R.id.val_x);
        val_w = findViewById(R.id.val_w);
        val_y = findViewById(R.id.val_y);

        add_h = findViewById(R.id.add_h);
        sub_h = findViewById(R.id.sub_h);
        add_gap = findViewById(R.id.add_gap);
        sub_gap = findViewById(R.id.sub_gap);
        add_x = findViewById(R.id.add_x);
        sub_x = findViewById(R.id.sub_x);
        add_w = findViewById(R.id.add_w);
        sub_w = findViewById(R.id.sub_w);
        add_y = findViewById(R.id.add_y);
        sub_y = findViewById(R.id.sub_y);

        findViewById(R.id.reset_btn).setOnClickListener(v -> resetValues());
    }

    private void setupButtons() {

        add_w.setOnClickListener(v -> stepSlider(w, 1));
        sub_w.setOnClickListener(v -> stepSlider(w, -1));

        add_h.setOnClickListener(v -> stepSlider(h, 1));
        sub_h.setOnClickListener(v -> stepSlider(h, -1));

        add_gap.setOnClickListener(v -> stepSlider(gap, 1));
        sub_gap.setOnClickListener(v -> stepSlider(gap, -1));

        add_x.setOnClickListener(v -> stepSlider(x, 0.1f));
        sub_x.setOnClickListener(v -> stepSlider(x, -0.1f));

        add_y.setOnClickListener(v -> stepSlider(y, 0.1f));
        sub_y.setOnClickListener(v -> stepSlider(y, -0.1f));
    }

    private void setupSliderListeners() {

        Slider.OnChangeListener listener = (slider, value, fromUser) -> {
            if (fromUser) onChange();
        };

        h.addOnChangeListener(listener);
        w.addOnChangeListener(listener);
        gap.addOnChangeListener(listener);
        x.addOnChangeListener(listener);
        y.addOnChangeListener(listener);
    }

    private void stepSlider(Slider slider, float step) {

        float value = slider.getValue() + step;

        if (value > slider.getValueTo()) value = slider.getValueTo();
        if (value < slider.getValueFrom()) value = slider.getValueFrom();

        slider.setValue(value);
        onChange();
    }

    private void loadValues() {
        gap.setValue(sharedPreferences.getFloat("overlay_gap", 50));
        w.setValue(sharedPreferences.getFloat("overlay_w", 100));
        h.setValue(sharedPreferences.getFloat("overlay_h", 34));
        x.setValue(sharedPreferences.getFloat("overlay_x", 0));
        y.setValue(sharedPreferences.getFloat("overlay_y", 0.8f));
        updateTexts();
    }

    private void resetValues() {
        gap.setValue(50);
        w.setValue(100);
        h.setValue(34);
        x.setValue(0);
        y.setValue(0.8f);
        onChange();
    }

    private void onChange() {

        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putFloat("overlay_w", w.getValue());
        editor.putFloat("overlay_h", h.getValue());
        editor.putFloat("overlay_gap", gap.getValue());
        editor.putFloat("overlay_x", x.getValue());
        editor.putFloat("overlay_y", y.getValue());

        editor.apply();

        updateTexts();

        Intent intent = new Intent(getPackageName() + ".OVERLAY_LAYOUT_CHANGE");

        intent.putExtra("overlay_w", w.getValue());
        intent.putExtra("overlay_h", h.getValue());
        intent.putExtra("overlay_gap", gap.getValue());
        intent.putExtra("overlay_x", x.getValue());
        intent.putExtra("overlay_y", y.getValue());

        sendBroadcast(intent);
    }

    @SuppressLint("SetTextI18n")
    private void updateTexts() {

        DecimalFormat df = new DecimalFormat("0.##");
        df.setRoundingMode(RoundingMode.DOWN);

        val_gap.setText(df.format(gap.getValue()) + " dp");
        val_x.setText(df.format(x.getValue()) + " %");
        val_y.setText(df.format(y.getValue()) + " %");
        val_h.setText(df.format(h.getValue()) + " dp");
        val_w.setText(df.format(w.getValue()) + " dp");
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadValues();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}