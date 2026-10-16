package com.kieronquinn.app.pcs.xposed

import com.kieronquinn.app.pcs.utils.DebugLogging
import com.kieronquinn.app.pcs.repositories.SettingsRepository.CallScreenMode
import com.kieronquinn.app.pcs.repositories.SettingsRepository.SharpieCountryMode
import android.app.Application
import android.util.Base64
import com.kieronquinn.app.pcs.model.PcsManifestList
import com.kieronquinn.app.pcs.model.phone.PhoneFlag
import com.kieronquinn.app.pcs.model.phone.FlagValueHolders
import com.kieronquinn.app.pcs.model.phone.PhoneSettings
import com.kieronquinn.app.pcs.providers.PhoneSettingsProvider
import com.kieronquinn.app.pcs.repositories.AstreaRepository.Companion.PORT_PHONE
import com.kieronquinn.app.pcs.repositories.SettingsRepository.BeeslyRegion
import com.kieronquinn.app.pcs.repositories.SettingsRepository.DobbyRegion
import com.kieronquinn.app.pcs.repositories.SettingsRepository.PatrickPhase
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
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong

object PhoneHooks: GrpcHooks() {

    override val tag = "PhoneHooks"
    override val applicationClassName = "com.android.dialer.Dialer_Application"
    override val activityClassName =
        "com.android.dialer.multibindingsettings.impl.DialerSettingsActivity"
    override val port = PORT_PHONE

    private val seenFlags = ConcurrentHashMap.newKeySet<String>()
    private val isolatedFlags = ConcurrentHashMap.newKeySet<PhoneFlag>()
    private val isolationFailures = ConcurrentHashMap.newKeySet<PhoneFlag>()
    private val missingFlags = ConcurrentHashMap.newKeySet<PhoneFlag>()
    private val appliedFlags = ConcurrentHashMap.newKeySet<PhoneFlag>()
    private val typeMismatches = ConcurrentHashMap.newKeySet<PhoneFlag>()
    private val unassociatedReads = ConcurrentHashMap.newKeySet<String>()
    private val lastXatuDownloadAttempt = AtomicLong(0)

    override val deferGrpcHooksUntilApplication = true
    private var phoneSettings: PhoneSettings? = null

    override fun hook(loadPackageParam: LoadPackageParam) {
        DebugLogging.beginPhoneProcess()
        super.hook(loadPackageParam)
    }

    override fun isEnabled() = phoneSettings?.manifestHandlingEnabled == true

    override fun LoadPackageParam.onBeforeApplicationOnCreate(application: Application) {
        val dexKit = loadDexKit(appInfo.sourceDir)
        val settings = PhoneSettingsProvider.getSettings(application) ?: run {
            log("Unable to get phone settings")
            return
        }
        phoneSettings = settings
        DebugLogging.configurePhone(settings.debugLogging)
        installGrpcHooks()
        DebugHooks.hook(this)
        log("Phone settings: mode=${settings.effectiveCallScreenMode}, " +
            "sharpie=${settings.sharpieCountryMode}/${settings.sharpieCountry}, " +
            "metered=${settings.xatuAllowMetered}, manifests=${settings.manifestHandlingEnabled}, " +
            "debug=${settings.debugLogging}")
        logDobbyData(settings)
        hookFlagDataStore(dexKit, settings)
        CallNotesHooks.install(dexKit, classLoader, settings.fermatEnabled)
        hookSharpieCountry(dexKit, settings)
        hookCallScreen(dexKit, settings)
        hookDobbyModel(dexKit, settings)
        if (settings.dobbyEnabled) DobbyMddHooks.install(dexKit, classLoader)
        hookDobbySettings(dexKit, settings)
        hookXatuModelDownload(dexKit, settings)
        hookXatuMddConnectivity(dexKit, settings)
        if (settings.patrickPhase > PatrickPhase.DISABLED) {
            hookPatrick(dexKit)
        }
        if (settings.fermatEnabled && settings.callRecordingEnabled) {
            hookCallRecording(dexKit)
        }
    }

    private fun LoadPackageParam.hookFlagDataStore(dexKit: DexKitBridge, settings: PhoneSettings) {
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
        val flagDataStores = dexKit.findMethod {
            matcher {
                usingStrings("mendelPackage", "Unknown package ")
            }
        }.mapNotNull { method ->
            try {
                method.declaredClass?.getInstance(classLoader)
            }catch (e: Throwable) {
                null
            }
        }.distinct()
        if (flagDataStores.isEmpty()) {
            log("Unable to find Flag DataStore")
            return
        }
        val holders = try {
            FlagValueHolders<PhoneFlag>(flagValueHolderClass)
        } catch (e: Exception) {
            log("Unable to isolate flag value holders: $e")
            return
        }
        hookFlagValueHolder(flagValueHolderClass, flagValueHolderProtoMethod, settings, holders)
        flagDataStores.forEach { hookFlagCreator(it, flagValueHolderClass, holders) }
    }

    private fun LoadPackageParam.hookFlagCreator(
        creator: Class<*>,
        flagValueHolder: Class<*>,
        holders: FlagValueHolders<PhoneFlag>
    ) {
        val creatorMethods = creator.declaredMethods.filter {
            it.returnType == flagValueHolder
        }
        if (creatorMethods.isEmpty()) {
            log("Unable to find creator method for flags")
            return
        }
        creatorMethods.forEach { creatorMethod ->
            log("Creator ${creator.name} method: ${creatorMethod.declaringClass.name}.${creatorMethod.name}(${creatorMethod.parameterTypes.joinToString(", ") { it.name }})")
            XposedBridge.hookMethod(creatorMethod, object: XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.hasThrowable()) return
                    val flagPackage = param.args.getOrNull(0) as? String ?: return
                    val flagName = param.args.getOrNull(1) as? String ?: return
                    if (seenFlags.add("$flagPackage/$flagName")) {
                        log("Flag lookup: $flagPackage -> $flagName")
                    }
                    val flag = resolveFlag(flagPackage, flagName) ?: return
                    val original = param.result ?: return
                    try {
                        // Default holders (especially true/false) are shared by many flags. Never
                        // associate or modify the original: that also changes unrelated flags.
                        param.result = holders.isolate(flag, original)
                        if (isolatedFlags.add(flag)) {
                            log("Isolated ${flag.name} ($flagPackage/$flagName), " +
                                    "original holder=${System.identityHashCode(original)}")
                        }
                    } catch (e: Exception) {
                        if (isolationFailures.add(flag)) {
                            log("Unable to isolate ${flag.name}: $e")
                        }
                    }
                }
            })
        }
    }

    private fun resolveFlag(flagPackage: Any?, flagName: Any?): PhoneFlag? {
        if (flagPackage !is String || flagName !is String) return null
        return PhoneFlag.getOrNull(flagPackage, flagName)
    }

    private fun hookFlagValueHolder(
        flagValueHolder: Class<*>,
        protoMethod: Method,
        settings: PhoneSettings,
        holders: FlagValueHolders<PhoneFlag>
    ) {
        val hookMethod: (Method) -> Unit = { method: Method ->
            XposedBridge.hookMethod(method, object: XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val flag = holders.flagFor(param.thisObject) ?: run {
                        logUnassociatedRead(param.thisObject, method)
                        return
                    }
                    val override = try {
                        flag.getValueOrNull(param.result, settings)
                    }catch (e: Throwable) {
                        if (missingFlags.add(flag)) {
                            log("Failed to build override value for ${flag.name}: $e")
                        }
                        null
                    }
                    if (override == null) {
                        if (missingFlags.add(flag)) {
                            log("No override value for ${flag.name}: ${flag.describeMissingValue(settings)}")
                        }
                        return
                    }
                    if (!method.returnType.accepts(override)) {
                        if (typeMismatches.add(flag)) {
                            log("Cannot apply ${flag.name} to ${method.name}(): ${method.returnType.name}")
                        }
                        return
                    }
                    param.result = override
                    if (appliedFlags.add(flag)) {
                        log(
                            "Applied override value for ${flag.name} via ${method.name}(), " +
                                    "value: ${override.describeValue()}"
                        )
                    }
                }
            })
        }
        // Flag values are read through the holder's zero argument accessors, which have changed
        // between Dialer versions (and now include a primitive boolean), so hook all of them
        // rather than assuming a fixed set of return types
        (flagValueHolder.declaredMethods.filter {
            it.parameterCount == 0
                    && !Modifier.isStatic(it.modifiers)
                    && it.returnType != Void.TYPE
        } + protoMethod).distinct().forEach(hookMethod)
    }

    private fun Any.describeValue(): String {
        return toString().take(200)
    }

    /**
     *  A value was read from a holder that no known flag was created for, so we cannot override
     *  it. Log the caller once per holder type/accessor so the missed path is identifiable
     */
    private fun logUnassociatedRead(holder: Any, method: Method) {
        val key = "${holder.javaClass.name}.${method.name}()"
        if (unassociatedReads.add(key)) {
            val caller = try {
                getCallingInformation()?.let { "${it.first}.${it.second}" }
            }catch (e: Throwable) {
                null
            }
            log("Unassociated read from $key, caller $caller")
        }
    }

    private fun Class<*>.accepts(value: Any): Boolean {
        if (isInstance(value)) return true
        return when (this) {
            java.lang.Boolean.TYPE -> value is Boolean
            java.lang.Long.TYPE -> value is Long
            java.lang.Integer.TYPE -> value is Int
            java.lang.Double.TYPE -> value is Double
            java.lang.Float.TYPE -> value is Float
            java.lang.Short.TYPE -> value is Short
            java.lang.Byte.TYPE -> value is Byte
            java.lang.Character.TYPE -> value is Char
            else -> false
        }
    }

    /**
     *  Explains why no value could be built for a tracked flag, so the reason is visible in logs
     *  without having to dump the flag data itself
     */
    private fun PhoneFlag.describeMissingValue(settings: PhoneSettings): String {
        return when (this) {
            PhoneFlag.DOBBY_DUPLEX_FILES, PhoneFlag.DOBBY_MODELS,
            PhoneFlag.DOBBY_DOWNLOAD_PATH, PhoneFlag.DOBBY_ENABLED -> buildString {
                append("dobbyEnabled=${settings.dobbyEnabled}")
                append(", dobbyUrl=${settings.dobbyUrl != null}")
                append(", manifestSize=${settings.dobbyDuplexFiles?.length ?: 0}")
                append(", region=${settings.dobbyRegion.locale}")
                append(", entry=${settings.dobbyDuplexFiles?.getListManifestOrNull(settings.dobbyRegion.locale) != null}")
            }
            else -> "not enabled in settings"
        }
    }

    /**
     *  Scam Detection (Sharpie) is gated on the SIM country being in an allowlist that is part of
     *  the Sharpie config, so a device with a SIM from an unsupported country gets the "Scam
     *  Detection is not available" banner and never downloads the Hades protections:
     *
     *  `SharpieCountryAllowlist.isSimCountryAllowed` -> `SharpieScamIntentPolicyMoiraiAvailability`
     *  -> `MoiraiIntegrationImpl.isAvailable` -> `SHARPIE_HADES_PROTECTION_CANNOT_DOWNLOAD`
     *
     *  The country comes from `TelephonyManager.getSimCountryIso()` and is carried in the config
     *  (`I18nConfig.detectionCountryLegacy`), so the check is replaced here rather than the
     *  telephony API, which other Dialer features rely on.
     *
     *  The country policy is selected in PCS settings.
     */
    private fun LoadPackageParam.hookSharpieCountry(dexKit: DexKitBridge, settings: PhoneSettings) {
        if (!settings.sharpieEnabled) return
        val mode = settings.sharpieCountryMode
        if (mode == SharpieCountryMode.DIALER_DEFAULT) {
            log("Leaving Dialer's Scam Detection country check alone")
            return
        }
        val allowlist = dexKit.findClass {
            matcher {
                usingStrings("Country %s not allowed.", "No sim country found.")
            }
        }.singleOrNull()?.getInstance(classLoader) ?: run {
            log("Unable to find SharpieCountryAllowlist")
            return
        }
        val isSimCountryAllowed = allowlist.declaredMethods.firstOrNull {
            it.parameterCount == 2 && it.parameterTypes[0] == String::class.java &&
                    it.returnType == java.lang.Boolean.TYPE
        } ?: run {
            log("Unable to find SharpieCountryAllowlist method")
            return
        }
        XposedBridge.hookMethod(isSimCountryAllowed, object: XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val country = param.args[0] as? String ?: return
                val allowed = settings.getSharpieCountryOverride(country) ?: return
                if (param.result != allowed) {
                    log("Scam Detection country override for $country: allowed=$allowed")
                    param.result = allowed
                }
            }
        })
        log("Overriding the Scam Detection country check in ${allowlist.name} ($mode)")
    }

    /**
     *  Call Screen is gated behind the CallScreenI18n (Tidepods) flag, which Dialer reads through
     *  a path the flag overrides do not reach, so enable the feature check itself
     */
    private fun LoadPackageParam.hookCallScreen(dexKit: DexKitBridge, settings: PhoneSettings) {
        if (!settings.dobbyEnabled) return
        val mode = settings.effectiveCallScreenMode
        if (mode != CallScreenMode.DUPLEX) {
            log("Leaving legacy Duplex Call Screen entry disabled for $mode mode")
            return
        }
        val callScreenEnabledFn = dexKit.findClass {
            matcher {
                usingStrings("feature disabled by tidepods call screen flag")
            }
        }.singleOrNull()?.getInstance(classLoader) ?: run {
            log("Unable to find CallScreenEnabledFn")
            return
        }
        val isEnabledMethod = callScreenEnabledFn.declaredMethods.firstOrNull {
            it.parameterCount == 0 && it.returnType == java.lang.Boolean.TYPE
        } ?: run {
            log("Unable to find CallScreenEnabledFn method")
            return
        }
        log("Enabling Call Screen via ${callScreenEnabledFn.name}.${isEnabledMethod.name}()")
        XposedBridge.hookMethod(isEnabledMethod, object: XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.result = true
            }
        })
    }

    /**
     *  Dialer picks the "agentic" Call Screen model (`call_assistant_extraction-v6_*`, driven by
     *  AICore's GACS feature 305) when `enableAgenticCallScreen` is set, or when
     *  `enableGacsManualScreening` is set in a region where manual screening is supported, and
     *  otherwise falls back to the earlier duplex model. Force the two flags the model picker is
     *  built with to the mode [PhoneSettings.effectiveCallScreenMode] selects, so the model does not depend on
     *  experiment flags the module cannot reliably pin. The flags are exposed as providers and
     *  read through Dialer's obfuscated experiment layer, which the flag value overrides do not
     *  reach, so replace the providers at construction time instead.
     */
    private fun LoadPackageParam.hookDobbyModel(dexKit: DexKitBridge, settings: PhoneSettings) {
        if (!settings.dobbyEnabled) return
        val mode = settings.effectiveCallScreenMode
        if (mode == CallScreenMode.DIALER_DEFAULT) {
            log("Leaving Dialer's Call Screen model choice alone (agentic mode requested)")
            return
        }
        val picker = try {
            DobbyModelPicker.find(dexKit, classLoader)
        } catch (error: Exception) {
            log("Unable to resolve Dobby model picker: $error")
            return
        }
        val constructor = picker.constructor
        val flagType = picker.flagType
        val disabled = flagType.constantProvider(false)
        val enabled = flagType.constantProvider(true)
        val agentic = flagType.constantProvider(mode == CallScreenMode.FORCE_AGENTIC)
        // Dialer's own region flags are not reliable without the experiment overrides, so select
        // the model from the region PCS is configured for
        val regionOffset = when (settings.dobbyRegion) {
            DobbyRegion.US -> 0
            DobbyRegion.GB -> 1
            DobbyRegion.JP -> 2
            DobbyRegion.CA -> 3
            DobbyRegion.IE -> 4
            DobbyRegion.AU -> 5
            DobbyRegion.IN -> 6
        }
        XposedBridge.hookMethod(constructor, object: XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                picker.overrideProviders(param.args, agentic, regionOffset, enabled, disabled)
            }
        })
        log(
            "Forcing the ${if (mode == CallScreenMode.FORCE_AGENTIC) "agentic" else "non-agentic"} " +
                    "Call Screen model for ${settings.dobbyRegion} in ${constructor.declaringClass.name} " +
                    "(${constructor.parameterCount} dependencies)"
        )
    }

    /**
     *  Dialer builds the "Problem downloading required resources" banner for the GACS flow as soon
     *  as the unconditional resources banner flag is set, regardless of whether that flow can
     *  actually run. Disable that flag and the GACS manual screening/agentic flags on the settings
     *  data source when the duplex model is forced. Keep enableFixForStuckDownloadingBanner under
     *  Dialer's control: it corrects download status rather than enabling a GACS-only banner.
     */
    private fun LoadPackageParam.hookDobbySettings(dexKit: DexKitBridge, settings: PhoneSettings) {
        if (!settings.dobbyEnabled) return
        if (settings.effectiveCallScreenMode != CallScreenMode.DUPLEX) {
            log("Leaving Dialer's GACS banner flags alone (agentic mode requested)")
            return
        }
        val dataSource = dexKit.findClass {
            matcher {
                usingStrings("enableLlmSmartReplyConsentFlow", "enableUnconditionalResourcesBanner",
                    "enableDobbyGemini")
            }
        }.singleOrNull()?.getInstance(classLoader) ?: run {
            log("Unable to find Dobby settings data source")
            return
        }
        val constructor = dataSource.declaredConstructors.firstOrNull {
            it.parameterCount in 20..22 && it.parameterTypes.drop(14).distinct().size == 1
        } ?: run {
            log("Unable to find Dobby settings data source constructor")
            return
        }
        // The consent/banner providers stay at 14/15. Agentic is last; before 242,
        // manual-screening was penultimate. 242 removed manual-screening and allowTtsFallback.
        val flagType = constructor.parameterTypes[15]
        if (!flagType.isInterface) {
            log("Unexpected Dobby settings data source signature in ${dataSource.name}")
            return
        }
        val disabled = flagType.constantProvider(false)
        XposedBridge.hookMethod(constructor, object: XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                log("Applying GACS banner overrides to ${dataSource.name}")
                param.args[15] = disabled
                param.args[constructor.parameterCount - 1] = disabled
                if (constructor.parameterCount > 20) {
                    param.args[constructor.parameterCount - 2] = disabled
                }
            }
        })
        log("Disabling the GACS resources banner in ${dataSource.name}")
    }

    /**
     *  Builds a provider that always returns [value], so a flag can be pinned without relying on
     *  Dialer's obfuscated experiment layer
     */
    private fun Class<*>.constantProvider(value: Any): Any {
        return Proxy.newProxyInstance(classLoader, arrayOf(this)) { proxy, method, args ->
            when {
                method.name == "equals" && method.parameterTypes.contentEquals(
                    arrayOf(Any::class.java)
                ) -> args?.firstOrNull() === proxy
                method.name == "hashCode" && method.parameterCount == 0 ->
                    System.identityHashCode(proxy)
                method.name == "toString" && method.parameterCount == 0 ->
                    "PCS constant provider ($value)"
                method.parameterCount == 0 && method.returnType.accepts(value) -> value
                else -> throw UnsupportedOperationException("Unsupported provider method: $method")
            }
        }
    }

    /**
     *  Direct My Call (Xatu) downloads its model from MDD, but Dialer only starts that download
     *  when the user opts in from the settings screen: the DataStore write that the opt-in performs
     *  is what runs `XatuPipelineModelDownloader.downloadNecessaryModels` (through the data store
     *  `onData` callback). Once the module has pinned the enrollment flag, the feature already
     *  reads as opted in, so Dialer shows "Downloading Direct My Call..." with the switches
     *  disabled and never performs the write that would start the download, leaving the model
     *  unavailable forever.
     *
     *  Run the same trigger whenever the Xatu settings data source is loaded, which is the point
     *  at which the download state is (re)checked. The download itself is the regular Dialer code
     *  path, so it uses the `XATU_MODELS` flag the manifest overrides provide.
     */
    private fun LoadPackageParam.hookXatuModelDownload(
        dexKit: DexKitBridge,
        settings: PhoneSettings
    ) {
        if (!settings.xatuEnabled || settings.xatuModels == null) return
        val dataSource = dexKit.findClass {
            matcher {
                usingStrings("XatuSettingsKey")
            }
        }.singleOrNull()?.getInstance(classLoader) ?: run {
            log("Unable to find XatuSettingsDataSource")
            return
        }
        // The downloader is only identifiable by its log tag, which other Xatu classes share, so
        // pick the candidate the settings data source holds
        val downloaderCandidates = dexKit.findClass {
            matcher {
                usingStrings("com/android/dialer/xatu/impl/pipeline/XatuPipelineModelDownloader")
            }
        }.mapNotNull {
            try {
                it.getInstance(classLoader)
            }catch (e: Throwable) {
                null
            }
        }
        val downloaderField = dataSource.declaredFields.firstOrNull { field ->
            downloaderCandidates.any { it == field.type }
        } ?: run {
            log("Unable to find the Xatu model downloader in ${dataSource.name}")
            return
        }
        val downloader = downloaderField.type
        // downloadNecessaryModels returns a ListenableFuture (obfuscated, but still a Future).
        // Reject ambiguous versions instead of picking an arbitrary getter by reflection order.
        val downloadMethod = downloader.declaredMethods.singleOrNull {
            it.parameterCount == 0 && !Modifier.isStatic(it.modifiers) &&
                    !it.isBridge && !it.isSynthetic &&
                    Future::class.java.isAssignableFrom(it.returnType)
        } ?: run {
            log("Unable to uniquely identify the Xatu model download trigger in ${downloader.name}")
            return
        }
        // The data source "load" is the accessor returning the same future type as the downloader
        val loadMethod = dataSource.declaredMethods.singleOrNull {
            it.parameterCount == 0 && !Modifier.isStatic(it.modifiers) &&
                    !it.isBridge && !it.isSynthetic &&
                    it.returnType == downloadMethod.returnType
        }
        val trigger = object: XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val now = System.currentTimeMillis()
                val last = lastXatuDownloadAttempt.get()
                if (now - last < XATU_DOWNLOAD_RETRY_DELAY) {
                    return
                }
                if (!lastXatuDownloadAttempt.compareAndSet(last, now)) {
                    return
                }
                try {
                    val downloaderInstance = downloaderField.apply {
                        isAccessible = true
                    }.get(param.thisObject) ?: return
                    downloadMethod.isAccessible = true
                    log("Triggering the Direct My Call model download")
                    downloadMethod.invoke(downloaderInstance)
                }catch (e: Throwable) {
                    log("Failed to trigger the Direct My Call model download: $e")
                }
            }
        }
        dataSource.declaredConstructors.forEach {
            XposedBridge.hookMethod(it, trigger)
        }
        loadMethod?.let {
            XposedBridge.hookMethod(it, trigger)
        }
        log(
            "Triggering the Direct My Call model download from ${dataSource.name}" +
                    (loadMethod?.let { ".${it.name}()" } ?: "")
        )
    }

    private const val XATU_DOWNLOAD_RETRY_DELAY = 60 * 1000L

    /**
     *  MDD only runs a download once the device has a connection that satisfies the request, and
     *  the queued Xatu model asks for an unmetered one, so on a device that only ever has mobile
     *  data the download started by [hookXatuModelDownload] stays queued forever and the settings
     *  banner keeps showing a download that never happens.
     *
     *  Dialer's download queue (`MddFileDownloader`'s queue) decides that from a requirement on
     *  each queued request, so treat the Direct My Call model request as having no requirement once
     *  it is queued. PCS controls whether metered downloads are allowed.
     */
    private fun LoadPackageParam.hookXatuMddConnectivity(
        dexKit: DexKitBridge,
        settings: PhoneSettings
    ) {
        if (!settings.xatuEnabled || settings.xatuModels == null) return
        if (!settings.xatuAllowMetered) {
            log("Leaving Dialer's MDD connectivity requirement alone")
            return
        }
        val modelFileName = try {
            settings.xatuModels.fromBase64()
                .toString(Charsets.ISO_8859_1)
                .let { XATU_MODEL_URL.find(it)?.value }
                ?.substringAfterLast('/')
        } catch (e: Exception) {
            log("Unable to decode the Direct My Call model manifest: $e")
            return
        } ?: run {
            log("Unable to find the Direct My Call model URL")
            return
        }
        val downloadQueue = dexKit.findClass {
            matcher {
                usingStrings("requests pending connectivity")
            }
        }.singleOrNull()?.getInstance(classLoader) ?: run {
            log("Unable to find the MDD download queue")
            return
        }
        val connectivityCheck = downloadQueue.declaredMethods.firstOrNull {
            it.parameterCount == 1 && it.returnType == java.lang.Boolean.TYPE &&
                    it.parameterTypes[0].isEnum
        } ?: run {
            log("Unable to find the MDD connectivity check")
            return
        }
        val requirementType = connectivityCheck.parameterTypes[0]
        // The request the queue checks is the one it hands to the download worker, and it is no
        // longer queued by then, so replace the requirement on the request itself
        val request = downloadQueue.declaredMethods.asSequence()
            .filter {
                it.parameterCount == 1 && !Modifier.isStatic(it.modifiers)
            }
            .map { it.parameterTypes[0] }
            .distinct()
            .firstOrNull { type ->
                type.declaredMethods.any {
                    it.parameterCount == 0 && it.returnType == requirementType
                }
            } ?: run {
            log("Unable to find the MDD download request")
            return
        }
        val requirementGetter = request.declaredMethods.firstOrNull {
            it.parameterCount == 0 && it.returnType == requirementType
        } ?: run {
            log("Unable to find the MDD request connectivity requirement")
            return
        }
        XposedBridge.hookMethod(requirementGetter, object: XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val requirement = param.result as? Enum<*> ?: return
                if (requirement.name != "WIFI_ONLY") return
                if (!param.thisObject.isRequestFor(modelFileName)) return
                val satisfied = requirementType.enumConstants
                    ?.firstOrNull { (it as? Enum<*>)?.name == "NONE" } ?: return
                log("Allowing the Direct My Call model download on the current network")
                param.result = satisfied
            }
        })
        log("Relaxing the MDD connectivity requirement for $modelFileName")
    }

    /**
     *  Whether a queued MDD download request targets the given file, matched on the URL the
     *  request was built with
     */
    private fun Any?.isRequestFor(fileName: String): Boolean {
        if (this == null) return false
        return javaClass.declaredFields.any { field ->
            if (field.type != String::class.java) return@any false
            try {
                field.isAccessible = true
                (field.get(this) as? String)?.contains(fileName) == true
            }catch (e: Throwable) {
                false
            }
        }
    }

    private val XATU_MODEL_URL = Regex("""https://[\x21-\x7e]{5,300}?\.zip""")

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

    /**
     *  Call recording is particularly annoying to get to behave when Call Notes are enabled, so
     *  force it
     */
    private fun LoadPackageParam.hookCallRecording(dexKit: DexKitBridge) {
        val callRecordingClass = dexKit.findClass {
            matcher {
                usingStrings("Call recording is enabled by call_recording_audio system feature")
            }
        }.singleOrNull()?.getInstance(classLoader) ?: run {
            log("Unable to find Call Recording class")
            return
        }
        XposedHelpers.findAndHookMethod(
            callRecordingClass,
            "a",
            object: XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = true
                }
            })
    }

    private fun PhoneFlag.getValueOrNull(originalValue: Any?, settings: PhoneSettings): Any? {
        return when (this) {
            PhoneFlag.DOBBY_DOWNLOAD_PATH -> settings.dobbyUrl?.takeIf {
                settings.dobbyEnabled
            }?.decodeRawBase64()
            PhoneFlag.DOBBY_DUPLEX_FILES, PhoneFlag.DOBBY_MODELS -> settings.dobbyDuplexFiles
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
            // The model picker and the call session read independent providers. Pin the
            // actual session flags explicitly instead of relying on shared-default leakage.
            PhoneFlag.DOBBY_AGENTIC_ENABLED,
            PhoneFlag.DOBBY_GACS_MANUAL_ENABLED -> if (settings.dobbyEnabled) {
                when (settings.effectiveCallScreenMode) {
                    CallScreenMode.FORCE_AGENTIC -> true
                    CallScreenMode.DUPLEX -> false
                    CallScreenMode.DIALER_DEFAULT -> null
                }
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
            // Dialer gates both its settings entry and sender UI separately from the service.
            PhoneFlag.EXPRESSO_ENABLED, PhoneFlag.EXPRESSO_UI_ENABLED -> if (settings.expressoEnabled) {
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
            PhoneFlag.CALL_SCREEN_I18N_TIDEPODS -> if (
                settings.dobbyEnabled && settings.effectiveCallScreenMode == CallScreenMode.FORCE_AGENTIC
            ) {
                true
            } else null
        }
    }

    /**
     *  Dumps the Dobby data PCS has synced, so it is clear which manifest/download URLs the
     *  overrides are built from
     */
    private fun logDobbyData(settings: PhoneSettings) {
        log(
            "Dobby: enabled=${settings.dobbyEnabled}, region=${settings.dobbyRegion.locale}, " +
                    "url=${settings.dobbyUrl.describeDobbyUrl()}"
        )
        log("Dobby manifest: ${settings.dobbyDuplexFiles.describeManifestEntries()}")
        settings.dobbyDuplexFiles?.getListManifestOrNull(settings.dobbyRegion.locale)?.let {
            log("Dobby manifest entry (${it.size} bytes): ${it.describeBytes()}")
        }
    }

    private fun String?.describeDobbyUrl(): String {
        if (this == null) return "not set"
        return try {
            decodeRawBase64()
        }catch (e: Exception) {
            "invalid base64"
        }
    }

    private fun String?.describeManifestEntries(): String {
        if (this == null) return "not set"
        return try {
            PcsManifestList.parseFrom(fromBase64()).manifestList
                .joinToString(", ") { "${it.id}(${it.manifest.size()})" }
        }catch (e: Exception) {
            "unparseable"
        }
    }

    private fun ByteArray.describeBytes(): String {
        val text = String(this, Charsets.UTF_8)
        val printable = text.all { it.code in 0x20..0x7e || it == '\n' || it == '\t' }
        return if (printable) text.take(400) else joinToString("") { "%02x".format(it) }.take(400)
    }

    private fun String.fromBase64(): ByteArray {
        return Base64.decode(this, Base64.DEFAULT)
    }

    private fun String.decodeRawBase64(): String {
        return String(fromBase64())
    }

    private fun String.getListManifestOrNull(id: String): ByteArray? {
        return try {
            PcsManifestList.parseFrom(fromBase64())
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
