package com.pixvault.ui.theme

enum class ThemeMode(val prefValue: String) {
    System("system"),
    Light("light"),
    Dark("dark");

    companion object {
        fun fromPref(value: String?): ThemeMode =
            values().firstOrNull { it.prefValue == value } ?: System
    }
}