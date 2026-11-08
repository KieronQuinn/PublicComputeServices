package com.kieronquinn.app.pcs.xposed

import com.kieronquinn.app.pcs.callscreen.CallScreenModelFiles
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Modifier
import java.lang.reflect.Method
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal object DobbyMddHooks {
    fun install(dexKit: DexKitBridge, classLoader: ClassLoader) {
        try {
            val request = dexKit.findClass {
                matcher { usingStrings("GetFileGroupRequest{groupName=") }
            }.single().getInstance(classLoader)
            val groupName = request.declaredFields.single {
                !Modifier.isStatic(it.modifiers) && it.type == String::class.java
            }.apply { isAccessible = true }
            val client = dexKit.findClass {
                matcher { usingStrings("Unknown task tag sent to MDD.handleTask() ") }
            }.single().getInstance(classLoader)
            val getGroup = client.declaredMethods.single {
                it.parameterTypes.contentEquals(arrayOf(request)) &&
                    it.returnType.isInterface && Future::class.java.isAssignableFrom(it.returnType)
            }
            val repair = runCatching {
                createRepair(dexKit, classLoader, client, request, getGroup)
            }.onFailure {
                XposedBridge.log("PhoneHooks: Native Call Screen MDD recovery unavailable: $it")
            }.getOrNull()
            val reported = AtomicBoolean()
            XposedBridge.hookMethod(getGroup, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.hasThrowable() || groupName.get(param.args[0]) != CallScreenModelFiles.GROUP) return
                    val result = param.result as? Future<*> ?: return
                    val clientInstance = param.thisObject
                    param.result = CallScreenModelFiles.guard(getGroup.returnType, result) {
                        if (reported.compareAndSet(false, true)) {
                            XposedBridge.log("PhoneHooks: Empty Call Screen MDD group treated as unavailable; native model download required")
                        }
                        repair?.invoke(clientInstance)
                    }
                }
            })
            // Read through the same guard once at startup. This repairs cached empty groups
            // before an incoming call, without blocking Application.onCreate or editing MDD files.
            if (repair != null) {
                val createRequest = requestFactory(request)
                val checked = AtomicBoolean()
                XposedBridge.hookAllConstructors(client, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.hasThrowable() || !checked.compareAndSet(false, true)) return
                        val instance = param.thisObject
                        Thread({
                            try {
                                (getGroup.invoke(instance, createRequest()) as Future<*>)
                                    .get(30, TimeUnit.SECONDS)
                            } catch (error: Exception) {
                                XposedBridge.log("PhoneHooks: Initial Call Screen MDD check deferred: $error")
                            }
                        }, "pcs-call-screen-model-check").start()
                    }
                })
            }
            XposedBridge.log("PhoneHooks: Call Screen MDD file validation installed")
        } catch (error: Exception) {
            XposedBridge.log("PhoneHooks: Unable to install Call Screen MDD file validation: $error")
        }
    }

    private fun requestFactory(type: Class<*>): () -> Any {
        val factory = type.declaredMethods.single {
            Modifier.isStatic(it.modifiers) && it.parameterCount == 0 &&
                it.returnType != Void.TYPE && !it.returnType.isPrimitive
        }
        val builder = factory.returnType
        val setName = builder.declaredMethods.single {
            it.parameterTypes.contentEquals(arrayOf(String::class.java)) && it.returnType == Void.TYPE
        }
        val build = builder.declaredMethods.single {
            it.parameterCount == 0 && it.returnType == type
        }
        return {
            val value = factory.invoke(null)
            setName.invoke(value, CallScreenModelFiles.GROUP)
            build.invoke(value)!!
        }
    }

    private fun createRepair(
        dexKit: DexKitBridge,
        classLoader: ClassLoader,
        client: Class<*>,
        getRequest: Class<*>,
        getGroup: Method
    ): (Any) -> Unit {
        val refresh = dexKit.findMethod {
            matcher { usingStrings("Unknown task tag sent to MDD.handleTask() ") }
        }.single().getMethodInstance(classLoader)
        require(refresh.declaringClass == client && refresh.parameterCount == 2 &&
            refresh.parameterTypes[0] == String::class.java &&
            Future::class.java.isAssignableFrom(refresh.returnType))
        // The native GetFileGroupRequest builder supplies absent optionals. Use that exact
        // native type rather than constructing an Optional from the module's class loader.
        val createGet = requestFactory(getRequest)
        val absent = getRequest.declaredFields.first {
            !Modifier.isStatic(it.modifiers) && it.type == refresh.parameterTypes[1]
        }.apply { isAccessible = true }.get(createGet())
        val downloadRequest = dexKit.findClass {
            matcher { usingStrings("DownloadFileGroupRequest{groupName=") }
        }.single().getInstance(classLoader)
        val createDownload = requestFactory(downloadRequest)
        val download = client.declaredMethods.filter {
            it.parameterTypes.contentEquals(arrayOf(downloadRequest)) &&
                Future::class.java.isAssignableFrom(it.returnType) &&
                client.interfaces.any { contract ->
                    contract.methods.any { api ->
                        api.name == it.name && api.parameterTypes.contentEquals(it.parameterTypes)
                    }
                }
        }
        // The standard and foreground-service APIs have identical types. Identify and exclude
        // the foreground-service variant by its diagnostic, not reflection ordering.
        val foreground = dexKit.findMethod {
            matcher {
                usingStrings("downloadFileGroupWithForegroundService: ForegroundDownloadService is not provided!")
            }
        }.single().getMethodInstance(classLoader)
        require(foreground in download)
        val eager = download.single { it != foreground }
        val active = AtomicBoolean()
        val lastAttempt = AtomicLong()
        return repair@{ instance ->
            val now = System.nanoTime()
            val previous = lastAttempt.get()
            if ((previous != 0L && now - previous < TimeUnit.MINUTES.toNanos(5)) ||
                !active.compareAndSet(false, true)) return@repair
            lastAttempt.set(now)
            Thread({
                try {
                    XposedBridge.log("PhoneHooks: Refreshing empty Call Screen MDD group through native maintenance")
                    (refresh.invoke(instance, "MDD.CHARGING.PERIODIC.TASK", absent) as Future<*>)
                        .get(90, TimeUnit.SECONDS)
                    (eager.invoke(instance, createDownload()) as Future<*>)
                        .get(180, TimeUnit.SECONDS)
                    val result = (getGroup.invoke(instance, createGet()) as Future<*>)
                        .get(30, TimeUnit.SECONDS)
                    XposedBridge.log("PhoneHooks: Native Call Screen model recovery complete; files available=${result != null}")
                } catch (error: Exception) {
                    XposedBridge.log("PhoneHooks: Native Call Screen model recovery pending or failed: $error")
                } finally {
                    active.set(false)
                }
            }, "pcs-call-screen-model-repair").start()
        }
    }
}
