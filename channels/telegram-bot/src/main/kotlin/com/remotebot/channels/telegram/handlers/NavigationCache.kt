package com.remotebot.channels.telegram.handlers

import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Path cache for Telegram inline buttons.
 * Telegram API enforces a strict 64-byte limit on callback_data.
 * Long Android filesystem paths frequently exceed this limit.
 * NavigationCache generates short unique tokens (e.g., "1", "2", "3a"),
 * enabling compact callback_data commands such as "/lsi 1" or "/gf 2".
 */
object NavigationCache {
    private const val MAX_ENTRIES = 2000

    private val tokenToPath =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, String>(MAX_ENTRIES, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > MAX_ENTRIES
            },
        )

    private val pathToToken =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, String>(MAX_ENTRIES, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > MAX_ENTRIES
            },
        )

    private val counter = AtomicLong(1)

    fun register(path: String): String {
        pathToToken[path]?.let { return it }
        val token = counter.getAndIncrement().toString(36)
        tokenToPath[token] = path
        pathToToken[path] = token
        return token
    }

    fun resolve(tokenOrPath: String): String = tokenToPath[tokenOrPath] ?: tokenOrPath
}
