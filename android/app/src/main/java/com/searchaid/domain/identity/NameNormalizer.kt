package com.searchaid.domain.identity

import javax.inject.Inject

/**
 * Normalizes person names: trims, generates case variants,
 * transliterates Cyrillic↔Latin, splits compound names.
 */
class NameNormalizer @Inject constructor() {

    fun normalize(name: String): List<String> {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return emptyList()

        val variants = mutableSetOf<String>()

        // Original
        variants.add(trimmed)

        // Parts (first name, last name, patronymic)
        val parts = trimmed.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (parts.size >= 2) {
            // "Иванов Иван Петрович" → "Иван Иванов", "Иванов И.П."
            variants.add(parts.reversed().joinToString(" "))
            if (parts.size >= 2) {
                val initials = parts.drop(1).joinToString("") { "${it.first()}." }
                variants.add("${parts[0]} $initials")
            }
        }

        // Individual parts as standalone
        parts.forEach { variants.add(it) }

        // Transliteration both ways
        val transliterated = variants.toList().flatMap { v ->
            listOfNotNull(
                transliterateCyrToLat(v).takeIf { it != v },
                transliterateLatToCyr(v).takeIf { it != v },
            )
        }
        variants.addAll(transliterated)

        return variants.toList()
    }

    fun transliterateCyrToLat(text: String): String {
        val sb = StringBuilder()
        for (ch in text) {
            sb.append(CYR_TO_LAT[ch] ?: ch)
        }
        return sb.toString()
    }

    fun transliterateLatToCyr(text: String): String {
        var result = text
        // Apply multi-char mappings first (sorted longest first)
        for ((lat, cyr) in LAT_TO_CYR_MULTI) {
            result = result.replace(lat, cyr, ignoreCase = false)
            result = result.replace(lat.replaceFirstChar { it.uppercaseChar() },
                cyr.replaceFirstChar { it.uppercaseChar() }, ignoreCase = false)
        }
        // Then single-char
        val sb = StringBuilder()
        for (ch in result) {
            sb.append(LAT_TO_CYR_SINGLE[ch] ?: ch)
        }
        return sb.toString()
    }

    companion object {
        private val CYR_TO_LAT = mapOf(
            'А' to "A", 'Б' to "B", 'В' to "V", 'Г' to "G", 'Д' to "D",
            'Е' to "E", 'Ё' to "Yo", 'Ж' to "Zh", 'З' to "Z", 'И' to "I",
            'Й' to "Y", 'К' to "K", 'Л' to "L", 'М' to "M", 'Н' to "N",
            'О' to "O", 'П' to "P", 'Р' to "R", 'С' to "S", 'Т' to "T",
            'У' to "U", 'Ф' to "F", 'Х' to "Kh", 'Ц' to "Ts", 'Ч' to "Ch",
            'Ш' to "Sh", 'Щ' to "Shch", 'Ъ' to "", 'Ы' to "Y", 'Ь' to "",
            'Э' to "E", 'Ю' to "Yu", 'Я' to "Ya",
            'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
            'е' to "e", 'ё' to "yo", 'ж' to "zh", 'з' to "z", 'и' to "i",
            'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
            'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
            'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch",
            'ш' to "sh", 'щ' to "shch", 'ъ' to "", 'ы' to "y", 'ь' to "",
            'э' to "e", 'ю' to "yu", 'я' to "ya",
        )

        private val LAT_TO_CYR_MULTI = listOf(
            "shch" to "щ", "Shch" to "Щ",
            "zh" to "ж", "Zh" to "Ж",
            "kh" to "х", "Kh" to "Х",
            "ts" to "ц", "Ts" to "Ц",
            "ch" to "ч", "Ch" to "Ч",
            "sh" to "ш", "Sh" to "Ш",
            "yu" to "ю", "Yu" to "Ю",
            "ya" to "я", "Ya" to "Я",
            "yo" to "ё", "Yo" to "Ё",
        )

        private val LAT_TO_CYR_SINGLE = mapOf(
            'A' to 'А', 'B' to 'Б', 'V' to 'В', 'G' to 'Г', 'D' to 'Д',
            'E' to 'Е', 'Z' to 'З', 'I' to 'И', 'Y' to 'Й', 'K' to 'К',
            'L' to 'Л', 'M' to 'М', 'N' to 'Н', 'O' to 'О', 'P' to 'П',
            'R' to 'Р', 'S' to 'С', 'T' to 'Т', 'U' to 'У', 'F' to 'Ф',
            'a' to 'а', 'b' to 'б', 'v' to 'в', 'g' to 'г', 'd' to 'д',
            'e' to 'е', 'z' to 'з', 'i' to 'и', 'y' to 'й', 'k' to 'к',
            'l' to 'л', 'm' to 'м', 'n' to 'н', 'o' to 'о', 'p' to 'п',
            'r' to 'р', 's' to 'с', 't' to 'т', 'u' to 'у', 'f' to 'ф',
        )
    }
}
