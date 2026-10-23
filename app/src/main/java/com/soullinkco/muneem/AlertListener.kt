package com.soullinkco.muneem

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** UPI / bank app aur Gmail/Outlook ke notifications padhta hai. */
class AlertListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg == packageName || pkg in SMS_APPS) return // SMS ko SmsReceiver handle karta hai
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val kind = if (pkg in EMAIL_APPS) "email" else "notif"
        Inbox.add(applicationContext, kind, "$title. $body", sbn.postTime)
    }

    companion object {
        private val SMS_APPS = setOf(
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.samsung.android.messaging",
            "com.miui.mms",
            "com.oneplus.mms"
        )
        private val EMAIL_APPS = setOf(
            "com.google.android.gm",
            "com.microsoft.office.outlook",
            "com.yahoo.mobile.client.android.mail",
            "ch.protonmail.android"
        )
    }
}
