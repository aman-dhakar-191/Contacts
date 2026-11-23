package io.github.amandhakar.contacts.util

object PhoneUtil {
    fun digits(s: String): String = s.filter { it.isDigit() }

    /** Last 10 digits, so "+91 98765 43210" and "09876543210" compare equal. */
    fun key(number: String): String {
        val d = digits(number)
        return if (d.length > 10) d.takeLast(10) else d
    }
}
