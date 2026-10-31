package com.qdmod;

// MediaListenerService.java - lets the Home screen see which song is playing.
// Android only shares "what is playing" with apps that have a notification listener
// that you switched on yourself (Settings > Notification access > QDLink).
// This class is empty on purpose: it never reads or stores your notifications.

import android.service.notification.NotificationListenerService;

public class MediaListenerService extends NotificationListenerService {
}
