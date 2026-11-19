package io.github.quezka.roundabout.service

import android.service.notification.NotificationListenerService

// empty <- exists only to unlock MediaSessionManager.getActiveSessions
class MediaListener : NotificationListenerService()
