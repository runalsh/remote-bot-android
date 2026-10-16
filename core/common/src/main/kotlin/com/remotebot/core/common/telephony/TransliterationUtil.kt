package com.remotebot.core.common.telephony

/**
 * Утилита транслитерации текста (ГОСТ 7.79-2000 / Система Б)
 * для перевода кириллических сообщений в латиницу (GSM 7-bit).
 * Позволяет уместить в 1 сотовый сегмент SMS до 160 символов вместо 70.
 */
object TransliterationUtil {
    private val cyrillicToLatinMap =
        mapOf(
            'а' to "a",
            'б' to "b",
            'в' to "v",
            'г' to "g",
            'д' to "d",
            'е' to "e",
            'ё' to "yo",
            'ж' to "zh",
            'з' to "z",
            'и' to "i",
            'й' to "j",
            'к' to "k",
            'л' to "l",
            'м' to "m",
            'н' to "n",
            'о' to "o",
            'п' to "p",
            'р' to "r",
            'с' to "s",
            'т' to "t",
            'у' to "u",
            'ф' to "f",
            'х' to "kh",
            'ц' to "ts",
            'ч' to "ch",
            'ш' to "sh",
            'щ' to "shch",
            'ъ' to "",
            'ы' to "y",
            'ь' to "",
            'э' to "e",
            'ю' to "yu",
            'я' to "ya",
            'А' to "A",
            'Б' to "B",
            'В' to "V",
            'Г' to "G",
            'Д' to "D",
            'Е' to "E",
            'Ё' to "Yo",
            'Ж' to "Zh",
            'З' to "Z",
            'И' to "I",
            'Й' to "J",
            'К' to "K",
            'Л' to "L",
            'М' to "M",
            'Н' to "N",
            'О' to "O",
            'П' to "P",
            'Р' to "R",
            'С' to "S",
            'Т' to "T",
            'У' to "U",
            'Ф' to "F",
            'Х' to "Kh",
            'Ц' to "Ts",
            'Ч' to "Ch",
            'Ш' to "Sh",
            'Щ' to "Shch",
            'Ъ' to "",
            'Ы' to "Y",
            'Ь' to "",
            'Э' to "E",
            'Ю' to "Yu",
            'Я' to "Ya",
        )

    fun transliterate(text: String): String {
        val sb = StringBuilder(text.length * 2)
        for (ch in text) {
            val replacement = cyrillicToLatinMap[ch]
            if (replacement != null) {
                sb.append(replacement)
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }
}
