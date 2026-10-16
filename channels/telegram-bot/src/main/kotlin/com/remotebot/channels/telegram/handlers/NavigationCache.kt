package com.remotebot.channels.telegram.handlers

import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Кэш путей для инлайн-кнопок Telegram.
 * Telegram API имеет строгое ограничение callback_data в 64 байта.
 * Длинные пути в файловой системе Android часто превышают этот лимит.
 * NavigationCache генерирует короткие уникальные токены (например, "1", "2", "3a"),
 * позволяя передавать в callback_data компактные команды вида "/lsi 1" или "/gf 2".
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
