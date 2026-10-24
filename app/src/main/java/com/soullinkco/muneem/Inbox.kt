package com.soullinkco.muneem

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Phone se aaye SMS / notification / email alerts ki queue (app band ho tab bhi save rehti hai). */
object Inbox {
    private const val PREFS = "inbox"
    private const val KEY = "items"
    private const val MAX_ITEMS = 500

    /** App khula ho to WebView ko turant batane ke liye. */
    @Volatile
    var onNew: (() -> Unit)? = null

    private val MONEY = Regex("(?:rs\\.?|inr|₹)\\s*[\\d,]+(?:\\.\\d{1,2})?", RegexOption.IGNORE_CASE)
    private val WORDS = Regex(
        "\\b(?:debited|spent|paid|sent|withdrawn|purchase|payment of|credited|received|deposited|refund|cashback|transaction|txn|successful(?:ly)?|swiped|charged|used at|auto-?debit|debit card|credit card|net ?banking)\\b",
        RegexOption.IGNORE_CASE
    )

    fun looksFinancial(text: String): Boolean = MONEY.containsMatchIn(text) && WORDS.containsMatchIn(text)

    @Synchronized
    fun add(ctx: Context, kind: String, text: String, ts: Long) {
        val clean = text.trim()
        if (clean.isEmpty() || !looksFinancial(clean)) return
        val prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        val o = JSONObject().put("kind", kind).put("text", clean).put("ts", ts)
        arr.put(o)
        val trimmed = if (arr.length() > MAX_ITEMS) {
            JSONArray().also { n -> for (i in arr.length() - MAX_ITEMS until arr.length()) n.put(arr.get(i)) }
        } else arr
        prefs.edit().putString(KEY, trimmed.toString()).apply()
        onNew?.invoke()
    }

    @Synchronized
    fun drain(ctx: Context): String {
        val prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val v = prefs.getString(KEY, "[]") ?: "[]"
        prefs.edit().putString(KEY, "[]").apply()
        return v
    }

    @Synchronized
    fun count(ctx: Context): Int {
        val prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return JSONArray(prefs.getString(KEY, "[]")).length()
    }
}
