package com.kieronquinn.app.pcs.xposed

import android.app.Application
import com.google.crypto.tink.hybrid.HybridConfig
import com.kieronquinn.app.pcs.repositories.AstreaRepository
import com.kieronquinn.app.pcs.repositories.AstreaRepository.Companion.HOST
import com.kieronquinn.app.pcs.repositories.AstreaRepository.Companion.PORT
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
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSession

object PcsHooks: XposedHooks, KoinComponent {

    override val tag = "PcsHooks"

    private const val APPLICATION_CLASS_NAME =
        "com.google.android.apps.miphone.astrea.PrivateComputeServicesApplication"

    private const val SERVICE_CLASS_NAME =
        "com.google.android.apps.miphone.astrea.grpc.AstreaGrpcService"

    private const val HOST_ODS = "ondevicesafety-pa.googleapis.com"

    private val astrea by inject<AstreaRepository>()

    override fun hook(loadPackageParam: LoadPackageParam) {
        val dexKit = loadDexKit(loadPackageParam.appInfo.sourceDir)
        loadPackageParam.hookApplication()
        loadPackageParam.hookService()
        loadPackageParam.hookTrustManager()
        loadPackageParam.hookOds(dexKit)
        loadPackageParam.hookSsl(dexKit)
        log("Finished hooking")
    }

    private fun LoadPackageParam.hookApplication() {
        XposedHelpers.findAndHookMethod(
            APPLICATION_CLASS_NAME,
            classLoader,
            "onCreate",
            object: XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    super.afterHookedMethod(param)
                    val application = param.thisObject as Application
                    System.loadLibrary("sekret")
                    HybridConfig.register()
                    startKoin {
                        androidContext(application)
                        modules(singles())
                    }
                }
            }
        )
    }

    private fun LoadPackageParam.hookService() {
        XposedHelpers.findAndHookMethod(
            SERVICE_CLASS_NAME,
            classLoader,
            "onCreate",
            object: XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    super.afterHookedMethod(param)
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

    /**
     *  Hooks creation of gRPC client for On Device Safety (ODS) and redirects it to our own custom
     *  localhost one.
     */
    private fun LoadPackageParam.hookOds(dexKit: DexKitBridge) {
        val hostLoader = dexKit.findClass {
            matcher { usingStrings("keepalive time must be positive") }
        }.singleOrNull()?.let {
            try {
                XposedHelpers.findClass(it.name, classLoader)
            } catch (e: XposedHelpers.ClassNotFoundError) {
                null
            }
        } ?: run {
            log("Unable to find host loader class")
            return
        }
        XposedHelpers.findAndHookConstructor(
            hostLoader,
            String::class.java,
            Integer.TYPE,
            object: XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    super.beforeHookedMethod(param)
                    if(param.args[0] == HOST_ODS) {
                        param.args[0] = HOST
                        param.args[1] = PORT
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
        single<AstreaRepository> { AstreaRepositoryImpl() }
        single<PhenotypeRepository> { PhenotypeRepositoryStub }
        single<ManifestRepository> {
            ManifestRepositoryImpl(get(), get(), get())
        }
    }

}