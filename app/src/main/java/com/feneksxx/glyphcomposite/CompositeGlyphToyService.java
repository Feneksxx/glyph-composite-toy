package com.feneksxx.glyphcomposite;

import android.app.Service;
import android.content.BroadcastReceiver;
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
    private static final String VOLUME_BRIGHTNESS = "volume_brightness";
    private static final String BATTERY_BRIGHTNESS = "battery_brightness";
    private static final String NOTIFICATION_BRIGHTNESS = "notification_brightness";
    private static final String NOTIFICATION_FLASH_BRIGHTNESS = "notification_flash_brightness";
    private static final String MASTER_BRIGHTNESS = "master_brightness";
    private static final String LARGE_CLOCK = "large_clock";
    private static final String CLOCK_FONT = "clock_font";
    private static final String VISUALIZER_ENABLED = "visualizer_enabled";
    private static final String VISUALIZER_STYLE = "visualizer_style";
    private static final String VISUALIZER_SPEED = "visualizer_speed";
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
    private final float[] visualizerLevels = new float[7];
    private int lastMusicVolume = -1;
    private long volumeIndicatorUntil = 0L;
    private float dotVolumeFrom = 0f;
    private float dotVolumeTo = 0f;
    private float dotVolumeLevel = 0f;
    private long dotVolumeAnimationStarted = 0L;
    private static final long DOT_VOLUME_ANIMATION_MS = 140L;

    private final BroadcastReceiver volumeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (audioManager == null) return;
            int volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (lastMusicVolume == -1 || volume == lastMusicVolume) return;
            handleVolumeChanged(volume);
            handler.removeCallbacks(loop);
            handler.post(loop);
        }
    };

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            renderFrame();
            handler.postDelayed(this, nextFrameDelayMs());
        }
    };

    @Override public IBinder onBind(Intent intent) {
        initGlyph();
        return null;
    }

    @Override public void onCreate() {
        super.onCreate();
        registerReceiver(volumeReceiver,
                new android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION"));
    }

    @Override public void onDestroy() {
        unregisterReceiver(volumeReceiver);
        super.onDestroy();
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
        boolean largeClockEnabled = preferences == null
                || preferences.getBoolean(LARGE_CLOCK, true);
        drawClock(canvas, charging);
        if (charging || !largeClockEnabled) drawBattery(canvas, level, charging);
        else drawBatteryLevelLine(canvas, level);

        updateVolumeIndicator();
        // The clock reserves columns 2..22, so the two outermost columns can
        // safely extend the volume bar only for the dedicated Grid font.
        boolean gridClock = preferences != null
                && preferences.getInt(CLOCK_FONT, 1) == 2;
        boolean dotClock = preferences != null
                && preferences.getInt(CLOCK_FONT, 1) == 1;
        drawVolumeIndicator(canvas, gridClock, dotClock);

        float flashAlpha = GlyphNotificationListener.notificationFlashAlpha();
        if (flashAlpha > 0f) {
            drawNotificationFlash(canvas, Math.round(brightness(NOTIFICATION_FLASH_BRIGHTNESS) * flashAlpha));
        }
        float dotAlpha = GlyphNotificationListener.notificationDotAlpha();
        if (dotAlpha > 0f) {
            drawPixel(canvas, 12, 4, Math.round(brightness(NOTIFICATION_BRIGHTNESS) * dotAlpha));
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

    /**
     * Adaptive refresh keeps animations smooth only while something is moving.
     * An idle clock does not need an 80 ms render loop and can sleep almost a
     * full second between frames, reducing wakeups and battery use.
     */
    private long nextFrameDelayMs() {
        boolean visualizerEnabled = preferences == null
                || preferences.getBoolean(VISUALIZER_ENABLED, true);
        if (visualizerEnabled && audioManager != null && audioManager.isMusicActive()) return 80L;
        if (visualizerEnabled && visualizerEnvelope >= 0.02f) return 80L;
        if (GlyphNotificationListener.shouldShowNotificationFlash()) return 80L;
        if (System.currentTimeMillis() < volumeIndicatorUntil) return 80L;

        // Poll the media volume frequently enough for the edge indicator to
        // react without waiting for the slow idle-clock frame.
        if (audioManager != null) return 80L;

        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int status = battery == null ? 0 : battery.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        if (charging) return 120L;

        // The clock and a stable notification dot only need a slow refresh.
        return 900L;
    }

    private void drawMusicVisualizer(Canvas canvas) {
        if (preferences != null && !preferences.getBoolean(VISUALIZER_ENABLED, true)) {
            visualizerEnvelope = 0f;
            return;
        }
        boolean playing = audioManager != null && audioManager.isMusicActive();
        float target = playing ? 1f : 0f;
        visualizerEnvelope += (target - visualizerEnvelope)
                * (target > visualizerEnvelope ? 0.30f : 0.28f);
        if (visualizerEnvelope < 0.02f) return;

        if (visualizerStartNanos == 0L) visualizerStartNanos = System.nanoTime();
        double speed = preferences == null ? 1.0
                : Math.max(0.5, Math.min(2.0, preferences.getInt(VISUALIZER_SPEED, 100) / 100.0));
        double seconds = (System.nanoTime() - visualizerStartNanos) / 1_000_000_000.0 * speed;
        int baseIntensity = brightness(MUSIC_BRIGHTNESS);
        int style = preferences == null ? 0 : preferences.getInt(VISUALIZER_STYLE, 0);
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
            float primary = 0.5f + 0.5f * (float) Math.sin(seconds * 8.6 + i * 1.05);
            float secondary = 0.5f + 0.5f * (float) Math.sin(seconds * 4.3 + i * 0.67 + 1.2);
            float beat = 0.5f + 0.5f * (float) Math.sin(seconds * 12.5);
            float level;
            if (style == 1) {
                // Pulse: two matching wave fronts travel from the centre to
                // the outer bars, then return. A slightly eased triangle path
                // makes the fronts leave the centre immediately, removing the
                // perceptual pause at the end of each return.
                float phase = (float) ((seconds * 3.40) % 6.0);
                float folded = phase <= 3f ? phase / 3f : (6f - phase) / 3f;
                float offset = 3f * (float) Math.pow(
                        Math.max(0f, Math.min(1f, folded)), 0.68f);
                float leftDistance = Math.abs(i - (3f - offset));
                float rightDistance = Math.abs(i - (3f + offset));
                float front = Math.max(
                        (float) Math.exp(-(leftDistance * leftDistance) / 0.58f),
                        (float) Math.exp(-(rightDistance * rightDistance) / 0.58f));
                float trail = Math.max(
                        (float) Math.exp(-(leftDistance * leftDistance) / 2.20f),
                        (float) Math.exp(-(rightDistance * rightDistance) / 2.20f));
                level = 0.05f + trail * 0.19f + front * 0.74f;
            } else {
                // Wave keeps the original movement and timing. The only
                // visual refinement is the fractional top pixel below,
                // which creates a soft trailing fade without changing the
                // style's actual motion or height pattern.
                level = 0.08f + primary * 0.64f + secondary * 0.18f + beat * 0.10f;
            }
            level *= visualizerEnvelope;
            int intensity = Math.min(255, baseIntensity
                    + Math.round(55f * level));
            if (style == 1) {
                // A fractional top pixel gives the pulse a visually smooth
                // height transition instead of jumping one whole LED at once.
                float pixelHeight = Math.max(0f, Math.min(available, level * available));
                int solidPixels = (int) Math.floor(pixelHeight);
                float fractional = pixelHeight - solidPixels;
                int lit = 0;
                for (int y = 21; y <= 24; y++) {
                    if (!Phone3LedLayout.isValid(x, y)) continue;
                    if (lit < solidPixels) {
                        drawPixel(canvas, x, y, intensity);
                    } else if (lit == solidPixels && fractional > 0.02f) {
                        int softIntensity = Math.max(1,
                                Math.round(intensity * (0.16f + fractional * 0.84f)));
                        drawPixel(canvas, x, y, softIntensity);
                    }
                    lit++;
                }
                continue;
            }
            // Wave also uses a fractional top pixel: the leading edge fades
            // smoothly while the lower pixels remain solid and readable.
            float pixelHeight = Math.max(0.25f, Math.min(available, level * available));
            int solidPixels = (int) Math.floor(pixelHeight);
            float fractional = pixelHeight - solidPixels;
            int lit = 0;
            for (int y = 24; y >= 21; y--) {
                if (Phone3LedLayout.isValid(x, y)) {
                    if (lit < solidPixels) {
                        drawPixel(canvas, x, y, intensity);
                    } else if (lit == solidPixels && fractional > 0.02f) {
                        int softIntensity = Math.max(1,
                                Math.round(intensity * (0.14f + fractional * 0.86f)));
                        drawPixel(canvas, x, y, softIntensity);
                    }
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
        int font = preferences == null ? 1 : preferences.getInt(CLOCK_FONT, 1);
        boolean large = !charging && preferences != null
                && preferences.getBoolean(LARGE_CLOCK, true);
        if (font == 2) {
            if (large) drawGridClock(canvas, time, intensity);
            else drawGridChargingClock(canvas, time, intensity);
            return;
        }
        if (font == 1) {
            if (large) drawDotClock(canvas, time, intensity);
            else drawDotChargingClock(canvas, time, intensity);
            return;
        }
        int startX = 4;
        if (large) drawLargePixelText(canvas, time, startX, 9, intensity);
        else drawPixelText(canvas, time, startX, 7, intensity);
    }

    private void drawGridClock(Canvas canvas, String value, int intensity) {
        int x = 2;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == ':') {
                drawPixel(canvas, x, 10, intensity);
                drawPixel(canvas, x, 14, intensity);
                x += 2;
                continue;
            }
            int[] rows = GRID_LARGE_DIGITS[character - '0'];
            for (int row = 0; row < 7; row++) {
                for (int column = 0; column < 4; column++) {
                    if ((rows[row] & (1 << (3 - column))) != 0) {
                        drawPixel(canvas, x + column, 9 + row, intensity);
                    }
                }
            }
            x += 5;
        }
    }

    private void drawDotClock(Canvas canvas, String value, int intensity) {
        int x = 2;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == ':') {
                drawPixel(canvas, x, 11, intensity);
                drawPixel(canvas, x, 13, intensity);
                x += 2;
                continue;
            }
            int[] rows = DOT_LARGE_DIGITS[character - '0'];
            for (int row = 0; row < 7; row++) {
                for (int column = 0; column < 4; column++) {
                    if ((rows[row] & (1 << (3 - column))) != 0) {
                        drawPixel(canvas, x + column, 9 + row, intensity);
                    }
                }
            }
            x += 5;
        }
    }

    private void drawDotChargingClock(Canvas canvas, String value, int intensity) {
        // Charging uses the same compact clock zone as the default style:
        // five rows starting at row 7, leaving the existing battery geometry
        // untouched below it.
        int x = 2;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == ':') {
                drawPixel(canvas, x, 8, intensity);
                drawPixel(canvas, x, 10, intensity);
                x += 2;
                continue;
            }
            int[] rows = DOT_CHARGING_DIGITS[character - '0'];
            for (int row = 0; row < 5; row++) {
                for (int column = 0; column < 4; column++) {
                    if ((rows[row] & (1 << (3 - column))) != 0) {
                        drawPixel(canvas, x + column, 7 + row, intensity);
                    }
                }
            }
            x += 5;
        }
    }

    private void drawGridChargingClock(Canvas canvas, String value, int intensity) {
        int x = 2;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == ':') {
                drawPixel(canvas, x, 8, intensity);
                drawPixel(canvas, x, 10, intensity);
                x += 2;
                continue;
            }
            int[] rows = GRID_CHARGING_DIGITS[character - '0'];
            for (int row = 0; row < 5; row++) {
                for (int column = 0; column < 4; column++) {
                    if ((rows[row] & (1 << (3 - column))) != 0) {
                        drawPixel(canvas, x + column, 7 + row, intensity);
                    }
                }
            }
            x += 5;
        }
    }

    private void drawBattery(Canvas canvas, int level, boolean charging) {
        int left = 8, right = 16, top = 14, bottom = 18;
        int intensity = brightness(BATTERY_BRIGHTNESS);
        double seconds = (System.nanoTime() - visualizerStartNanos) / 1_000_000_000.0;
        // During normal discharge the battery outline is static. Only the
        // charging states are allowed to animate below.
        int outline = intensity;
        if (charging) {
            int pulse = Math.round(35f
                    * (0.5f + 0.5f * (float) Math.sin(seconds * Math.PI * 2.0)));
            outline = Math.min(255, intensity + pulse);
        }
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
            float initial = volume / (float) Math.max(1,
                    audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            dotVolumeFrom = initial;
            dotVolumeTo = initial;
            dotVolumeLevel = initial;
        } else if (volume != lastMusicVolume) {
            handleVolumeChanged(volume);
        }
    }

    private void handleVolumeChanged(int volume) {
        long now = System.currentTimeMillis();
        if (dotVolumeAnimationStarted != 0L) {
            float progress = Math.min(1f, (now - dotVolumeAnimationStarted)
                    / (float) DOT_VOLUME_ANIMATION_MS);
            float eased = progress * progress * (3f - 2f * progress);
            dotVolumeLevel = dotVolumeFrom + (dotVolumeTo - dotVolumeFrom) * eased;
        }
        lastMusicVolume = volume;
        dotVolumeFrom = dotVolumeLevel;
        dotVolumeTo = volume / (float) Math.max(1,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        dotVolumeAnimationStarted = now;
        volumeIndicatorUntil = now + 3500L;
    }

    private void drawVolumeIndicator(Canvas canvas, boolean gridClock, boolean dotClock) {
        if (System.currentTimeMillis() >= volumeIndicatorUntil || audioManager == null) return;

        int max = Math.max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int bright = brightness(VOLUME_BRIGHTNESS);
        int dim = Math.max(8, Math.round(bright * 0.12f));
        float level = current / (float) max * 11f;
        // One straight eleven-pixel line just inside the physical edge.
        int[] yLevels = {12, 11, 13, 10, 14, 9, 15, 8, 16, 7, 17};

        if (dotClock) {
            long elapsed = System.currentTimeMillis() - dotVolumeAnimationStarted;
            float animatedLevel = dotVolumeTo;
            float progress = 1f;
            if (dotVolumeAnimationStarted != 0L && elapsed < DOT_VOLUME_ANIMATION_MS) {
                progress = Math.max(0f, elapsed / (float) DOT_VOLUME_ANIMATION_MS);
                float eased = progress * progress * (3f - 2f * progress);
                animatedLevel = dotVolumeFrom + (dotVolumeTo - dotVolumeFrom) * eased;
                dotVolumeLevel = animatedLevel;
            } else {
                dotVolumeLevel = dotVolumeTo;
            }
            int filled = Math.round(Math.max(0f, Math.min(1f, animatedLevel)) * 7f);
            int fromFilled = Math.round(dotVolumeFrom * 7f);
            int targetFilled = Math.round(dotVolumeTo * 7f);
            for (int i = 0; i < 7; i++) {
                int y = 15 - i;
                int intensity = i < filled ? bright : 0;
                if (dotVolumeAnimationStarted != 0L
                        && targetFilled > fromFilled && i == fromFilled) {
                    intensity = Math.max(intensity, Math.round(bright * progress));
                } else if (dotVolumeAnimationStarted != 0L
                        && targetFilled < fromFilled && i == targetFilled) {
                    intensity = Math.max(intensity, Math.round(bright * (1f - progress)));
                }
                drawPixel(canvas, 0, y, intensity);
                drawPixel(canvas, 24, y, intensity);
            }
            return;
        }

        if (gridClock) {
            // Grid mode reserves the inner edge columns completely. The
            // outer columns are the only volume indicator in this mode.
            // Unlike the stepped 11-pixel bar below, this uses the complete
            // 0..100% range and cannot saturate early.
            float fraction = current / (float) max;
            int edgeIntensity = Math.round(bright * fraction);
            for (int y : yLevels) {
                drawPixel(canvas, 0, y, edgeIntensity);
                drawPixel(canvas, 24, y, edgeIntensity);
            }
            return;
        }

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
            // Muted: a compact, slow breath at the centre. It never lights
            // the whole edge, so zero volume reads as silence immediately.
            float phase = (now % 1500L) / 1500f;
            float breath = 0.5f - 0.5f * (float) Math.cos(phase * Math.PI * 2.0);
            float radius = 0.15f + breath * 1.85f;
            for (int y = 9; y <= 15; y++) {
                float distance = Math.abs(y - 12);
                float ring = (float) Math.exp(-((distance - radius) * (distance - radius)) / 0.42f);
                float centre = (float) Math.exp(-(distance * distance) / 0.80f);
                float amount = 0.04f + ring * 0.42f + centre * (0.22f * (1f - breath));
                int edgeIntensity = Math.round(dim + (bright - dim) * amount);
                drawPixel(canvas, 0, y, edgeIntensity);
                drawPixel(canvas, 24, y, edgeIntensity);
            }
        } else if (current >= max) {
            // Maximum: the complete seven-pixel edge line remains visible;
            // a bright symmetric crest sweeps out from the centre and returns.
            float phase = (now % 1800L) / 1800f;
            float travel = phase < 0.5f ? phase * 2f : 2f - phase * 2f;
            float radius = travel * 3f;
            for (int y = 9; y <= 15; y++) {
                float distance = Math.abs(y - 12);
                float crest = (float) Math.exp(-((distance - radius) * (distance - radius)) / 0.55f);
                float shimmer = 0.05f * (0.5f + 0.5f
                        * (float) Math.sin(phase * Math.PI * 2.0));
                float amount = 0.34f + shimmer + crest * 0.66f;
                int edgeIntensity = Math.min(255,
                        Math.round(dim + (bright - dim) * amount));
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
            int style = preferences == null ? 1 : preferences.getInt(CLOCK_FONT, 1);
            int[] rows = fontRows(character - '0');
            int width = 3;
            for (int row = 0; row < 5; row++) {
                for (int column = 0; column < width; column++) {
                    if ((rows[row] & (1 << (width - 1 - column))) != 0) drawPixel(canvas, x + column, startY + row, intensity);
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
            int style = preferences == null ? 1 : preferences.getInt(CLOCK_FONT, 1);
            int[] rows = fontRows(character - '0');
            int width = 3;
            for (int row = 0; row < 7; row++) {
                for (int column = 0; column < width; column++) {
                    int sourceRow = Math.min(4, Math.round(row * 4f / 6f));
                    if ((rows[sourceRow] & (1 << (width - 1 - column))) != 0) {
                        drawPixel(canvas, x + column, startY + row, intensity);
                    }
                }
            }
            x += 4;
        }
    }

    private int[] fontRows(int digit) {
        int style = preferences == null ? 1 : preferences.getInt(CLOCK_FONT, 1);
        if (style == 2) return GRID_DIGITS[digit];
        if (style == 1) return DOT_DIGITS[digit];
        return DIGITS[digit];
    }

    // Compact 3x5 grid font. The one is split into two upper and two lower
    // pixels with an empty centre for a cleaner Glyph appearance.
    private static final int[][] GRID_DIGITS = {
        {7,5,5,5,7}, {2,2,0,2,2}, {7,1,3,4,7}, {7,1,3,1,7}, {5,5,7,1,3},
        {7,4,6,1,7}, {7,4,6,5,7}, {7,1,1,1,3}, {7,5,7,5,7}, {7,5,7,1,7}
    };

    private static final int[][] GRID_LARGE_DIGITS = {
        {15,9,9,9,9,9,15}, {2,6,2,2,2,2,7},
        {15,1,1,15,8,8,15}, {15,1,1,7,1,1,15},
        {9,9,9,15,1,1,1}, {15,8,8,15,1,1,15},
        {15,8,8,15,9,9,15}, {15,1,1,1,1,1,1},
        {15,9,9,15,9,9,15}, {15,9,9,15,1,1,15}
    };

    private static final int[][] DOT_LARGE_DIGITS = {
        {6,9,9,0,9,9,6}, {0,1,1,0,1,1,0},
        {6,1,1,6,8,8,6}, {6,1,1,6,1,1,6},
        {0,9,9,6,1,1,0}, {6,8,8,6,1,1,6},
        {6,8,8,6,9,9,6}, {6,1,1,0,1,1,1},
        {6,9,9,6,9,9,6}, {6,9,9,6,1,1,6}
    };

    private static final int[][] DOT_CHARGING_DIGITS = {
        {6,9,0,9,6}, {1,1,0,1,1}, {6,1,6,8,6}, {6,1,6,1,6},
        {9,9,6,1,1}, {6,8,6,1,6}, {6,8,6,9,6}, {6,1,0,1,1},
        {6,9,6,9,6}, {6,9,6,1,6}
    };

    private static final int[][] GRID_CHARGING_DIGITS = {
        {15,9,9,9,15}, {2,6,2,2,7}, {15,1,15,8,15}, {15,1,7,1,15},
        {9,9,15,1,1}, {15,8,15,1,15}, {15,8,15,9,15}, {15,1,1,1,1},
        {15,9,15,9,15}, {15,9,15,1,15}
    };

    private static final int[][] MINIMAL_DIGITS = {
        {7,5,5,5,7}, {2,2,2,2,2}, {7,1,7,4,7}, {7,1,7,1,7}, {5,5,7,1,1},
        {7,4,7,1,7}, {7,4,7,5,7}, {7,1,1,1,1}, {7,5,7,5,7}, {7,5,7,1,7}
    };
    private static final int[][] THIN_DIGITS = {
        {2,5,5,5,2}, {2,6,2,2,7}, {6,1,2,4,7}, {6,1,2,1,6}, {5,5,7,1,1},
        {7,4,6,1,6}, {2,4,6,5,2}, {7,1,2,2,2}, {2,5,2,5,2}, {2,5,3,1,2}
    };
    private static final int[][] DOT_DIGITS = {
        {2,5,5,5,2}, {2,2,2,2,2}, {6,1,2,4,3}, {6,1,2,1,6}, {5,5,7,1,1},
        {7,4,6,1,6}, {2,4,6,5,2}, {7,1,2,2,2}, {2,5,2,5,2}, {2,5,3,1,2}
    };

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
