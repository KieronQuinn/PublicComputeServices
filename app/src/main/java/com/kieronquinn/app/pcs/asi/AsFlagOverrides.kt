package com.kieronquinn.app.pcs.asi

import java.util.Locale

/**
 *  Values for Android System Intelligence flags, by flag name: booleans that are forced, and
 *  string lists that get entries added (the server's entries are kept).
 */
class AsFlagOverrides private constructor(
    private val booleans: Map<String, Boolean>,
    private val additions: Map<String, List<String>>
) {

    companion object {

        private const val NOTIFICATIONS = "SmartNotification__"

        /**
         *  Notification Organizer (notification bundles) in [locale]'s language and [country].
         *  ASI only offers bundles in listed countries and languages, and its language detector
         *  drops notifications that are not in a listed language before they are classified.
         */
        fun notificationOrganizer(locale: Locale, country: String?) = Builder()
            .boolean("${NOTIFICATIONS}enable_notification_bundle", true)
            .boolean("${NOTIFICATIONS}enable_default_bundle_feature", true)
            .add("${NOTIFICATIONS}bundle_supported_languages", locale.language)
            .add("${NOTIFICATIONS}supported_languages", locale.language)
            .apply { country?.let { add("${NOTIFICATIONS}bundle_supported_countries", it) } }

    }

    class Builder {
        private val booleans = LinkedHashMap<String, Boolean>()
        private val additions = LinkedHashMap<String, MutableList<String>>()

        fun boolean(name: String, value: Boolean) = apply { booleans[name] = value }

        fun add(name: String, entry: String) = apply {
            if (entry.isNotBlank()) additions.getOrPut(name) { ArrayList() }.add(entry)
        }

        fun plus(other: Builder) = apply {
            booleans.putAll(other.booleans)
            other.additions.forEach { (name, entries) -> entries.forEach { add(name, it) } }
        }

        fun build() = AsFlagOverrides(booleans.toMap(), additions.mapValues { it.value.toList() })
    }

    val size get() = booleans.size + additions.size

    fun isEmpty() = size == 0

    fun boolean(name: String): Boolean? = booleans[name]

    /**
     *  [current] with this override's entries added, or null if there is none or all are listed.
     *  Entries match without case, as ASI compares language and country codes.
     */
    fun list(name: String, current: List<String>): List<String>? {
        val missing = additions[name].orEmpty().filter { entry ->
            current.none { it.equals(entry, ignoreCase = true) }
        }.distinctBy { it.lowercase(Locale.ROOT) }
        return if (missing.isEmpty()) null else current + missing
    }

}
