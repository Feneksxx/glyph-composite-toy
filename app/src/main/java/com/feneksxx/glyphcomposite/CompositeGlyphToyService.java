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
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import java.util.Arrays;

import com.nothing.ketchum.Glyph;
import com.nothing.ketchum.GlyphException;
import com.nothing.ketchum.GlyphMatrixFrame;
import com.nothing.ketchum.GlyphMatrixManager;
import com.nothing.ketchum.GlyphMatrixObject;

/** One-screen Glyph Toy: clock, notification dot, charging icon and bottom music visualizer. */
public class CompositeGlyphToyService extends Service {
    private static final String TAG = "GlyphCompositeToy";
    public static final String ACTION_RENDER_TEST_NOTIFICATION =
            "com.feneksxx.glyphcomposite.RENDER_TEST_NOTIFICATION";
    public static final String ACTION_RENDER_NOTIFICATION =
            "com.feneksxx.glyphcomposite.RENDER_NOTIFICATION";
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
    private static final String VISUALIZER_SCREEN_ON = "visualizer_screen_on";
    private static final String VISUALIZER_SCREEN_OFF = "visualizer_screen_off";
    private static final String VISUALIZER_STYLE = "visualizer_style";
    private static final String VISUALIZER_SPEED = "visualizer_speed";
    private static final String NOTIFICATION_STYLE = "notification_style";
    private static final String COMPACT_CHARGING_BATTERY = "compact_charging_battery";
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
    private static final int[] VISUALIZER_BARS = {6, 8, 10, 12, 14, 16, 18};
    private static final int[] VOLUME_Y_LEVELS =
            {12, 11, 13, 10, 14, 9, 15, 8, 16, 7, 17};
    private static final int[][] NOTIFICATION_RADAR_POINTS =
            {{12, 3}, {13, 4}, {12, 5}, {11, 4}};
    private static final int[][] NOTIFICATION_ORBIT_POINTS =
            {{11, 3}, {12, 3}, {13, 3}, {13, 4},
                    {13, 5}, {12, 5}, {11, 5}, {11, 4}};
    private static final int[][] NOTIFICATION_SPIRAL_PATH =
            {{11, 3}, {12, 3}, {13, 3}, {13, 4},
                    {13, 5}, {12, 5}, {11, 5}, {11, 4}};
    private static final int[][] NOTIFICATION_DIAGONAL_POINTS =
            {{11, 3}, {12, 3}, {11, 4}, {13, 3}, {12, 4},
                    {11, 5}, {13, 4}, {12, 5}, {13, 5}};
    private static final int[] NOTIFICATION_DIAGONAL_LEVELS =
            {0, 1, 1, 2, 2, 2, 3, 3, 4};
    private static final int[][] NOTIFICATION_FIGURE_EIGHT_PATH =
            {{11, 3}, {12, 3}, {13, 3}, {13, 4}, {12, 4},
                    {11, 4}, {11, 5}, {12, 5}, {13, 5}, {12, 4}};
    private static final java.text.SimpleDateFormat CLOCK_FORMAT =
            new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());

    private final Paint pixelPaint = new Paint();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Bitmap matrixBitmap;
    private Canvas matrixCanvas;
    // Reused buffers let the service avoid sending an identical 25x25 frame
    // to the Glyph binder when only static content is visible.
    private final int[] currentFramePixels = new int[25 * 25];
    private final int[] submittedFramePixels = new int[25 * 25];
    private boolean hasSubmittedFrame;
    private volatile GlyphMatrixManager manager;
    private AudioManager audioManager;
    private PowerManager powerManager;
    private PowerManager.WakeLock notificationWakeLock;
    private SharedPreferences preferences;
    private float visualizerEnvelope = 0f;
    private long visualizerStartNanos = 0L;
    private long batteryAnimationStartNanos = 0L;
    private int lastMusicVolume = -1;
    private long volumeIndicatorUntil = 0L;
    private float dotVolumeFrom = 0f;
    private float dotVolumeTo = 0f;
    private float dotVolumeLevel = 0f;
    private long dotVolumeAnimationStarted = 0L;
    private static final long DOT_VOLUME_ANIMATION_MS = 140L;
    // The Matrix service remains smooth at this cadence without building a
    // binder queue (20 FPS notifications could visibly stall mid-animation).
    private static final long ANIMATION_FRAME_DELAY_MS = 80L;
    private static final long POWER_SAVE_ANIMATION_FRAME_DELAY_MS = 111L;
    // Android can suspend an idle Glyph Toy between the first and second
    // animation frame. This is a bounded safety window, not a permanent lock.
    private static final long NOTIFICATION_WAKE_LOCK_MS = 12_000L;
    private int batteryLevel;
    private boolean batteryCharging;
    private boolean screenInteractive;
    private boolean audioPlaybackCallbackRegistered;
    private volatile boolean glyphConnected;
    private volatile boolean glyphInitInProgress;
    private volatile boolean managerInitialized;
    private volatile long managerGeneration;
    private volatile boolean serviceDestroyed;
    private boolean volumeReceiverRegistered;
    private boolean testReceiverRegistered;
    private boolean batteryReceiverRegistered;
    private boolean screenReceiverRegistered;
    private boolean timeTickReceiverRegistered;
    private long nextManagerReconnectAt;
    private int lastPixelColor = Integer.MIN_VALUE;
    private long cachedClockMinute = Long.MIN_VALUE;
    private String cachedClockText = "";

    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener =
            (sharedPreferences, key) -> requestRenderNow();

    private final AudioManager.AudioPlaybackCallback audioPlaybackCallback =
            new AudioManager.AudioPlaybackCallback() {
                @Override public void onPlaybackConfigChanged(
                        java.util.List<android.media.AudioPlaybackConfiguration> configs) {
                    // Android tells us when playback starts or stops, so the
                    // idle service does not need to poll music state.
                    requestRenderNow();
                }
            };

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            batteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, batteryLevel);
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
            boolean wasCharging = batteryCharging;
            batteryCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
            if (!wasCharging && batteryCharging) {
                batteryAnimationStartNanos = System.nanoTime();
            } else if (wasCharging && !batteryCharging) {
                batteryAnimationStartNanos = 0L;
            }
            requestRenderNow();
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            screenInteractive = powerManager != null && powerManager.isInteractive();
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())
                    && !glyphConnected && !glyphInitInProgress) {
                // The Glyph manager can lose its binder while the display is
                // waking. Re-request the connection instead of rendering
                // through a stale manager instance.
                initGlyph();
            }
            requestRenderNow();
        }
    };

    private final BroadcastReceiver timeTickReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_TIME_TICK.equals(intent.getAction())) {
                // ACTION_TIME_TICK is delivered by Android once per minute,
                // including while the display is off. Render immediately so
                // the Glyph clock does not wait for the next screen wake-up.
                cachedClockMinute = Long.MIN_VALUE;
                // Re-read the sticky battery state on the same low-frequency
                // tick. This keeps the clock and battery in sync even when
                // Android batches the regular battery broadcast.
                Intent battery = registerReceiver(null,
                        new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (battery != null) batteryReceiver.onReceive(CompositeGlyphToyService.this, battery);
                requestRenderNow();
            }
        }
    };

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

    private final BroadcastReceiver testNotificationReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            keepNotificationAnimationAwake();
            requestRenderNow();
        }
    };

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            if (serviceDestroyed) return;
            long delay = 1500L;
            try {
                if (glyphConnected) {
                    renderFrame();
                    delay = nextFrameDelayMs();
                } else if (!glyphInitInProgress && !managerInitialized
                        && System.currentTimeMillis() >= nextManagerReconnectAt) {
                    nextManagerReconnectAt = System.currentTimeMillis() + 1500L;
                    initGlyph();
                }
            } catch (RuntimeException error) {
                // One bad frame must not terminate the Handler loop and leave
                // the Glyph looking permanently switched off.
                Log.e(TAG, "Frame failed; retrying", error);
                delay = 500L;
            }
            if (!serviceDestroyed) handler.postDelayed(this, delay);
        }
    };

    @Override public IBinder onBind(Intent intent) {
        if (!glyphConnected && !glyphInitInProgress) initGlyph();
        return null;
    }

    @Override public void onCreate() {
        super.onCreate();
        serviceDestroyed = false;
        pixelPaint.setStyle(Paint.Style.FILL);
        pixelPaint.setAntiAlias(false);
        registerReceiver(volumeReceiver,
                new android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION"));
        volumeReceiverRegistered = true;
        IntentFilter renderFilter = new IntentFilter(ACTION_RENDER_TEST_NOTIFICATION);
        renderFilter.addAction(ACTION_RENDER_NOTIFICATION);
        registerReceiver(testNotificationReceiver, renderFilter, Context.RECEIVER_NOT_EXPORTED);
        testReceiverRegistered = true;
        Intent initialBattery = registerReceiver(batteryReceiver,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        batteryReceiverRegistered = true;
        if (initialBattery != null) batteryReceiver.onReceive(this, initialBattery);
        IntentFilter screenFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver, screenFilter);
        screenReceiverRegistered = true;
        registerReceiver(timeTickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
        timeTickReceiverRegistered = true;
    }

    @Override public void onDestroy() {
        serviceDestroyed = true;
        handler.removeCallbacks(loop);
        glyphConnected = false;
        hasSubmittedFrame = false;
        if (volumeReceiverRegistered) {
            unregisterReceiver(volumeReceiver);
            volumeReceiverRegistered = false;
        }
        if (testReceiverRegistered) {
            unregisterReceiver(testNotificationReceiver);
            testReceiverRegistered = false;
        }
        if (batteryReceiverRegistered) {
            unregisterReceiver(batteryReceiver);
            batteryReceiverRegistered = false;
        }
        if (screenReceiverRegistered) {
            unregisterReceiver(screenReceiver);
            screenReceiverRegistered = false;
        }
        if (timeTickReceiverRegistered) {
            unregisterReceiver(timeTickReceiver);
            timeTickReceiverRegistered = false;
        }
        if (preferences != null) preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
        if (audioManager != null && audioPlaybackCallbackRegistered && Build.VERSION.SDK_INT >= 26) {
            audioManager.unregisterAudioPlaybackCallback(audioPlaybackCallback);
        }
        releaseNotificationWakeLock();
        managerGeneration++;
        if (manager != null && managerInitialized) {
            try {
                manager.unInit();
            } catch (RuntimeException ignored) {
                // The system Glyph service may already be gone during teardown.
            }
        }
        managerInitialized = false;
        manager = null;
        if (matrixBitmap != null && !matrixBitmap.isRecycled()) matrixBitmap.recycle();
        super.onDestroy();
    }

    @Override public boolean onUnbind(Intent intent) {
        handler.removeCallbacks(loop);
        glyphConnected = false;
        hasSubmittedFrame = false;
        glyphInitInProgress = false;
        managerGeneration++;
        if (manager != null && managerInitialized) {
            try {
                manager.unInit();
            } catch (RuntimeException ignored) {
                // Safe teardown when Nothing's binder has already disconnected.
            }
        }
        managerInitialized = false;
        manager = null;
        return false;
    }

    private void initGlyph() {
        if (glyphInitInProgress || managerInitialized) return;
        glyphInitInProgress = true;
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
        if (preferences.getInt(SETTINGS_VERSION, 0) < 1) {
            preferences.edit().putBoolean(LARGE_CLOCK, true)
                    .putInt(SETTINGS_VERSION, 1).apply();
        }
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        screenInteractive = powerManager != null && powerManager.isInteractive();
        if (audioManager != null && !audioPlaybackCallbackRegistered && Build.VERSION.SDK_INT >= 26) {
            audioManager.registerAudioPlaybackCallback(audioPlaybackCallback, handler);
            audioPlaybackCallbackRegistered = true;
        }
        final GlyphMatrixManager connection = GlyphMatrixManager.getInstance(getApplicationContext());
        final long generation = ++managerGeneration;
        manager = connection;
        managerInitialized = true;
        connection.init(new GlyphMatrixManager.Callback() {
            @Override public void onServiceConnected(ComponentName name) {
                handler.post(() -> {
                    if (serviceDestroyed || manager != connection
                            || generation != managerGeneration) return;
                    try {
                        connection.register(Glyph.DEVICE_23112);
                    } catch (RuntimeException ignored) {
                        glyphInitInProgress = false;
                        glyphConnected = false;
                        managerInitialized = false;
                        if (!serviceDestroyed) handler.post(loop);
                        return;
                    }
                    glyphInitInProgress = false;
                    glyphConnected = true;
                    hasSubmittedFrame = false;
                    // Force a fresh clock value on the first frame after
                    // startup/reconnect instead of reusing an old cached
                    // minute from before the Glyph service was rebound.
                    cachedClockMinute = Long.MIN_VALUE;
                    handler.removeCallbacks(loop);
                    handler.post(loop);
                });
            }
            @Override public void onServiceDisconnected(ComponentName name) {
                handler.post(() -> {
                    if (generation != managerGeneration) return;
                    glyphInitInProgress = false;
                    glyphConnected = false;
                    managerInitialized = false;
                    hasSubmittedFrame = false;
                    handler.removeCallbacks(loop);
                    if (!serviceDestroyed) handler.post(loop);
                });
            }
        });
    }

    private void requestRenderNow() {
        if (manager == null || !glyphConnected) return;
        handler.removeCallbacks(loop);
        handler.post(loop);
    }

    /** Keeps the CPU awake for one complete notification animation only. */
    private void keepNotificationAnimationAwake() {
        if (powerManager == null) {
            powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        }
        if (powerManager == null) return;
        try {
            if (notificationWakeLock == null) {
                notificationWakeLock = powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK, TAG + ":notification-animation");
                notificationWakeLock.setReferenceCounted(false);
            }
            // Timed acquisition is a failsafe even if service teardown is delayed.
            notificationWakeLock.acquire(NOTIFICATION_WAKE_LOCK_MS);
        } catch (RuntimeException ignored) {
            // Devices which reject the lock still use the normal Handler path.
        }
    }

    private void releaseNotificationWakeLock() {
        try {
            if (notificationWakeLock != null && notificationWakeLock.isHeld()) {
                notificationWakeLock.release();
            }
        } catch (RuntimeException ignored) {
            // The timed acquisition may have released it already.
        }
        notificationWakeLock = null;
    }

    private void renderFrame() {
        if (manager == null || !glyphConnected) return;
        if (matrixBitmap == null || matrixBitmap.isRecycled()) {
            matrixBitmap = Bitmap.createBitmap(25, 25, Bitmap.Config.ARGB_8888);
            matrixCanvas = new Canvas(matrixBitmap);
        }
        Canvas canvas = matrixCanvas;
        canvas.drawColor(Color.BLACK);

        // Run every frame so the visualizer can also fade out after music stops.
        drawMusicVisualizer(canvas);
        int level = batteryLevel;
        boolean charging = batteryCharging;
        boolean largeClockEnabled = preferences == null
                || preferences.getBoolean(LARGE_CLOCK, true);
        boolean compactChargingBattery = preferences != null
                && preferences.getBoolean(COMPACT_CHARGING_BATTERY, true);
        drawClock(canvas, charging && !compactChargingBattery);
        if (charging && compactChargingBattery) drawBatteryLevelLine(canvas, level, true);
        else if (charging || !largeClockEnabled) drawBattery(canvas, level, charging);
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
            int notificationStyle = preferences == null ? 0
                    : preferences.getInt(NOTIFICATION_STYLE, 0);
            drawNotificationFlash(canvas,
                    Math.round(brightness(NOTIFICATION_FLASH_BRIGHTNESS) * flashAlpha),
                    notificationStyle, GlyphNotificationListener.notificationEndingProgress());
        }
        float dotAlpha = GlyphNotificationListener.notificationDotAlpha();
        if (dotAlpha > 0f) {
            drawPixel(canvas, 12, 4, Math.round(brightness(NOTIFICATION_BRIGHTNESS) * dotAlpha));
        }

        if (!frameChangedSinceLastSubmission()) return;
        try {
            GlyphMatrixObject object = new GlyphMatrixObject.Builder()
                    .setImageSource(matrixBitmap)
                    // 255 is the SDK's full object brightness. Component
                    // sliders are already combined into each pixel's RGB
                    // intensity, so 180 here imposed an unintended global
                    // ~70% ceiling even when every slider was at 100%.
                    .setBrightness(255)
                    .build();
            GlyphMatrixFrame frame = new GlyphMatrixFrame.Builder().addTop(object).build(this);
            if (glyphConnected && manager != null) {
                manager.setMatrixFrame(frame.render());
                System.arraycopy(currentFramePixels, 0, submittedFramePixels, 0,
                        currentFramePixels.length);
                hasSubmittedFrame = true;
            }
        } catch (GlyphException | RuntimeException ignored) {
            // A frame may be rejected while Nothing's service is reconnecting.
        }
    }

    private boolean frameChangedSinceLastSubmission() {
        matrixBitmap.getPixels(currentFramePixels, 0, 25, 0, 0, 25, 25);
        return !hasSubmittedFrame
                || !Arrays.equals(currentFramePixels, submittedFramePixels);
    }

    /** Adaptive refresh: static content wakes only for the next minute tick. */
    private long nextFrameDelayMs() {
        boolean visualizerEnabled = preferences == null
                || preferences.getBoolean(VISUALIZER_ENABLED, true);
        boolean powerSave = powerManager != null && powerManager.isPowerSaveMode();
        if (visualizerEnabled && isVisualizerActive()) {
            return powerSave ? POWER_SAVE_ANIMATION_FRAME_DELAY_MS
                    : ANIMATION_FRAME_DELAY_MS;
        }
        if (GlyphNotificationListener.shouldShowNotificationFlash()) {
            return powerSave ? POWER_SAVE_ANIMATION_FRAME_DELAY_MS
                    : ANIMATION_FRAME_DELAY_MS;
        }
        if (System.currentTimeMillis() < volumeIndicatorUntil) {
            return powerSave ? 160L : (screenInteractive ? 80L : 120L);
        }

        if (batteryCharging) {
            // Charging remains animated, but a sleeping screen needs only a
            // very occasional update for its deliberately gentle animation.
            return powerSave ? 650L : (screenInteractive ? 250L : 900L);
        }

        long untilNextMinute = 60_000L - (System.currentTimeMillis() % 60_000L);
        return Math.max(1_000L, untilNextMinute + 30L);
    }

    private void drawMusicVisualizer(Canvas canvas) {
        if (preferences != null && !preferences.getBoolean(VISUALIZER_ENABLED, true)) {
            visualizerEnvelope = 0f;
            visualizerStartNanos = 0L;
            return;
        }
        boolean playing = isVisualizerActive();
        if (!playing) {
            visualizerEnvelope = 0f;
            visualizerStartNanos = 0L;
            return;
        }
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
        for (int i = 0; i < VISUALIZER_BARS.length; i++) {
            int x = VISUALIZER_BARS[i];
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

    private void drawNotificationFlash(Canvas canvas, int intensity, int style,
            float endingProgress) {
        long now = System.currentTimeMillis();
        float cycle = (now % 1300L) / 1300f;
        if (style == 3) {
            drawNotificationSpiral(canvas, intensity, cycle, endingProgress);
            return;
        }
        if (style == 4) {
            drawNotificationDiagonal(canvas, intensity, cycle);
            return;
        }
        if (style == 5) {
            drawNotificationCheckerboard(canvas, intensity, cycle, endingProgress > 0f);
            return;
        }
        if (style == 6) {
            drawNotificationFigureEight(canvas, intensity, cycle, endingProgress > 0f);
            return;
        }
        if (style == 1) {
            drawNotificationRadar(canvas, intensity, cycle);
            return;
        }
        if (style == 2) {
            drawNotificationOrbit(canvas, intensity, cycle);
            return;
        }
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
                float amount = ring * 0.78f;
                if (dx == 0 && dy == 0) amount = Math.max(amount, 0.72f * centerPulse);
                int pixelIntensity = Math.min(255, Math.round(intensity * amount));
                drawPixel(canvas, 12 + dx, 4 + dy, pixelIntensity);
            }
        }
    }

    private void drawNotificationRadar(Canvas canvas, int intensity, float cycle) {
        // A clean 3x3 expanding ring: the centre remains visible while the
        // emphasis travels through the four cardinal pixels.
        float phase = cycle * (float) (Math.PI * 2.0);
        float pulse = 0.55f + 0.45f * (0.5f + 0.5f * (float) Math.sin(phase));
        drawPixel(canvas, 12, 4, Math.round(intensity * pulse));
        for (int i = 0; i < NOTIFICATION_RADAR_POINTS.length; i++) {
            float local = 0.5f + 0.5f * (float) Math.sin(phase - i * 1.57f);
            drawPixel(canvas, NOTIFICATION_RADAR_POINTS[i][0],
                    NOTIFICATION_RADAR_POINTS[i][1],
                    Math.round(intensity * (0.72f * Math.max(0f, local))));
        }
    }

    private void drawNotificationOrbit(Canvas canvas, int intensity, float cycle) {
        // One bright point makes a compact clockwise orbit with a soft tail.
        float position = cycle * 8f;
        for (int i = 0; i < NOTIFICATION_ORBIT_POINTS.length; i++) {
            float distance = Math.abs(position - i);
            distance = Math.min(distance, 8f - distance);
            float amount = 0.90f * (float) Math.exp(-distance * distance / 1.8f);
            drawPixel(canvas, NOTIFICATION_ORBIT_POINTS[i][0],
                    NOTIFICATION_ORBIT_POINTS[i][1], Math.round(intensity * amount));
        }
        drawPixel(canvas, 12, 4, Math.round(intensity * 0.32f));
    }

    private void drawNotificationSpiral(Canvas canvas, int intensity, float cycle,
            float endingProgress) {
        float position = cycle * NOTIFICATION_SPIRAL_PATH.length;
        float outerWeight = 1f - Math.max(0f, Math.min(1f, endingProgress));
        for (int i = 0; i < NOTIFICATION_SPIRAL_PATH.length; i++) {
            float distance = Math.abs(position - i);
            distance = Math.min(distance, NOTIFICATION_SPIRAL_PATH.length - distance);
            float amount = outerWeight * (float) Math.exp(-distance * distance / 1.35f);
            drawPixel(canvas, NOTIFICATION_SPIRAL_PATH[i][0],
                    NOTIFICATION_SPIRAL_PATH[i][1], Math.round(intensity * amount));
        }
        if (endingProgress > 0f) {
            float centerWeight = endingProgress * endingProgress
                    * (3f - 2f * endingProgress);
            drawPixel(canvas, 12, 4, Math.round(intensity * centerWeight));
        }
    }

    private void drawNotificationDiagonal(Canvas canvas, int intensity, float cycle) {
        // Smooth ping-pong travel: 0 -> 4 -> 0. The cosine easing reaches
        // both ends with zero velocity, so the wave reverses direction
        // naturally instead of jumping when the cycle restarts.
        float position = 2f - 2f * (float) Math.cos(cycle * Math.PI * 2.0);
        for (int i = 0; i < NOTIFICATION_DIAGONAL_POINTS.length; i++) {
            float distance = Math.abs(position - NOTIFICATION_DIAGONAL_LEVELS[i]);
            float amount = 0.96f
                    * (float) Math.exp(-distance * distance / 0.70f);
            drawPixel(canvas, NOTIFICATION_DIAGONAL_POINTS[i][0],
                    NOTIFICATION_DIAGONAL_POINTS[i][1], Math.round(intensity * amount));
        }
    }

    private void drawNotificationCheckerboard(Canvas canvas, int intensity, float cycle,
            boolean ending) {
        // Centre + corners and the four sides alternate at a fixed rhythm.
        if (ending) {
            int corner = Math.round(intensity * 0.55f);
            drawPixel(canvas, 12, 4, intensity);
            drawPixel(canvas, 11, 3, corner);
            drawPixel(canvas, 13, 3, corner);
            drawPixel(canvas, 11, 5, corner);
            drawPixel(canvas, 13, 5, corner);
            return;
        }
        boolean primary = cycle < 0.5f;
        int bright = intensity;
        int dim = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                boolean primaryCell = (dx + dy) % 2 == 0;
                drawPixel(canvas, 12 + dx, 4 + dy,
                        primaryCell == primary ? bright : dim);
            }
        }
    }

    private void drawNotificationFigureEight(Canvas canvas, int intensity, float cycle,
            boolean ending) {
        if (ending) {
            drawPixel(canvas, 12, 4, Math.min(255, Math.round(intensity * 1.25f)));
            return;
        }
        // The centre is visited twice every loop: upper loop -> centre ->
        // lower loop -> centre. A soft tail keeps the route continuous.
        float position = cycle * NOTIFICATION_FIGURE_EIGHT_PATH.length;
        for (int i = 0; i < NOTIFICATION_FIGURE_EIGHT_PATH.length; i++) {
            float distance = Math.abs(position - i);
            distance = Math.min(distance, NOTIFICATION_FIGURE_EIGHT_PATH.length - distance);
            float amount = 0.94f
                    * (float) Math.exp(-distance * distance / 1.05f);
            drawPixel(canvas, NOTIFICATION_FIGURE_EIGHT_PATH[i][0],
                    NOTIFICATION_FIGURE_EIGHT_PATH[i][1], Math.round(intensity * amount));
        }
    }

    private boolean isVisualizerActive() {
        boolean enabled = preferences == null
                || preferences.getBoolean(VISUALIZER_ENABLED, true);
        boolean screenAllowed = screenInteractive
                ? preferences == null || preferences.getBoolean(VISUALIZER_SCREEN_ON, true)
                : preferences == null || preferences.getBoolean(VISUALIZER_SCREEN_OFF, true);
        return enabled && screenAllowed && audioManager != null && isMusicPlayingNow();
    }

    private boolean isMusicPlayingNow() {
        if (!audioManager.isMusicActive()) return false;
        if (screenInteractive || Build.VERSION.SDK_INT < 26) return true;
        try {
            java.util.List<android.media.AudioPlaybackConfiguration> active =
                    audioManager.getActivePlaybackConfigurations();
            return active != null && !active.isEmpty();
        } catch (RuntimeException ignored) {
            // Some system builds restrict this list; isMusicActive() remains
            // the safe fallback rather than allowing the visualizer to crash.
            return true;
        }
    }

    private void drawClock(Canvas canvas, boolean charging) {
        long minute = System.currentTimeMillis() / 60_000L;
        if (minute != cachedClockMinute) {
            cachedClockMinute = minute;
            cachedClockText = CLOCK_FORMAT.format(new java.util.Date(minute * 60_000L));
        }
        String time = cachedClockText;
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
        if (charging && batteryAnimationStartNanos == 0L) {
            batteryAnimationStartNanos = System.nanoTime();
        }
        double seconds = charging
                ? (System.nanoTime() - batteryAnimationStartNanos) / 1_000_000_000.0
                : 0.0;
        // During normal discharge the battery outline is static. A full
        // battery uses one calm breathing brightness instead of the moving
        // highlight used during ordinary charging.
        int outline = intensity;
        boolean full = charging && level >= 100;
        if (full) {
            outline = fullChargePulseIntensity(intensity, seconds, 0.68f);
        } else if (charging) {
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
        if (full) {
            // Full charge is deliberately still: all pixels breathe together
            // so it cannot be confused with the travelling charge highlight.
            int fullIntensity = fullChargePulseIntensity(intensity, seconds, 0.76f);
            for (int x = left + 1; x < right; x++) {
                for (int y = 15; y <= 17; y++) {
                    drawPixel(canvas, x, y, fullIntensity);
                }
            }
            int contact = fullChargePulseIntensity(intensity, seconds, 0.84f);
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
        drawBatteryLevelLine(canvas, level, false);
    }

    private void drawBatteryLevelLine(Canvas canvas, int level, boolean charging) {
        int intensity = brightness(BATTERY_BRIGHTNESS);
        // Keep the unfilled part proportional to the already combined
        // component + master brightness. A fixed floor here made the dim
        // pixels ignore the master brightness slider at low settings.
        int dim = Math.round(intensity * 0.18f);
        final int lineLeft = 8;
        final int lineWidth = 9;
        float pixelHeight = Math.max(0f, Math.min(100, level)) / 100f * lineWidth;
        int filled = (int) Math.floor(pixelHeight);
        float fractional = pixelHeight - filled;
        for (int x = 0; x < lineWidth; x++) {
            int pixelIntensity = x < filled ? intensity : dim;
            if (x == filled && fractional > 0.001f) {
                // The leading pixel represents the fractional part of the
                // battery percentage instead of jumping by a whole LED.
                pixelIntensity = Math.max(dim, Math.round(intensity * fractional));
            }
            drawPixel(canvas, lineLeft + x, 18, pixelIntensity);
        }
        if (charging && level >= 100) {
            if (batteryAnimationStartNanos == 0L) {
                batteryAnimationStartNanos = System.nanoTime();
            }
            double seconds = (System.nanoTime() - batteryAnimationStartNanos)
                    / 1_000_000_000.0;
            // No travelling pixels at 100%: the complete compact bar simply
            // breathes as one calm indicator of a completed charge.
            int fullIntensity = fullChargePulseIntensity(intensity, seconds, 0.72f);
            for (int x = 0; x < lineWidth; x++) {
                drawPixel(canvas, lineLeft + x, 18, fullIntensity);
            }
            return;
        }
        if (charging && filled > 0) {
            if (batteryAnimationStartNanos == 0L) batteryAnimationStartNanos = System.nanoTime();
            double seconds = (System.nanoTime() - batteryAnimationStartNanos) / 1_000_000_000.0;
            int shine = (int) Math.floor(seconds * 3.0) % filled;
            drawPixel(canvas, lineLeft + shine, 18, Math.min(255, intensity + 80));
        } else if (!charging) {
            batteryAnimationStartNanos = 0L;
        }
    }

    /**
     * A low-frequency cosine gives the complete-charge state a soft inhale /
     * exhale without creating a directional sweep. {@code minimum} keeps the
     * symbol legible throughout the dimmest part of the pulse.
     */
    private int fullChargePulseIntensity(int intensity, double seconds, float minimum) {
        float phase = 0.5f - 0.5f
                * (float) Math.cos(seconds * Math.PI * 2.0 / 2.4);
        return Math.max(1, Math.round(intensity * (minimum + (1f - minimum) * phase)));
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
            // Do not round the seven-pixel bar too early. With sixteen
            // hardware volume steps, rounding made some button presses look
            // like they did nothing. A fractional leading pixel gives clear
            // feedback: first it appears at half brightness, then it becomes
            // full on the next step.
            float pixelHeight = Math.max(0f, Math.min(1f, animatedLevel)) * 7f;
            int filled = (int) Math.floor(pixelHeight);
            float fractional = pixelHeight - filled;
            for (int i = 0; i < 7; i++) {
                int y = 15 - i;
                int intensity = i < filled ? bright : 0;
                if (i == filled && fractional > 0.01f) {
                    float stepBrightness = fractional >= 0.5f ? 1f : 0.5f;
                    intensity = Math.round(bright * stepBrightness);
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
            for (int y : VOLUME_Y_LEVELS) {
                drawPixel(canvas, 0, y, edgeIntensity);
                drawPixel(canvas, 24, y, edgeIntensity);
            }
            return;
        }

        for (int i = 0; i < VOLUME_Y_LEVELS.length; i++) {
            float fill = Math.max(0f, Math.min(1f, level - i));
            int intensity = Math.round(dim + (bright - dim) * fill);
            int y = VOLUME_Y_LEVELS[i];
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
        {6,8,8,6,9,9,6}, {6,1,1,0,1,1,0},
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

    private static final int[][] DOT_DIGITS = {
        {2,5,5,5,2}, {2,2,2,2,2}, {6,1,2,4,3}, {6,1,2,1,6}, {5,5,7,1,1},
        {7,4,6,1,6}, {2,4,6,5,2}, {7,1,2,2,2}, {2,5,2,5,2}, {2,5,3,1,2}
    };

    private void drawPixel(Canvas canvas, int x, int y) {
        drawPixel(canvas, x, y, brightness(CLOCK_BRIGHTNESS));
    }

    private void drawPixel(Canvas canvas, int x, int y, int intensity) {
        if (!Phone3LedLayout.isValid(x, y)) return;
        int color = Color.rgb(intensity, intensity, intensity);
        if (color != lastPixelColor) {
            pixelPaint.setColor(color);
            lastPixelColor = color;
        }
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
