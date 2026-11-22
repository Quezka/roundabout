package com.andrerinas.openheadunit.roundabout

import android.annotation.TargetApi
import android.os.Build
import android.service.notification.NotificationListenerService

// empty <- exists only to unlock `MediaSessionManager.getActiveSessions`
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class MediaListener : NotificationListenerService()
