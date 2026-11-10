package com.packfare.app.domain

/** One thing to book (a journey or a stay) and whether it is done. */
data class BookingItem(val key: String, val label: String, val done: Boolean) {
    companion object {
        /** One line per item: key, label, 0/1, separated by tabs. */
        fun encode(items: List<BookingItem>): String =
            items.joinToString("\n") { "${it.key}\t${it.label.replace('\t', ' ').replace('\n', ' ')}\t${if (it.done) 1 else 0}" }

        fun decode(text: String?): List<BookingItem> =
            text.orEmpty().lines().mapNotNull { line ->
                val p = line.split('\t')
                if (p.size < 3 || p[0].isBlank()) null else BookingItem(p[0], p[1], p[2] == "1")
            }

        fun toggle(items: List<BookingItem>, key: String): List<BookingItem> =
            items.map { if (it.key == key) it.copy(done = !it.done) else it }
    }
}
