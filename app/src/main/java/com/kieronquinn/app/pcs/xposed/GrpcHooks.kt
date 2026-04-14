package com.kieronquinn.app.pcs.xposed

import android.app.Application
import android.os.Bundle
import androidx.annotation.CallSuper
import com.google.crypto.tink.hybrid.HybridConfig
import com.kieronquinn.app.pcs.repositories.AstreaRepository
import com.kieronquinn.app.pcs.repositories.AstreaRepository.Companion.HOST
import com.kieronquinn.app.pcs.repositories.AstreaRepositoryImpl
import com.kieronquinn.app.pcs.repositories.ManifestRepository
import com.kieronquinn.app.pcs.repositories.ManifestRepositoryImpl
import com.kieronquinn.app.pcs.repositories.PhenotypeRepository
import com.kieronquinn.app.pcs.repositories.PhenotypeRepositoryStub
import com.kieronquinn.app.pcs.utils.extensions.loadDexKit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import org.koin.android.ext.koin.androidContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.luckypray.dexkit.DexKitBridge
import retrofit2.Retrofit
import java.lang.reflect.Member
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSession

abstract class GrpcHooks: XposedHooks, KoinComponent {

    companion object {
        private const val HOST_ODS = "ondevicesafety-pa.googleapis.com:443"
    }

    /**
     *  Class of the sub-classed Application to hook (required)
     *
     *  [onBeforeApplicationOnCreate] and [onAfterApplicationOnCreate] will be called automatically
     */
    abstract val applicationClassName: String

    /**
     *  The port to use for the gRPC server. This should be unique for this package.
     */
    abstract val port: Int

    /**
     *  If the gRPC service should be started with a service, the service to hook
     */
    open val serviceClassName: String? = null

    /**
     *  If the gRPC service should be started with an activity, the activity to hook
     */
    open val activityClassName: String? = null

    private val astrea by inject<AstreaRepository>()

    @CallSuper
    override fun hook(loadPackageParam: LoadPackageParam) {
        val dexKit = loadDexKit(loadPackageParam.appInfo.sourceDir)
        loadPackageParam.hookApplication()
        if (!isEnabled()) return
        log("Begin gRPC hooking")
        loadPackageParam.hookService()
        loadPackageParam.hookActivity()
        loadPackageParam.hookTrustManager()
        loadPackageParam.hookOds(dexKit)
        loadPackageParam.hookSsl(dexKit)
        log("Finished gRPC hooking")
    }

    open fun isEnabled(): Boolean = true

    private fun LoadPackageParam.hookApplication() {
        XposedHelpers.findAndHookMethod(
            applicationClassName,
            classLoader,
            "onCreate",
            object: XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val application = param.thisObject as Application
                    onBeforeApplicationOnCreate(application)
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    super.afterHookedMethod(param)
                    val application = param.thisObject as Application
                    System.loadLibrary("sekret")
                    HybridConfig.register()
                    startKoin {
                        androidContext(application)
                        modules(singles())
                    }
                    onAfterApplicationOnCreate(application)
                }
            }
        )
    }

    private fun LoadPackageParam.hookService() {
        XposedHelpers.findAndHookMethod(
            serviceClassName ?: return,
            classLoader,
            "onCreate",
            object: XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    super.afterHookedMethod(param)
                    log("Starting service")
                    astrea.start()
                }
            }
        )
    }

    private fun LoadPackageParam.hookActivity() {
        XposedHelpers.findAndHookMethod(
            activityClassName ?: return,
            classLoader,
            "onCreate",
            Bundle::class.java,
            object: XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    super.afterHookedMethod(param)
                    log("Starting service")
                    astrea.start()
                }
            }
        )
    }

    /**
     *  Hooks Trust Manager and bypasses SSL host verification for 127.0.0.1 only. Based on
     *  the Xposed module TrustMeAlready.
     */
    private fun LoadPackageParam.hookTrustManager() {
        val trustManager = XposedHelpers.findClass(
            "com.android.org.conscrypt.TrustManagerImpl",
            classLoader
        )
        val checkTrustedRecursive = trustManager.declaredMethods.first {
            it.name == "checkTrustedRecursive"
        }
        XposedBridge.hookMethod(
            checkTrustedRecursive,
            object: XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    super.beforeHookedMethod(param)
                    val host = param.args.filterIsInstance<String>().first()
                    if(host == HOST) {
                        param.result = listOf<X509Certificate>()
                    }
                }
            }
        )
    }

    open fun LoadPackageParam.getOdsConstructor(dexKit: DexKitBridge): Member? {
        val hostLoader = dexKit.findClass {
            matcher { usingStrings("TLS Provider failure") }
        }.singleOrNull()?.let {
            try {
                XposedHelpers.findClass(it.name, classLoader)
            } catch (e: XposedHelpers.ClassNotFoundError) {
                null
            }
        } ?: run {
            return null
        }
        return hostLoader.getConstructor(String::class.java)
    }

    /**
     *  Hooks creation of gRPC client for On Device Safety (ODS) and redirects it to our own custom
     *  localhost one.
     */
    private fun LoadPackageParam.hookOds(dexKit: DexKitBridge) {
        val constructor = getOdsConstructor(dexKit) ?: run {
            log("Unable to find host loader class")
            return
        }
        XposedBridge.hookMethod(
            constructor,
            object: XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    super.beforeHookedMethod(param)
                    log("Loading service for ${param.args[0]}")
                    if(param.args[0] == HOST_ODS) {
                        param.args[0] = "$HOST:$port"
                    }
                }
            }
        )
    }

    /**
     *  Hooks and disables SSL hostname verification for 127.0.0.1
     */
    private fun LoadPackageParam.hookSsl(dexKit: DexKitBridge) {
        val sslVerifier = dexKit.findClass {
            matcher {
                usingStrings(
                    "UTF-8 length does not fit in int: ",
                    "Unpaired surrogate at index "
                )
            }
        }.singleOrNull()?.let {
            try {
                XposedHelpers.findClass(it.name, classLoader)
            } catch (e: XposedHelpers.ClassNotFoundError) {
                null
            }
        } ?: run {
            log("Unable to find SSL verifier class")
            return
        }
        XposedHelpers.findAndHookMethod(
            sslVerifier,
            "verify",
            String::class.java,
            SSLSession::class.java,
            object: XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if(param.args[0] == HOST) {
                        param.result = true
                    }
                }
            }
        )
    }

    private fun singles() = module {
        single {
            Retrofit.Builder()
                .baseUrl("http://localhost")
                .build()
        }
        single<AstreaRepository> { AstreaRepositoryImpl(port, get()) }
        single<PhenotypeRepository> { PhenotypeRepositoryStub }
        single<ManifestRepository> {
            ManifestRepositoryImpl(get(), get(), get())
        }
    }

    open fun LoadPackageParam.onBeforeApplicationOnCreate(application: Application) {
        // No-op by default
    }

    open fun LoadPackageParam.onAfterApplicationOnCreate(application: Application) {
        // No-op by default
    }

}