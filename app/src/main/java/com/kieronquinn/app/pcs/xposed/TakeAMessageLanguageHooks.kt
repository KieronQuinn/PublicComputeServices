package com.kieronquinn.app.pcs.xposed

import android.content.Context
import com.kieronquinn.app.pcs.voicepack.VoicePack
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Modifier

/**
 *  Runs Take a Message in the phone's language with its voice pack (see [VoicePackHooks]).
 *
 *  - The file group ("beesly-duplex-files") comes from Take a Message's model provider, which
 *    picks the US or UK zip from the region flags PCS sets.
 *  - `BeeslySingletonLocaleProvider` keeps the locale of the current service in a lazy property,
 *    and the newer service asks a suspend method of the same provider. Both are answered with the
 *    pack's locale while a pack is active.
 */
internal object TakeAMessageLanguageHooks {

    private const val TAG = "PhoneHooks"
    private const val LOCALE_PROVIDER = "com/android/dialer/beesly/impl/locale/BeeslySingletonLocaleProvider"

    private val voicePack = VoicePackHooks(VoicePack.TAKE_A_MESSAGE)

    fun install(dexKit: DexKitBridge, classLoader: ClassLoader, context: Context) {
        try {
            val groupSource = dexKit.findClass {
                matcher { usingStrings(VoicePack.TAKE_A_MESSAGE.groupName, "isUserInUk") }
            }.single().getInstance(classLoader)
            val localeProvider = dexKit.findClass {
                matcher { usingStrings(LOCALE_PROVIDER, "isUserInUk") }
            }.single().getInstance(classLoader)
            val suspendLocale = localeProvider.declaredMethods.single {
                !Modifier.isStatic(it.modifiers) && it.parameterCount == 1 &&
                    it.parameterTypes[0].isInterface && it.returnType == Any::class.java
            }
            voicePack.install(dexKit, classLoader, context, groupSource, localeProvider)
            XposedBridge.hookMethod(suspendLocale, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    // A suspended call returns a marker; the locale arrives when it resumes,
                    // which calls this method again.
                    val tag = param.result as? String ?: return
                    if ('-' !in tag) return
                    voicePack.resolvePack()?.let { param.result = it.locale.toLanguageTag() }
                }
            })
        } catch (error: Exception) {
            log("Take a Message language unavailable: $error")
        }
    }

    private fun log(message: String) = XposedBridge.log("$TAG: $message")

}
