package com.packfare.app.explore

/** Pure text rules for short place descriptions (unit tested). */
object BlurbText {
    /** Words of 4+ letters that must overlap between the thing asked about and the article title. */
    fun words(s: String): Set<String> =
        s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 4 }.toSet()

    /** True when [title] plausibly is [subject] (shares a meaningful word), so we don't show a wrong blurb. */
    fun matches(subject: String, title: String): Boolean {
        val a = words(subject)
        return a.isEmpty() || a.any { it in words(title) }
    }

    /** Trim to whole sentences, at most [maxChars]. */
    fun tidy(extract: String, maxChars: Int = 260): String {
        val text = extract.replace(Regex("\\s*\\([^)]*\\)"), "").replace(Regex("\\s+"), " ").trim()
        if (text.length <= maxChars) return text
        val cut = text.take(maxChars)
        val end = cut.lastIndexOfAny(charArrayOf('.', '!', '?'))
        return if (end > 80) cut.substring(0, end + 1) else cut.substringBeforeLast(' ') + "…"
    }
}
