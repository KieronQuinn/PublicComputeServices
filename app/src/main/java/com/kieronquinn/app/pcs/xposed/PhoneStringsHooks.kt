package com.kieronquinn.app.pcs.xposed

import android.content.Context
import android.content.res.Resources
import android.content.res.TypedArray
import android.text.Html
import android.util.TypedValue
import com.kieronquinn.app.pcs.phonestrings.PhoneStrings
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 *  Shows Phone's English-only strings in the phone's language, from [PhoneStrings].
 *
 *  Phone ships its Direct My Call, Call Screen, Hold for Me and spam screens with an English
 *  default only, so a phone in another language shows them in English. Each translated string is
 *  answered in place of Phone's string of the same name, from every `Resources` and `TypedArray`
 *  accessor that returns text (preference XML reads through `TypedArray`). The accessors are
 *  hooked directly rather than only `getText`, because compiled framework code may inline
 *  `getText` into `getString`.
 *
 *  Only Phone's own resources are changed, and only while they are in the translation's language.
 *  The translation is read from PCS's APK through the module's class loader, so it does not depend
 *  on the PCS app running.
 */
internal object PhoneStringsHooks {

    private const val TAG = "PhoneHooks"
    private const val PHONE = "com.google.android.dialer"
    private const val APP_PACKAGE_ID = 0x7f

    fun install(context: Context) {
        val phoneLocale = context.resources.configuration.locales.get(0)
        val strings = try {
            PhoneStrings.forLocale(phoneLocale) { path ->
                PhoneStringsHooks::class.java.classLoader?.getResourceAsStream("assets/$path")
            }
        } catch (error: Exception) {
            log("Phone strings unreadable: $error")
            return
        } ?: run {
            log("No Phone strings for ${phoneLocale.toLanguageTag()}")
            return
        }
        val table = Table(strings)
        try {
            hookTextLookups(table)
            log("Phone strings installed for ${strings.locale.toLanguageTag()} (${strings.strings.size})")
        } catch (error: Throwable) {
            log("Phone strings unavailable: $error")
        }
    }

    private fun hookTextLookups(table: Table) {
        val text = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                table.lookup(param.thisObject as Resources, param.args[0] as Int)?.let { param.result = it }
            }
        }
        XposedBridge.hookMethod(Resources::class.java.getDeclaredMethod("getText", Int::class.java), text)
        XposedBridge.hookMethod(
            Resources::class.java.getDeclaredMethod("getText", Int::class.java, CharSequence::class.java), text
        )
        XposedBridge.hookMethod(Resources::class.java.getDeclaredMethod("getString", Int::class.java),
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    table.lookup(param.thisObject as Resources, param.args[0] as Int)
                        ?.let { param.result = it.toString() }
                }
            })
        XposedBridge.hookMethod(
            Resources::class.java.getDeclaredMethod("getString", Int::class.java, Array<Any>::class.java),
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val resources = param.thisObject as Resources
                    val value = table.lookup(resources, param.args[0] as Int) ?: return
                    val arguments = param.args[1] as? Array<*> ?: emptyArray<Any?>()
                    // As Resources.getString(int, Object...) formats it.
                    param.result = String.format(resources.configuration.locales.get(0), value.toString(), *arguments)
                }
            })
        val typedArray = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val array = param.thisObject as TypedArray
                val value = array.peekValue(param.args[0] as Int) ?: return
                if (value.type != TypedValue.TYPE_STRING || value.resourceId == 0) return
                val translated = table.lookup(array.resources, value.resourceId) ?: return
                param.result = if (param.method.name == "getString") translated.toString() else translated
            }
        }
        XposedBridge.hookMethod(TypedArray::class.java.getDeclaredMethod("getText", Int::class.java), typedArray)
        XposedBridge.hookMethod(TypedArray::class.java.getDeclaredMethod("getString", Int::class.java), typedArray)
    }

    /** Phone's resource ids to their translations, resolved on Phone's first string lookup. */
    private class Table(private val strings: PhoneStrings) {

        @Volatile
        private var ids: Map<Int, CharSequence>? = null

        fun lookup(resources: Resources, id: Int): CharSequence? {
            // Phone's resources use the app package id; framework strings are never changed.
            if (id ushr 24 != APP_PACKAGE_ID) return null
            if (resources.configuration.locales.get(0).language != strings.locale.language) return null
            val table = ids ?: build(resources) ?: return null
            val translated = table[id] ?: return null
            // Another package loaded in Phone's process can have the same id.
            return translated.takeIf { packageOf(resources, id) == PHONE }
        }

        @Synchronized
        private fun build(resources: Resources): Map<Int, CharSequence>? {
            ids?.let { return it }
            // Built from Phone's resources only: another package's lookup does not resolve names.
            if (strings.strings.keys.none { resources.getIdentifier(it, "string", PHONE) != 0 }) return null
            val missing = ArrayList<String>()
            val table = HashMap<Int, CharSequence>()
            strings.strings.forEach { (name, value) ->
                val id = resources.getIdentifier(name, "string", PHONE)
                if (id == 0) missing += name else table[id] = styled(value)
            }
            if (missing.isNotEmpty()) log("Phone has no string ${missing.joinToString()}; left out")
            ids = table
            return table
        }

        private fun styled(value: String): CharSequence =
            if ('<' in value) Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY) else value

        private fun packageOf(resources: Resources, id: Int) = try {
            resources.getResourcePackageName(id)
        } catch (e: Resources.NotFoundException) {
            null
        }
    }

    private fun log(message: String) = XposedBridge.log("$TAG: $message")

}
