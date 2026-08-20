package com.feneksxx.glyphcomposite;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Flashes a new notification, then keeps a quiet dot until that notification is removed. */
public class GlyphNotificationListener extends NotificationListenerService {
    private static volatile long latestNotificationAt = 0L;
    private static volatile String latestNotificationKey;
    private static final Set<String> ACTIVE_NOTIFICATION_KEYS =
            ConcurrentHashMap.newKeySet();
    private static final long FLASH_WINDOW_MS = 10000L;

    @Override public void onListenerConnected() {
        StatusBarNotification[] active = getActiveNotifications();
        if (active == null) return;
        for (StatusBarNotification sbn : active) {
            if (isRelevantNotification(sbn)) ACTIVE_NOTIFICATION_KEYS.add(sbn.getKey());
        }
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (!isRelevantNotification(sbn)) return;
        ACTIVE_NOTIFICATION_KEYS.add(sbn.getKey());
        latestNotificationAt = System.currentTimeMillis();
        latestNotificationKey = sbn.getKey();
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        ACTIVE_NOTIFICATION_KEYS.remove(sbn.getKey());
        if (sbn.getKey().equals(latestNotificationKey)) {
            latestNotificationKey = null;
            latestNotificationAt = 0L;
        }
    }

    private boolean isRelevantNotification(StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();
        if (getPackageName().equals(sbn.getPackageName())) return false;
        if ((notification.flags & Notification.FLAG_ONGOING_EVENT) != 0) return false;
        if (Notification.CATEGORY_TRANSPORT.equals(notification.category)) return false;
        if (notification.extras != null
                && notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return false;
        return true;
    }

    static boolean shouldShowNotificationFlash() {
        long elapsed = System.currentTimeMillis() - latestNotificationAt;
        if (ACTIVE_NOTIFICATION_KEYS.isEmpty() || elapsed < 0 || elapsed >= FLASH_WINDOW_MS) return false;
        return true;
    }

    static boolean shouldShowNotificationDot() {
        long elapsed = System.currentTimeMillis() - latestNotificationAt;
        return !ACTIVE_NOTIFICATION_KEYS.isEmpty() && elapsed >= FLASH_WINDOW_MS;
    }

}
