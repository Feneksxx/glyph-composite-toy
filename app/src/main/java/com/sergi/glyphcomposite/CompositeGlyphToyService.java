package com.sergi.glyphcomposite;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.nothing.ketchum.Glyph;
import com.nothing.ketchum.GlyphException;
import com.nothing.ketchum.GlyphMatrixFrame;
import com.nothing.ketchum.GlyphMatrixManager;
import com.nothing.ketchum.GlyphMatrixObject;

/** One-screen Glyph Toy: clock, notification dot, charging icon and bottom music visualizer. */
public class CompositeGlyphToyService extends Service {
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
    private static final int[][] DIGITS = {
        {7,5,5,5,7}, {2,2,2,2,2}, {7,1,7,4,7}, {7,1,7,1,7}, {5,5,7,1,1},
        {7,4,7,1,7}, {7,4,7,5,7}, {7,1,1,1,1}, {7,5,7,5,7}, {7,5,7,1,7}
    };
    private static final int[][] LARGE_DIGITS = {
        {7,5,5,5,5,5,7}, {2,2,2,2,2,2,2},
        {7,1,1,7,4,4,7}, {7,1,1,7,1,1,7},
        {5,5,5,7,1,1,1}, {7,4,4,7,1,1,7},
        {7,4,4,7,5,5,7}, {7,1,1,1,1,1,1},
        {7,5,5,7,5,5,7}, {7,5,5,7,1,1,7}
    };

    private final Paint pixelPaint = new Paint();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private GlyphMatrixManager manager;
    private AudioManager audioManager;
    private SharedPreferences preferences;
    private float visualizerEnvelope = 0f;
    private long visualizerStartNanos = 0L;
    private int lastMusicVolume = -1;
    private long volumeIndicatorUntil = 0L;

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            renderFrame();
            handler.postDelayed(this, 80);
        }
    };

    @Override public IBinder onBind(Intent intent) {
        initGlyph();
        return null;
    }

    @Override public boolean onUnbind(Intent intent) {
        handler.removeCallbacks(loop);
        if (manager != null) manager.unInit();
        return false;
    }

    private void initGlyph() {
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (preferences.getInt(SETTINGS_VERSION, 0) < 1) {
            preferences.edit().putBoolean(LARGE_CLOCK, true)
                    .putInt(SETTINGS_VERSION, 1).apply();
        }
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        manager = GlyphMatrixManager.getInstance(getApplicationContext());
        manager.init(new GlyphMatrixManager.Callback() {
            @Override public void onServiceConnected(ComponentName name) {
                manager.register(Glyph.DEVICE_23112);
                handler.removeCallbacks(loop);
                handler.post(loop);
            }
            @Override public void onServiceDisconnected(ComponentName name) { }
        });
    }

    private void renderFrame() {
        Bitmap bitmap = Bitmap.createBitmap(25, 25, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Run every frame so the visualizer can also fade out after music stops.
        drawMusicVisualizer(canvas);
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = battery == null ? 0 : battery.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        int batteryStatus = battery == null ? 0
                : battery.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
        boolean charging = batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING
                || batteryStatus == BatteryManager.BATTERY_STATUS_FULL;
        drawClock(canvas, charging);
        if (charging) drawBattery(canvas, level, true);
        else drawBatteryLevelLine(canvas, level);

        updateVolumeIndicator();
        drawVolumeIndicator(canvas);

        if (GlyphNotificationListener.shouldShowNotificationFlash()) {
            drawNotificationFlash(canvas, brightness(NOTIFICATION_FLASH_BRIGHTNESS));
        } else if (GlyphNotificationListener.shouldShowNotificationDot()) {
            drawPixel(canvas, 12, 4, brightness(NOTIFICATION_BRIGHTNESS));
        }

        GlyphMatrixObject object = new GlyphMatrixObject.Builder()
                .setImageSource(bitmap)
                .setBrightness(180)
                .build();
        GlyphMatrixFrame frame = new GlyphMatrixFrame.Builder().addTop(object).build(this);
        try {
            manager.setMatrixFrame(frame.render());
        } catch (GlyphException ignored) {
            // A frame may be rejected while Nothing's service is reconnecting.
        }
    }

    private void drawMusicVisualizer(Canvas canvas) {
        boolean playing = audioManager != null && audioManager.isMusicActive();
        float target = playing ? 1f : 0f;
        visualizerEnvelope += (target - visualizerEnvelope)
                * (target > visualizerEnvelope ? 0.30f : 0.10f);
        if (visualizerEnvelope < 0.02f) return;

        if (visualizerStartNanos == 0L) visualizerStartNanos = System.nanoTime();
        double seconds = (System.nanoTime() - visualizerStartNanos) / 1_000_000_000.0;
        int baseIntensity = brightness(MUSIC_BRIGHTNESS);
        // A stable dim ribbon follows the real lower arc of the matrix.
        // Seven fixed equalizer bars read clearly as music and never spawn randomly.
        int[] bars = {6, 8, 10, 12, 14, 16, 18};
        for (int i = 0; i < bars.length; i++) {
            int x = bars[i];
            int available = 0;
            for (int y = 21; y <= 24; y++) {
                if (Phone3LedLayout.isValid(x, y)) available++;
            }
            if (available == 0) continue;
            float primary = 0.5f + 0.5f
                    * (float) Math.sin(seconds * 8.6 + i * 1.05);
            float secondary = 0.5f + 0.5f
                    * (float) Math.sin(seconds * 4.3 + i * 0.67 + 1.2);
            float beat = 0.5f + 0.5f * (float) Math.sin(seconds * 12.5);
            float level = (0.08f + primary * 0.64f + secondary * 0.18f + beat * 0.10f)
                    * visualizerEnvelope;
            int height = Math.max(1, Math.min(available, (int) Math.ceil(level * available)));
            int intensity = Math.min(255, baseIntensity
                    + Math.round(55f * level));
            int lit = 0;
            for (int y = 21; y <= 24 && lit < height; y++) {
                if (Phone3LedLayout.isValid(x, y)) {
                    drawPixel(canvas, x, y, intensity);
                    lit++;
                }
            }
        }
    }

    private void drawNotificationFlash(Canvas canvas, int intensity) {
        long now = System.currentTimeMillis();
        float cycle = (now % 1300L) / 1300f;
        // Smooth 0 -> 1.35 -> 0 radius: the complete animation stays inside
        // the 3x3 area and never turns into a cross or a distant ring.
        float radius = 0.05f + 1.30f
                * (0.5f - 0.5f * (float) Math.cos(cycle * Math.PI * 2.0));
        float centerPulse = 0.78f + 0.22f
                * (0.5f + 0.5f * (float) Math.sin(now / 170.0));

        // Compact 3x3 pixel circle: a bright centre with a soft one-pixel halo.
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                float delta = distance - radius;
                float ring = (float) Math.exp(-(delta * delta) / 0.13f);
                float amount = 0.07f + ring * 0.78f;
                if (dx == 0 && dy == 0) amount = Math.max(amount, 0.72f * centerPulse);
                int pixelIntensity = Math.min(255, Math.round(intensity * amount));
                drawPixel(canvas, 12 + dx, 4 + dy, pixelIntensity);
            }
        }
    }

    private void drawClock(Canvas canvas, boolean charging) {
        String time = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(new java.util.Date());
        int intensity = brightness(CLOCK_BRIGHTNESS);
        boolean large = !charging && preferences != null
                && preferences.getBoolean(LARGE_CLOCK, true);
        if (large) drawLargePixelText(canvas, time, 4, 9, intensity);
        else drawPixelText(canvas, time, 4, 7, intensity);
    }

    private void drawBattery(Canvas canvas, int level, boolean charging) {
        int left = 8, right = 16, top = 14, bottom = 18;
        int intensity = brightness(BATTERY_BRIGHTNESS);
        double seconds = (System.nanoTime() - visualizerStartNanos) / 1_000_000_000.0;
        int pulse = Math.round(35f * (0.5f + 0.5f * (float) Math.sin(seconds * Math.PI * 2.0)));
        int outline = Math.min(255, intensity + pulse);
        for (int x = left; x <= right; x++) { drawPixel(canvas, x, top, outline); drawPixel(canvas, x, bottom, outline); }
        for (int y = top; y <= bottom; y++) { drawPixel(canvas, left, y, outline); drawPixel(canvas, right, y, outline); }
        drawPixel(canvas, 17, 15, outline);
        drawPixel(canvas, 17, 16, outline);
        drawPixel(canvas, 17, 17, outline);
        int filledColumns = Math.round(Math.max(0, Math.min(100, level)) / 100f * 7);
        if (charging && level >= 100) {
            float fullWave = 0.5f + 0.5f
                    * (float) Math.sin(seconds * Math.PI * 1.6);
            int fullIntensity = Math.min(255, intensity + Math.round(55f * fullWave));
            for (int x = left + 1; x < right; x++) {
                for (int y = 15; y <= 17; y++) {
                    drawPixel(canvas, x, y, fullIntensity);
                }
            }
            int contact = Math.min(255, intensity + Math.round(80f * fullWave));
            drawPixel(canvas, 17, 15, contact);
            drawPixel(canvas, 17, 16, contact);
            drawPixel(canvas, 17, 17, contact);
            return;
        }
        for (int x = 0; x < filledColumns; x++) {
            for (int y = 15; y <= 17; y++) drawPixel(canvas, left + 1 + x, y, intensity);
        }
        if (charging && filledColumns > 0) {
            int shineColumn = (int) Math.floor(seconds * 3.0) % Math.max(1, filledColumns);
            int shine = Math.min(255, intensity + 80);
            drawPixel(canvas, left + 1 + shineColumn, 15, shine);
            drawPixel(canvas, left + 1 + shineColumn, 16, shine);
            drawPixel(canvas, left + 1 + shineColumn, 17, shine);
        }
    }

    private void drawBatteryLevelLine(Canvas canvas, int level) {
        int intensity = brightness(BATTERY_BRIGHTNESS);
        int dim = Math.max(10, Math.round(intensity * 0.18f));
        int filled = Math.round(Math.max(0, Math.min(100, level)) / 100f * 7f);
        for (int x = 0; x < 7; x++) {
            drawPixel(canvas, 9 + x, 18, x < filled ? intensity : dim);
        }
    }

    private void updateVolumeIndicator() {
        if (audioManager == null) return;
        int volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (lastMusicVolume == -1) {
            lastMusicVolume = volume;
        } else if (volume != lastMusicVolume) {
            lastMusicVolume = volume;
            volumeIndicatorUntil = System.currentTimeMillis() + 3500L;
        }
    }

    private void drawVolumeIndicator(Canvas canvas) {
        if (System.currentTimeMillis() >= volumeIndicatorUntil || audioManager == null) return;

        int max = Math.max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int bright = brightness(MUSIC_BRIGHTNESS);
        int dim = Math.max(8, Math.round(bright * 0.12f));
        float level = current / (float) max * 11f;
        // One straight eleven-pixel line just inside the physical edge.
        int[] yLevels = {12, 11, 13, 10, 14, 9, 15, 8, 16, 7, 17};

        for (int i = 0; i < yLevels.length; i++) {
            float fill = Math.max(0f, Math.min(1f, level - i));
            int intensity = Math.round(dim + (bright - dim) * fill);
            int y = yLevels[i];
            drawPixel(canvas, 1, y, intensity);
            drawPixel(canvas, 23, y, intensity);
        }

        // The actual outermost seven-pixel lines are reserved for endpoint
        // animations, so they never become a permanent second volume row.
        long now = System.currentTimeMillis();
        if (current == 0) {
            // Muted: a quiet pulse travels from the centre to both ends and
            // fades, like a signal with no output rather than a volume bar.
            float phase = (now % 1200L) / 1200f;
            float travel = phase < 0.5f ? phase * 2f : (1f - phase) * 2f;
            float radius = travel * 3f;
            for (int y = 9; y <= 15; y++) {
                float wave = Math.max(0f, 1f - Math.abs(Math.abs(y - 12) - radius) * 1.45f);
                float amount = 0.08f + wave * 0.70f;
                int edgeIntensity = Math.round(dim + (bright - dim) * amount);
                drawPixel(canvas, 0, y, edgeIntensity);
                drawPixel(canvas, 24, y, edgeIntensity);
            }
        } else if (current >= max) {
            // Maximum: both edge lines stay present while a symmetric peak
            // travels centre -> ends -> centre.
            float phase = (now % 1400L) / 1400f;
            float travel = phase < 0.5f ? phase * 2f : (1f - phase) * 2f;
            float radius = travel * 3f;
            for (int y = 9; y <= 15; y++) {
                float peak = Math.max(0f, 1f - Math.abs(Math.abs(y - 12) - radius) * 1.55f);
                float amount = 0.20f + peak * 0.80f;
                int edgeIntensity = Math.min(255, Math.round(dim + (bright - dim) * amount));
                drawPixel(canvas, 0, y, edgeIntensity);
                drawPixel(canvas, 24, y, edgeIntensity);
            }
        }
    }

    private void drawPixelText(Canvas canvas, String value, int startX, int startY, int intensity) {
        int x = startX;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == ':') {
                drawPixel(canvas, x, startY + 1, intensity);
                drawPixel(canvas, x, startY + 3, intensity);
                x += 2;
                continue;
            }
            int[] rows = DIGITS[character - '0'];
            for (int row = 0; row < 5; row++) {
                for (int column = 0; column < 3; column++) {
                    if ((rows[row] & (1 << (2 - column))) != 0) drawPixel(canvas, x + column, startY + row, intensity);
                }
            }
            x += 4;
        }
    }

    private void drawLargePixelText(Canvas canvas, String value, int startX, int startY, int intensity) {
        int x = startX;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == ':') {
                drawPixel(canvas, x, startY + 2, intensity);
                drawPixel(canvas, x, startY + 4, intensity);
                x += 2;
                continue;
            }
            int[] rows = LARGE_DIGITS[character - '0'];
            for (int row = 0; row < 7; row++) {
                for (int column = 0; column < 3; column++) {
                    if ((rows[row] & (1 << (2 - column))) != 0) {
                        drawPixel(canvas, x + column, startY + row, intensity);
                    }
                }
            }
            x += 4;
        }
    }

    private void drawPixel(Canvas canvas, int x, int y) {
        drawPixel(canvas, x, y, brightness(CLOCK_BRIGHTNESS));
    }

    private void drawPixel(Canvas canvas, int x, int y, int intensity) {
        if (!Phone3LedLayout.isValid(x, y)) return;
        pixelPaint.setColor(Color.rgb(intensity, intensity, intensity));
        pixelPaint.setStyle(Paint.Style.FILL);
        pixelPaint.setAntiAlias(false);
        canvas.drawPoint(x, y, pixelPaint);
    }

    private int brightness(String key) {
        int value = preferences == null ? DEFAULT_BRIGHTNESS
                : preferences.getInt(key, DEFAULT_BRIGHTNESS);
        int master = preferences == null ? DEFAULT_MASTER_BRIGHTNESS
                : preferences.getInt(MASTER_BRIGHTNESS, DEFAULT_MASTER_BRIGHTNESS);
        float component = Math.max(20, Math.min(180, value)) / 180f;
        float overall = Math.max(20, Math.min(180, master)) / 180f;
        return Math.round(component * overall * 255f);
    }
}
