package com.kieronquinn.app.pcs.xposed

import android.app.Application
import android.util.Base64
import com.kieronquinn.app.pcs.model.PcsManifestList
import com.kieronquinn.app.pcs.model.phone.PhoneFlag
import com.kieronquinn.app.pcs.model.phone.PhoneSettings
import com.kieronquinn.app.pcs.providers.PhoneSettingsProvider
import com.kieronquinn.app.pcs.repositories.AstreaRepository.Companion.PORT_PHONE
import com.kieronquinn.app.pcs.repositories.SettingsRepository.BeeslyRegion
import com.kieronquinn.app.pcs.repositories.SettingsRepository.DobbyRegion
import com.kieronquinn.app.pcs.repositories.SettingsRepository.PatrickPhase
import com.kieronquinn.app.pcs.utils.extensions.getKeyByValue
import com.kieronquinn.app.pcs.utils.extensions.loadDexKit
import com.kieronquinn.app.pcs.utils.extensions.reflectParseProto
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.ClassData
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Method
import java.lang.reflect.Modifier

object PhoneHooks: GrpcHooks() {

    override val tag = "PhoneHooks"
    override val applicationClassName = "com.android.dialer.Dialer_Application"
    override val activityClassName =
        "com.android.dialer.multibindingsettings.impl.DialerSettingsActivity"
    override val port = PORT_PHONE

    private val flagOverrides = HashMap<PhoneFlag, Any>()

    override fun LoadPackageParam.onBeforeApplicationOnCreate(application: Application) {
        val dexKit = loadDexKit(appInfo.sourceDir)
        val settings = PhoneSettingsProvider.getSettings(application) ?: run {
            log("Unable to get phone settings")
            return
        }
        hookFlagDataStore(dexKit, settings)
        if (settings.patrickPhase > PatrickPhase.DISABLED) {
            hookPatrick(dexKit)
        }
    }

    private fun LoadPackageParam.hookFlagDataStore(dexKit: DexKitBridge, settings: PhoneSettings) {
        val flagDataStore = dexKit.findClass {
            matcher {
                usingStrings("phenotypeServerTokens")
            }
        }.singleOrNull()?.getInstance(classLoader) ?: run {
            log("Unable to find Flag DataStore")
            return
        }
        val flagValueHolder = dexKit.findClass {
            matcher {
                usingStrings("null cannot be cast to non-null type T of com.google.apps.tiktok.experiments.FlagValueHolder.getProtoValue")
            }
        }.singleOrNull()
        val flagValueHolderClass = flagValueHolder?.getInstance(classLoader) ?: run {
            log("Unable to find FlagValueHolder")
            return
        }
        val flagValueHolderProtoMethod = flagValueHolder.findMethod {
            matcher {
                usingStrings("null cannot be cast to non-null type T of com.google.apps.tiktok.experiments.FlagValueHolder.getProtoValue")
            }
        }.singleOrNull()?.getMethodInstance(classLoader) ?: run {
            log("Unable to find FlagValueHolder proto method")
            return
        }
        var hasHookedCreator = false
        XposedHelpers.findAndHookConstructor(
            flagDataStore,
            Object::class.java,
            ByteArray::class.java,
            object: XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (hasHookedCreator) return
                    val creator = XposedHelpers
                        .callMethod(param.args[0], "a")::class.java
                    if (creator != java.lang.Boolean::class.java) {
                        hasHookedCreator = true
                        hookFlagCreator(creator, flagValueHolderClass)
                    }
                }
            }
        )
        hookFlagValueHolder(flagValueHolderClass, flagValueHolderProtoMethod, settings)
    }

    private fun LoadPackageParam.hookFlagCreator(
        creator: Class<*>,
        flagValueHolder: Class<*>
    ) {
        val creatorMethod = creator.declaredMethods.firstOrNull {
            it.returnType == flagValueHolder
        } ?: run {
            log("Unable to find creator method for flags")
            return
        }
        log("Creator method: ${creatorMethod.declaringClass.name}.${creatorMethod.name}(${creatorMethod.parameterTypes.joinToString(", ") { it.name }})")
        XposedBridge.hookMethod(creatorMethod, object: XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                PhoneFlag.getOrNull(
                    param.args[0] as String,
                    param.args[1] as String
                )?.let {
                    flagOverrides[it] = param.result
                }
            }
        })
    }

    private fun hookFlagValueHolder(
        flagValueHolder: Class<*>,
        protoMethod: Method,
        settings: PhoneSettings
    ) {
        val hookMethod = { method: Method ->
            XposedBridge.hookMethod(method, object: XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    flagOverrides.getKeyByValue(param.thisObject)
                        ?.getValueOrNull(param.result, settings)
                        ?.let { param.result = it }
                }
            })
        }
        hookMethod(protoMethod)
        val hookReturnType = { returnType: Class<*> ->
            val method = flagValueHolder.declaredMethods.firstOrNull {
                it.parameterCount == 0
                        && Modifier.isPublic(it.modifiers)
                        && Modifier.isFinal(it.modifiers)
                        && it.returnType == returnType
            }
            if (method != null) {
                hookMethod(method)
            }
        }
        val types = listOf(Double::class.java, Long::class.java, String::class.java, Boolean::class.java)
        types.forEach(hookReturnType)
    }

    /**
     *  Additional hooks required to make Patrick work on some devices
     */
    private fun LoadPackageParam.hookPatrick(dexKit: DexKitBridge) {
        val patrickClass = dexKit.findClass {
            matcher {
                usingStrings("com/android/dialer/patrick/impl/checker/PatrickAvailabilityChecker")
            }
        }.singleOrNull()?.getInstance(classLoader) ?: run {
            log("Unable to find Patrick class")
            return
        }
        val patrickMethod = patrickClass.methods.firstOrNull {
            it.returnType == Object::class.java && Modifier.isFinal(it.modifiers)
        } ?: run {
            log("Unable to find Patrick method")
            return
        }
        XposedBridge.hookMethod(patrickMethod, object: XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val objectField = param.args[0]::class.java.declaredFields.firstOrNull {
                    it.type == Object::class.java && !Modifier.isFinal(it.modifiers)
                } ?: return
                objectField.isAccessible = true
                objectField.set(param.args[0], true)
            }
        })
    }

    private fun PhoneFlag.getValueOrNull(originalValue: Any?, settings: PhoneSettings): Any? {
        return when (this) {
            PhoneFlag.DOBBY_DOWNLOAD_PATH -> settings.dobbyUrl?.takeIf {
                settings.dobbyEnabled
            }?.decodeRawBase64()
            PhoneFlag.DOBBY_DUPLEX_FILES -> settings.dobbyDuplexFiles
                ?.takeIf { settings.dobbyEnabled }
                ?.getListManifestOrNull(settings.dobbyRegion.locale)
                ?.reflectParseProto(originalValue?.javaClass ?: return null)
            PhoneFlag.DOBBY_IS_USER_IN_US -> if (settings.dobbyEnabled) {
                settings.dobbyRegion == DobbyRegion.US
            } else null
            PhoneFlag.DOBBY_IS_USER_IN_UK -> if (settings.dobbyEnabled) {
                settings.dobbyRegion == DobbyRegion.GB
            } else null
            PhoneFlag.DOBBY_IS_USER_IN_JP -> if (settings.dobbyEnabled) {
                settings.dobbyRegion == DobbyRegion.JP
            } else null
            PhoneFlag.DOBBY_IS_USER_IN_CA -> if (settings.dobbyEnabled) {
                settings.dobbyRegion == DobbyRegion.CA
            } else null
            PhoneFlag.DOBBY_IS_USER_IN_IE -> if (settings.dobbyEnabled) {
                settings.dobbyRegion == DobbyRegion.IE
            } else null
            PhoneFlag.DOBBY_IS_USER_IN_AU -> if (settings.dobbyEnabled) {
                settings.dobbyRegion == DobbyRegion.AU
            } else null
            PhoneFlag.DOBBY_IS_USER_IN_IN -> if (settings.dobbyEnabled) {
                settings.dobbyRegion == DobbyRegion.IN
            } else null
            PhoneFlag.DOBBY_ENABLE_V57_INDIA -> if (settings.dobbyEnabled) {
                true
            } else null
            PhoneFlag.DOBBY_INDIA_PB_FIX -> if (settings.dobbyEnabled) {
                true
            } else null
            PhoneFlag.ATLAS_MODELS -> settings.atlasModels
                ?.takeIf { settings.beeslyEnabled }
                ?.fromBase64()
                ?.reflectParseProto(originalValue?.javaClass ?: return null)
            PhoneFlag.BEESLY_MODEL_FILENAME_US -> settings.beesly
                ?.takeIf { settings.beeslyEnabled && settings.beeslyRegion != BeeslyRegion.GB }
                ?.getListManifestOrNull(settings.dobbyRegion.locale)
                ?.let { String(it) }
            PhoneFlag.BEESLY_MODEL_FILENAME_UK -> settings.beesly
                ?.takeIf { settings.beeslyEnabled && settings.beeslyRegion == BeeslyRegion.GB }
                ?.getListManifestOrNull(settings.beeslyRegion.locale)
                ?.let { String(it) }
            PhoneFlag.BEESLY_IS_USER_IN_US -> if (settings.beeslyEnabled) {
                settings.beeslyRegion == BeeslyRegion.US
            } else null
            PhoneFlag.BEESLY_IS_USER_IN_CA -> if (settings.beeslyEnabled) {
                settings.beeslyRegion == BeeslyRegion.CA
            } else null
            PhoneFlag.BEESLY_IS_USER_IN_UK -> if (settings.beeslyEnabled) {
                settings.beeslyRegion == BeeslyRegion.GB
            } else null
            PhoneFlag.BEESLY_IS_USER_IN_IE -> if (settings.beeslyEnabled) {
                settings.beeslyRegion == BeeslyRegion.IE
            } else null
            PhoneFlag.BEESLY_IS_USER_IN_AU -> if (settings.beeslyEnabled) {
                settings.beeslyRegion == BeeslyRegion.AU
            } else null
            PhoneFlag.XATU_MODELS -> settings.xatuModels
                ?.takeIf { settings.xatuEnabled }
                ?.fromBase64()
                ?.reflectParseProto(originalValue?.javaClass ?: return null)
            PhoneFlag.SHARPIE_ENABLED -> if (settings.sharpieEnabled) {
                true
            } else null
            PhoneFlag.DOBBY_ENABLED -> if (settings.dobbyEnabled) {
                true
            } else null
            PhoneFlag.ATLAS_ENABLED -> if (settings.atlasEnabled) {
                true
            } else null
            PhoneFlag.BEESLY_ENABLED -> if (settings.beeslyEnabled) {
                true
            } else null
            PhoneFlag.BEESLY_ACTIONS_ENABLED -> if (settings.beeslyEnabled) {
                true
            } else null
            PhoneFlag.BEESLY_GREETING_ENABLED -> if (settings.beeslyEnabled) {
                true
            } else null
            PhoneFlag.NAUTILUS_ENABLED -> if (settings.nautilusEnabled) {
                true
            } else null
            PhoneFlag.SONIC_ENABLED -> if (settings.sonicEnabled) {
                true
            } else null
            PhoneFlag.XATU_ENABLED -> if (settings.xatuEnabled) {
                true
            } else null
            PhoneFlag.CALLER_TAG_EXPERIMENT_ID -> if (settings.callerTagsEnabled) {
                1L
            } else null
            PhoneFlag.FERMAT_ENABLED -> when {
                settings.fermatEnabled -> 3L // Enables Call Notes at the top
                settings.callRecordingEnabled -> 4L // Enables Call Recording at the top
                else -> null
            }
            PhoneFlag.FERMAT_GEOFENCE -> if (settings.callRecordingEnabled || settings.fermatEnabled) {
                true
            } else null
            PhoneFlag.EXPRESSO_ENABLED -> if (settings.expressoEnabled) {
                true
            } else null
            PhoneFlag.PATRICK_ENABLED -> if (settings.patrickPhase >= PatrickPhase.PHASE_ONE) {
                true
            } else null
            PhoneFlag.PATRICK_PHASE_ONE_ENABLED -> if (settings.patrickPhase >= PatrickPhase.PHASE_ONE) {
                true
            } else null
            PhoneFlag.PATRICK_PHASE_TWO_ENABLED -> if (settings.patrickPhase >= PatrickPhase.PHASE_TWO) {
                true
            } else null
            PhoneFlag.PATRICK_PHASE_TWO_ENABLE_REPOSITORY -> if (settings.patrickPhase >= PatrickPhase.PHASE_TWO) {
                true
            } else null
            PhoneFlag.CALL_RECORDING_OVERRIDE_ENABLED -> if (settings.callRecordingEnabled) {
                true
            } else null
            PhoneFlag.CALL_RECORDING_ENABLED -> if (settings.callRecordingEnabled) {
                true
            } else null
            PhoneFlag.CALL_RECORDING_FORCE_OVERRIDE_ENABLED -> if (settings.callRecordingEnabled) {
                true
            } else null
            PhoneFlag.CALL_RECORDING_CROSBY_ENABLED -> if (settings.callRecordingEnabled) {
                true
            } else null
            PhoneFlag.CALL_RECORDING_FERMAT_DISABLE -> if (settings.callRecordingEnabled) {
                false
            } else null
        }
    }

    private fun String.fromBase64(): ByteArray {
        return Base64.decode(this, Base64.DEFAULT)
    }

    private fun String.decodeRawBase64(): String {
        return String(fromBase64())
    }

    private fun String.getListManifestOrNull(id: String): ByteArray? {
        val rawManifest = fromBase64()
        return try {
            PcsManifestList.parseFrom(rawManifest)
                .manifestList.firstOrNull { it.id.startsWith(id) }?.manifest?.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    private fun ClassData.findMethodMultiple(vararg search: String): MethodData? {
        return search.firstNotNullOfOrNull { term ->
            findMethod {
                matcher {
                    usingStrings(term)
                }
            }.singleOrNull()
        }
    }

}