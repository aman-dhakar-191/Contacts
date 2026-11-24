package io.github.amandhakar.contacts.data

import android.content.Context
import io.github.amandhakar.contacts.util.PhoneUtil

/**
 * Names Android gave for callers who are not in the contacts (the caller-ID name on an incoming call), kept on this phone
 * so they can be shown later, e.g. when you call that number back. Never saved into contacts, never sent anywhere.
 */
class CallerNames(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("caller_names", Context.MODE_PRIVATE)

    fun get(number: String): String? {
        val k = PhoneUtil.key(number)
        if (k.length < 3) return null
        return sp.getString("n:$k", null)?.substringAfter('|', "")?.takeIf { it.isNotBlank() }
    }

    fun put(number: String, name: String) {
        val k = PhoneUtil.key(number)
        val n = name.trim()
        if (k.length < 3 || n.isEmpty() || PhoneUtil.digits(n) == PhoneUtil.digits(number) && PhoneUtil.digits(n).isNotEmpty()) return
        val old = sp.getString("n:$k", null)
        if (old != null && old.substringAfter('|', "") == n) return
        val e = sp.edit().putString("n:$k", "${System.currentTimeMillis()}|$n")
        e.apply()
        if (sp.all.size > MAX + PRUNE) prune()
    }

    fun count(): Int = sp.all.size

    fun clear() { sp.edit().clear().apply() }

    /** Drops the oldest entries so the list cannot grow without bound. */
    private fun prune() {
        val oldest = sp.all.entries
            .mapNotNull { (k, v) -> (v as? String)?.substringBefore('|')?.toLongOrNull()?.let { k to it } }
            .sortedBy { it.second }
            .take(sp.all.size - MAX)
        val e = sp.edit()
        oldest.forEach { e.remove(it.first) }
        e.apply()
    }

    private companion object { const val MAX = 3000; const val PRUNE = 200 }
}
