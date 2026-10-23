package com.packfare.app.domain

enum class Category(val label: String) {
    TRAVEL("Travel"),
    STAY("Stay"),
    FOOD("Food"),
    ACTIVITIES("Activities"),
    SHOPPING("Shopping"),
    OTHER("Other");

    companion object {
        fun fromName(name: String): Category = entries.firstOrNull { it.name == name } ?: OTHER
    }
}
