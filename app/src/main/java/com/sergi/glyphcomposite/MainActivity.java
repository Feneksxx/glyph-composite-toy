package com.sergi.glyphcomposite;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final String PREFS = "glyph_composite";
    private static final String CLOCK_BRIGHTNESS = "clock_brightness";
    private static final String MUSIC_BRIGHTNESS = "music_brightness";
    private static final String BATTERY_BRIGHTNESS = "battery_brightness";
    private static final String NOTIFICATION_BRIGHTNESS = "notification_brightness";
    private static final String NOTIFICATION_FLASH_BRIGHTNESS = "notification_flash_brightness";
    private static final String MASTER_BRIGHTNESS = "master_brightness";
    private static final String LARGE_CLOCK = "large_clock";
    private static final String SETTINGS_VERSION = "settings_version";
    private static final int DEFAULT_BRIGHTNESS = 120;
    private static final int DEFAULT_MASTER_BRIGHTNESS = 180;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (preferences.getInt(SETTINGS_VERSION, 0) < 1) {
            preferences.edit().putBoolean(LARGE_CLOCK, true)
                    .putInt(SETTINGS_VERSION, 1).apply();
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(24));
        root.setBackgroundColor(Color.BLACK);

        TextView eyebrow = text(getString(R.string.ui_eyebrow), 12, Color.rgb(150, 150, 150));
        eyebrow.setLetterSpacing(0.14f);
        root.addView(eyebrow);

        TextView title = text(getString(R.string.ui_title), 32, Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView description = text(getString(R.string.ui_description), 15, Color.rgb(175, 175, 175));
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
        descriptionParams.topMargin = dp(16);
        root.addView(description, descriptionParams);

        Space space = new Space(this);
        root.addView(space, new LinearLayout.LayoutParams(1, dp(22)));

        addBrightnessControl(root, preferences, getString(R.string.brightness_master), MASTER_BRIGHTNESS, DEFAULT_MASTER_BRIGHTNESS);
        addBrightnessControl(root, preferences, getString(R.string.brightness_clock), CLOCK_BRIGHTNESS, DEFAULT_BRIGHTNESS);
        addBrightnessControl(root, preferences, getString(R.string.brightness_music), MUSIC_BRIGHTNESS, DEFAULT_BRIGHTNESS);
        addBrightnessControl(root, preferences, getString(R.string.brightness_battery), BATTERY_BRIGHTNESS, DEFAULT_BRIGHTNESS);
        addBrightnessControl(root, preferences, getString(R.string.brightness_notifications), NOTIFICATION_BRIGHTNESS, DEFAULT_BRIGHTNESS);
        addBrightnessControl(root, preferences, getString(R.string.brightness_flash), NOTIFICATION_FLASH_BRIGHTNESS, DEFAULT_BRIGHTNESS);

        LinearLayout clockMode = card();
        Switch largeClock = new Switch(this);
        largeClock.setText(getString(R.string.large_clock));
        largeClock.setTextColor(Color.WHITE);
        largeClock.setTextSize(12);
        largeClock.setChecked(preferences.getBoolean(LARGE_CLOCK, true));
        largeClock.setOnCheckedChangeListener((button, checked) ->
                preferences.edit().putBoolean(LARGE_CLOCK, checked).apply());
        clockMode.addView(largeClock, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams clockModeParams = new LinearLayout.LayoutParams(-1, -2);
        clockModeParams.topMargin = dp(8);
        root.addView(clockMode, clockModeParams);

        LinearLayout.LayoutParams firstButtonParams = new LinearLayout.LayoutParams(-1, dp(52));
        firstButtonParams.topMargin = dp(12);
        Button notificationAccess = actionButton(getString(R.string.notification_access), false);
        notificationAccess.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        root.addView(notificationAccess, firstButtonParams);

        LinearLayout.LayoutParams managerParams = new LinearLayout.LayoutParams(-1, dp(52));
        managerParams.topMargin = dp(10);
        Button manager = actionButton(getString(R.string.open_glyph_toys), true);
        manager.setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.nothing.thirdparty", "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity"));
            startActivity(intent);
        });
        root.addView(manager, managerParams);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.BLACK);
        scrollView.addView(root);
        setContentView(scrollView);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(14));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(26, 26, 26));
        background.setCornerRadius(dp(18));
        background.setStroke(dp(1), Color.rgb(55, 55, 55));
        card.setBackground(background);
        return card;
    }

    private void addBrightnessControl(LinearLayout root, SharedPreferences preferences,
            String label, String key, int defaultBrightness) {
        LinearLayout brightnessCard = card();
        TextView brightnessTitle = text(label, 12, Color.rgb(175, 175, 175));
        brightnessTitle.setLetterSpacing(0.10f);
        TextView brightnessValue = text("", 18, Color.WHITE);
        brightnessValue.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(brightnessTitle, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(brightnessValue, new LinearLayout.LayoutParams(-2, -2));
        brightnessCard.addView(row);

        SeekBar slider = new SeekBar(this);
        slider.setMax(160);
        int savedBrightness = Math.max(20, Math.min(180,
                preferences.getInt(key, defaultBrightness)));
        slider.setProgress(savedBrightness - 20);
        brightnessValue.setText(percent(savedBrightness));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int value = progress + 20;
                brightnessValue.setText(percent(value));
                preferences.edit().putInt(key, value).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        brightnessCard.addView(slider, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8);
        root.addView(brightnessCard, params);
    }

    private Button actionButton(String label, boolean filled) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setLetterSpacing(0.08f);
        button.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(26));
        if (filled) {
            background.setColor(Color.WHITE);
            button.setTextColor(Color.BLACK);
        } else {
            background.setColor(Color.rgb(26, 26, 26));
            background.setStroke(dp(1), Color.rgb(90, 90, 90));
            button.setTextColor(Color.WHITE);
        }
        button.setBackground(background);
        return button;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setFontFeatureSettings("tnum");
        return view;
    }

    private String percent(int brightness) { return Math.round(brightness / 180f * 100) + "%"; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

}
