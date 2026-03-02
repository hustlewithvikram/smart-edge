package com.abh80.smartedge.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.abh80.smartedge.R;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.skydoves.colorpickerview.ColorEnvelope;
import com.skydoves.colorpickerview.ColorPickerDialog;
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppearanceActivity extends AppCompatActivity {

    private TextInputLayout textInputLayout;
    private SharedPreferences sharedPreferences;

    @SuppressLint("RestrictedApi")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.appearence_layout);

        setSupportActionBar(findViewById(R.id.toolbar));
        Objects.requireNonNull(getSupportActionBar()).setDefaultDisplayHomeAsUpEnabled(true);

        sharedPreferences = getSharedPreferences(getPackageName(), MODE_PRIVATE);
        textInputLayout = findViewById(R.id.textField);

        int savedColor = sharedPreferences.getInt("color", getColor(R.color.black));
        textInputLayout.getEditText().setText(String.format("#%08X", savedColor));

        findViewById(R.id.apply_btn).setOnClickListener(v -> applyManualColor());

        findViewById(R.id.pick_color_btn).setOnClickListener(v -> openColorPicker());
    }

    private void openColorPicker() {
        new ColorPickerDialog.Builder(this)
                .setTitle("Pick Color")
                .setPositiveButton(
                        "Select",
                        new ColorEnvelopeListener() {
                            @Override
                            public void onColorSelected(ColorEnvelope envelope, boolean fromUser) {

                                int color = envelope.getColor();

                                saveColor(color);
                                updatePreview(color);

                                textInputLayout.getEditText()
                                        .setText(String.format("%06X", (0xFFFFFF & color)));
                            }
                        }
                )
                .setNegativeButton("Cancel",
                        (dialogInterface, i) -> dialogInterface.dismiss())
                .show();
    }

    private void applyManualColor() {
        String input = Objects.requireNonNull(textInputLayout.getEditText())
                .getText().toString().trim();

        if (input.isEmpty()) {
            textInputLayout.setError("Provide a color value");
            return;
        }

        try {
            int parsedColor = parseFlexibleColor(input);
            textInputLayout.setError(null);
            hideKeyboard();
            saveColor(parsedColor);
            Snackbar.make(findViewById(R.id.textField),
                    "Color updated",
                    Snackbar.LENGTH_SHORT).show();
        } catch (Exception e) {
            textInputLayout.setError("Invalid color format");
        }
    }

    private int parseFlexibleColor(String value) {

        // Hex without #
        if (!value.startsWith("#")
                && value.matches("^[0-9A-Fa-f]{6,8}$")) {
            value = "#" + value;
        }

        // RGB format
        if (value.toLowerCase(Locale.ROOT).startsWith("rgb")) {
            return parseRGB(value);
        }

        // Named colors
        try {
            return Color.parseColor(value);
        } catch (Exception ignored) {}

        return Color.parseColor(value);
    }

    private int parseRGB(String value) {
        Pattern pattern = Pattern.compile(
                "rgba?\\((\\d+),(\\d+),(\\d+)(,(\\d*\\.?\\d+))?\\)");
        Matcher matcher = pattern.matcher(value.replace(" ", ""));

        if (!matcher.matches()) {
            throw new IllegalArgumentException();
        }

        int r = Integer.parseInt(matcher.group(1));
        int g = Integer.parseInt(matcher.group(2));
        int b = Integer.parseInt(matcher.group(3));

        if (matcher.group(5) != null) {
            float alpha = Float.parseFloat(matcher.group(5));
            return Color.argb((int) (alpha * 255), r, g, b);
        }

        return Color.rgb(r, g, b);
    }

    private void saveColor(int color) {
        sharedPreferences.edit().putInt("color", color).apply();

        Intent intent = new Intent(getPackageName() + ".COLOR_CHANGED");
        intent.putExtra("color", color);
        sendBroadcast(intent);
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            View view = getCurrentFocus();
            if (view != null)
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        } catch (Exception ignored) {}
    }

    private void updatePreview(int color) {
        View preview = findViewById(R.id.color_preview);
        preview.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) finish();
        return super.onOptionsItemSelected(item);
    }
}