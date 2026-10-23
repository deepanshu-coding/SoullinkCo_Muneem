package com.soullinkco.muneem

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/** Naya SMS aate hi queue me daalta hai (app band ho tab bhi). */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        msgs.groupBy { it.originatingAddress ?: "" }.forEach { (_, parts) ->
            val body = parts.joinToString("") { it.messageBody ?: "" }
            Inbox.add(context, "sms", body, parts.first().timestampMillis)
        }
    }
}
