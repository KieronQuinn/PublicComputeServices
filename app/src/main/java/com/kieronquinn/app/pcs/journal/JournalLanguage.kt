package com.kieronquinn.app.pcs.journal

import java.lang.reflect.Modifier

/**
 *  How Pixel Journal's AI features (writing suggestions, feedback, reflections, auto format) are
 *  offered in the phone's language.
 *
 *  Journal has no flag for its languages. A built-in set of language codes ([isLanguageSet]) is
 *  handed to the classes that build per-language tables, and each table only keeps the languages in
 *  the set: the language names the prompts are given, the prompt templates, and per feature the
 *  AICore feature to run. A language missing from any of them leaves the feature off. The prompts
 *  ask for the output language by name, so another language takes the English entries
 *  ([ENGLISH]) with its own name where English names itself.
 *
 *  Journal also checks every generated text with a safety classifier that Google publishes per
 *  language, for the supported languages only. Other languages are checked with the English
 *  classifier and thresholds ([SAFETY_LANGUAGE]), which is weaker on text in another language.
 */
object JournalLanguage {

    /** The language whose entries are copied for another language. */
    const val ENGLISH = "en"
    private const val ENGLISH_NAME = "English"

    /** The safety classifier used for languages Google publishes none for. */
    const val SAFETY_LANGUAGE = ENGLISH

    /** The model list request label naming the classifier's language. */
    const val LOCALE_LABEL = "locale"

    /** Whether [value] is Journal's set of language codes: lower case codes, English among them. */
    fun isLanguageSet(value: Any?): Boolean {
        val set = value as? Set<*> ?: return false
        return ENGLISH in set && set.all { it is String && it.matches(LANGUAGE_CODE) }
    }

    private val LANGUAGE_CODE = Regex("[a-z]{2,3}")

    /** [set] with [language] added, or null when it is already there. */
    fun withLanguage(set: Set<*>, language: String): Set<Any?>? {
        if (language in set) return null
        return LinkedHashSet<Any?>(set).apply { add(language) }
    }

    /**
     *  [map], a per-language table keyed by language code or by a wrapper holding the code, with an
     *  entry for [language] copied from English, or null when it has no English entry or already
     *  has the language. The English name becomes [languageName].
     *
     *  @param keyFor builds a key of the map's key type for a language code.
     */
    fun withLanguage(
        map: Map<*, *>,
        language: String,
        languageName: String,
        keyFor: (template: Any, language: String) -> Any?
    ): Map<Any?, Any?>? {
        if (languageKey(map, language) != null) return null
        val englishKey = languageKey(map, ENGLISH) ?: return null
        val key = if (englishKey is String) language else keyFor(englishKey, language) ?: return null
        val english = map[englishKey]
        return LinkedHashMap<Any?, Any?>(map).apply {
            put(key, if (english == ENGLISH_NAME) languageName else english)
        }
    }

    /** The key of [map] for [language]: the code itself or a wrapper holding only that code. */
    fun languageKey(map: Map<*, *>, language: String): Any? {
        return map.keys.firstOrNull { key ->
            key == language || (key != null && key !is String && singleString(key) == language)
        }
    }

    /** The only instance String field of [value], or null when it has none or several. */
    fun singleString(value: Any): String? {
        val fields = value.javaClass.declaredFields.filter {
            it.type == String::class.java && !Modifier.isStatic(it.modifiers)
        }
        val field = fields.singleOrNull() ?: return null
        return runCatching { field.isAccessible = true; field.get(value) as? String }.getOrNull()
    }

    /**
     *  The English reflection template [template] asking for [languageName] instead, or null when
     *  it names no output language: "**English**" when present, else a single "English".
     */
    fun reflectionPrompt(template: String, languageName: String): String? {
        val bold = "**$ENGLISH_NAME**"
        if (template.contains(bold)) return template.replace(bold, "**$languageName**")
        val first = template.indexOf(ENGLISH_NAME)
        if (first < 0 || first != template.lastIndexOf(ENGLISH_NAME)) return null
        return template.replace(ENGLISH_NAME, languageName)
    }

    /** The model list labels [labels] asking for the [SAFETY_LANGUAGE] classifier instead of [language]'s. */
    fun safetyLabels(labels: Map<*, *>, language: String): Map<Any?, Any?>? {
        if (labels[LOCALE_LABEL] != language) return null
        return LinkedHashMap<Any?, Any?>(labels).apply { put(LOCALE_LABEL, SAFETY_LANGUAGE) }
    }

}
