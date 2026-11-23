package io.github.amandhakar.contacts.util

/** T9 matching: typing 5-6-3 finds "Joe", and also any name where a word starts with those keys. */
object T9 {
    fun encode(name: String): String = buildString {
        for (c in name.lowercase()) {
            append(
                when (c) {
                    in 'a'..'c' -> '2'
                    in 'd'..'f' -> '3'
                    in 'g'..'i' -> '4'
                    in 'j'..'l' -> '5'
                    in 'm'..'o' -> '6'
                    in 'p'..'s' -> '7'
                    in 't'..'v' -> '8'
                    in 'w'..'z' -> '9'
                    in '0'..'9' -> c
                    else -> ' '
                },
            )
        }
    }

    fun matches(name: String, digits: String): Boolean {
        if (digits.isEmpty()) return true
        val code = encode(name)
        for (i in code.indices) {
            if ((i == 0 || code[i - 1] == ' ') && code.startsWith(digits, i)) return true
        }
        return code.replace(" ", "").startsWith(digits)
    }
}
