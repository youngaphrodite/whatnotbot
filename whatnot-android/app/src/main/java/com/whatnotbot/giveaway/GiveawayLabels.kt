package com.whatnotbot.giveaway

/** Labels from the original app and verified phone screenshots; no guesses about coordinates or resource IDs. */
internal object GiveawayLabels {
    private fun normalize(text: String): String = text.trim().lowercase(java.util.Locale.ROOT)
        .replace('\u2019', '\'').replace(Regex("\\s+"), " ")

    fun isEntry(text: String): Boolean = normalize(text) in setOf(
        "enter giveaway", "follow and enter", "follow host & enter giveaway"
    )
    fun isBadge(text: String): Boolean = normalize(text) == "giveaway"
    fun isEntered(text: String): Boolean = normalize(text) == "you're in the giveaway"
}
