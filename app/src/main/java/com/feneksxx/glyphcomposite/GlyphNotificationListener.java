package com.feneksxx.glyphcomposite;

import android.app.Notification;
import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.Set;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Flashes a new notification, then keeps a quiet dot until that notification is removed. */
public class GlyphNotificationListener extends NotificationListenerService {
    private static volatile long latestNotificationAt = 0L;
    private static volatile long notificationExitAt = 0L;
    private static volatile long testNotificationAt = 0L;
    private static volatile String latestNotificationKey;
    private static final Set<String> ACTIVE_NOTIFICATION_KEYS =
            ConcurrentHashMap.newKeySet();
    private static final Map<String, Long> NOTIFICATION_TIMES =
            new ConcurrentHashMap<>();
    private static volatile GlyphNotificationListener instance;
    private static volatile Set<String> allowedPackages = Collections.emptySet();
    private static volatile boolean notificationsDisabled;
    private static final long FLASH_WINDOW_MS = 10000L;
    private static final long ENTER_FADE_MS = 280L;
    private static final long TRANSITION_MS = 900L;
    private static final long EXIT_FADE_MS = 700L;

    @Override public void onListenerConnected() {
        instance = this;
        Set<String> saved = getSharedPreferences("glyph_composite", MODE_PRIVATE)
                .getStringSet("notification_packages", Collections.emptySet());
        notificationsDisabled = saved.contains("__none__");
        HashSet<String> allowed = new HashSet<>(saved);
        allowed.remove("__none__");
        allowedPackages = Collections.unmodifiableSet(allowed);
        refreshActiveNotifications();
    }

    private void refreshActiveNotifications() {
        ACTIVE_NOTIFICATION_KEYS.clear();
        NOTIFICATION_TIMES.clear();
        latestNotificationAt = 0L;
        latestNotificationKey = null;
        notificationExitAt = 0L;
        StatusBarNotification[] active = getActiveNotifications();
        if (active == null) return;
        for (StatusBarNotification sbn : active) {
            if (isRelevantNotification(sbn)) {
                ACTIVE_NOTIFICATION_KEYS.add(sbn.getKey());
                NOTIFICATION_TIMES.put(sbn.getKey(), 0L);
            }
        }
        requestGlyphRender();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (!isRelevantNotification(sbn)) return;
        ACTIVE_NOTIFICATION_KEYS.add(sbn.getKey());
        long now = System.currentTimeMillis();
        NOTIFICATION_TIMES.put(sbn.getKey(), now);
        latestNotificationAt = now;
        notificationExitAt = 0L;
        latestNotificationKey = sbn.getKey();
        requestGlyphRender();
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        String removedKey = sbn.getKey();
        ACTIVE_NOTIFICATION_KEYS.remove(removedKey);
        NOTIFICATION_TIMES.remove(removedKey);
        if (ACTIVE_NOTIFICATION_KEYS.isEmpty()) {
            latestNotificationKey = null;
            latestNotificationAt = 0L;
            notificationExitAt = System.currentTimeMillis();
        } else if (removedKey.equals(latestNotificationKey)) {
            String replacement = newestActiveKey();
            latestNotificationKey = replacement;
            latestNotificationAt = replacement == null
                    ? 0L : NOTIFICATION_TIMES.getOrDefault(replacement, 0L);
            notificationExitAt = 0L;
        }
        requestGlyphRender();
    }

    private static String newestActiveKey() {
        String newestKey = null;
        long newestTime = Long.MIN_VALUE;
        for (String key : ACTIVE_NOTIFICATION_KEYS) {
            long time = NOTIFICATION_TIMES.getOrDefault(key, 0L);
            if (time > newestTime) {
                newestTime = time;
                newestKey = key;
            }
        }
        return newestKey;
    }

    private boolean isRelevantNotification(StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();
        if (getPackageName().equals(sbn.getPackageName())) return false;
        if (notificationsDisabled) return false;
        if (!allowedPackages.isEmpty() && !allowedPackages.contains(sbn.getPackageName())) return false;
        if ((notification.flags & Notification.FLAG_ONGOING_EVENT) != 0) return false;
        if (Notification.CATEGORY_TRANSPORT.equals(notification.category)) return false;
        if (notification.extras != null
                && notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return false;
        return true;
    }

    static void setAllowedPackages(Set<String> packages) {
        notificationsDisabled = packages.contains("__none__");
        HashSet<String> allowed = new HashSet<>(packages);
        allowed.remove("__none__");
        allowedPackages = Collections.unmodifiableSet(allowed);
        ACTIVE_NOTIFICATION_KEYS.clear();
        NOTIFICATION_TIMES.clear();
        latestNotificationAt = 0L;
        latestNotificationKey = null;
        notificationExitAt = System.currentTimeMillis();
        GlyphNotificationListener service = instance;
        if (service != null) service.refreshActiveNotifications();
    }

    /** Plays the notification animation without consulting app filters. */
    static void showTestNotification() {
        testNotificationAt = System.currentTimeMillis();
        notificationExitAt = 0L;
    }

    @Override public void onListenerDisconnected() {
        if (instance == this) instance = null;
        ACTIVE_NOTIFICATION_KEYS.clear();
        NOTIFICATION_TIMES.clear();
        latestNotificationAt = 0L;
        latestNotificationKey = null;
        notificationExitAt = System.currentTimeMillis();
        super.onListenerDisconnected();
    }

    @Override public void onDestroy() {
        if (instance == this) instance = null;
        ACTIVE_NOTIFICATION_KEYS.clear();
        NOTIFICATION_TIMES.clear();
        super.onDestroy();
    }

    private void requestGlyphRender() {
        sendBroadcast(new Intent(CompositeGlyphToyService.ACTION_RENDER_NOTIFICATION)
                .setPackage(getPackageName()));
    }

    static boolean shouldShowNotificationFlash() {
        return notificationFlashAlpha() > 0f;
    }

    static boolean shouldShowNotificationDot() {
        return notificationDotAlpha() > 0f;
    }

    static float notificationFlashAlpha() {
        long now = System.currentTimeMillis();
        if (testNotificationAt != 0L) {
            long elapsed = now - testNotificationAt;
            if (elapsed < FLASH_WINDOW_MS + TRANSITION_MS) {
                return flashAlphaForElapsed(elapsed);
            }
            testNotificationAt = 0L;
        }
        if (ACTIVE_NOTIFICATION_KEYS.isEmpty()) {
            if (notificationExitAt == 0L) return 0f;
            float exit = (now - notificationExitAt) / (float) EXIT_FADE_MS;
            return exit >= 1f ? 0f : Math.max(0f, 1f - exit);
        }
        if (latestNotificationAt == 0L) return 0f;
        return flashAlphaForElapsed(now - latestNotificationAt);
    }

    static float notificationEndingProgress() {
        long now = System.currentTimeMillis();
        if (notificationExitAt != 0L) {
            return Math.max(0f, Math.min(1f,
                    (now - notificationExitAt) / (float) EXIT_FADE_MS));
        }
        long started = testNotificationAt != 0L ? testNotificationAt : latestNotificationAt;
        if (started == 0L) return 0f;
        return Math.max(0f, Math.min(1f,
                (now - started - FLASH_WINDOW_MS) / (float) TRANSITION_MS));
    }

    private static float flashAlphaForElapsed(long elapsed) {
        if (elapsed < 0L || elapsed >= FLASH_WINDOW_MS + TRANSITION_MS) return 0f;
        if (elapsed < ENTER_FADE_MS) return elapsed / (float) ENTER_FADE_MS;
        if (elapsed < FLASH_WINDOW_MS) return 1f;
        return 1f - (elapsed - FLASH_WINDOW_MS) / (float) TRANSITION_MS;
    }

    static float notificationDotAlpha() {
        if (testNotificationAt != 0L
                && flashAlphaForElapsed(System.currentTimeMillis() - testNotificationAt) > 0f) {
            return 0f;
        }
        if (ACTIVE_NOTIFICATION_KEYS.isEmpty()) return 0f;
        if (latestNotificationAt == 0L) return 1f;
        long elapsed = System.currentTimeMillis() - latestNotificationAt;
        if (elapsed < FLASH_WINDOW_MS) return 0f;
        if (elapsed < FLASH_WINDOW_MS + TRANSITION_MS) {
            return (elapsed - FLASH_WINDOW_MS) / (float) TRANSITION_MS;
        }
        return 1f;
    }

}
