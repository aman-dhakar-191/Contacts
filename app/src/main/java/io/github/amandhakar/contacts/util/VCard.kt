package io.github.amandhakar.contacts.util

data class VCardContact(val name: String, val phones: List<String>, val emails: List<String>)

/** Small vCard reader: names, phone numbers and emails. Photos and other fields are ignored. */
object VCard {
    fun parse(text: String): List<VCardContact> {
        val lines = text.replace(Regex("\r?\n[ \t]"), "").split(Regex("\r?\n"))
        val out = ArrayList<VCardContact>()
        var inCard = false
        var fn = ""
        var n = ""
        var phones = ArrayList<String>()
        var emails = ArrayList<String>()
        for (line in lines) {
            val upper = line.trim().uppercase()
            if (upper == "BEGIN:VCARD") {
                inCard = true; fn = ""; n = ""; phones = ArrayList(); emails = ArrayList(); continue
            }
            if (upper == "END:VCARD") {
                if (inCard) {
                    val name = fn.ifBlank { n }
                    if (name.isNotBlank() || phones.isNotEmpty() || emails.isNotEmpty()) {
                        out += VCardContact(name.trim(), phones.distinct(), emails.distinct())
                    }
                }
                inCard = false; continue
            }
            if (!inCard) continue
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            var prop = line.substring(0, colon).substringBefore(';').uppercase()
            prop = prop.substringAfter('.') // drop "item1." group prefix
            val value = unescape(line.substring(colon + 1)).trim()
            when (prop) {
                "FN" -> fn = value
                "N" -> {
                    val p = line.substring(colon + 1).split(';')
                    n = listOf(p.getOrNull(1), p.getOrNull(0)).filterNotNull().map { unescape(it).trim() }
                        .filter { it.isNotEmpty() }.joinToString(" ")
                }
                "TEL" -> if (value.isNotEmpty()) phones += value
                "EMAIL" -> if (value.isNotEmpty()) emails += value
            }
        }
        return out
    }

    private fun unescape(s: String): String =
        s.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")
}
