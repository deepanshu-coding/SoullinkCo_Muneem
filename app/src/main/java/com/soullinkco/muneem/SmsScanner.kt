package com.soullinkco.muneem

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat

/** Inbox me se (pichhle 7 din / last scan ke baad ke) SMS padhta hai. */
object SmsScanner {
    private const val PREFS = "scan"
    private const val KEY = "last"
    private const val FIRST_SCAN_DAYS = 7L

    fun hasPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    fun scan(ctx: Context) {
        if (!hasPermission(ctx)) return
        val prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val since = prefs.getLong(KEY, now - FIRST_SCAN_DAYS * 24 * 3600 * 1000)
        try {
            ctx.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} > ?",
                arrayOf(since.toString()),
                "${Telephony.Sms.DATE} ASC"
            )?.use { c ->
                val bi = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val di = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                while (c.moveToNext()) {
                    Inbox.add(ctx, "sms", c.getString(bi) ?: "", c.getLong(di))
                }
            }
            prefs.edit().putLong(KEY, now).apply()
        } catch (_: Exception) {
        }
    }
}
