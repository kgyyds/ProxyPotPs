package com.example.proxypotps.util

private val knownFlags = listOf("🇯🇵", "🇺🇲", "🇺🇸", "🇭🇰", "🇸🇬", "🇩🇪", "🇫🇷", "🇬🇧", "🇨🇳")

fun extractFlagEmoji(name: String): String {
    return knownFlags.firstOrNull { name.contains(it) } ?: "🌐"
}
