package com.sergi.glyphcomposite;

import android.service.notification.NotificationListenerService;

public class GlyphNotificationListener extends NotificationListenerService {
    static volatile long lastNotificationAt;
    @Override public void onNotificationPosted(android.service.notification.StatusBarNotification sbn) { lastNotificationAt = System.currentTimeMillis(); }
}
