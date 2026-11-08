package com.kieronquinn.app.pcs.callscreen

import com.kieronquinn.app.pcs.model.gacs.GacsLocalization.DownloadedCallScreenGroup
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.concurrent.Future

/** Preserve the native ListenableFuture contract, but treat an empty MDD group as missing. */
internal object CallScreenModelFiles {
    const val GROUP = "dobby-duplex-files"

    fun hasFiles(bytes: ByteArray): Boolean =
        DownloadedCallScreenGroup.parseFrom(bytes).filesCount > 0

    fun guard(futureType: Class<*>, original: Future<*>, onEmpty: () -> Unit): Any {
        require(futureType.isInterface && Future::class.java.isAssignableFrom(futureType))
        return Proxy.newProxyInstance(futureType.classLoader, arrayOf(futureType)) { proxy, method, args ->
            when {
                method.name == "equals" -> proxy === args?.firstOrNull()
                method.name == "hashCode" -> System.identityHashCode(proxy)
                else -> {
                    val result = try {
                        method.invoke(original, *args.orEmpty())
                    } catch (error: InvocationTargetException) {
                        throw error.targetException
                    }
                    if (method.name != "get" || result == null) {
                        result
                    } else {
                        val serialize = result.javaClass.methods.single {
                            it.parameterCount == 0 && it.returnType == ByteArray::class.java
                        }.apply { isAccessible = true }
                        val bytes = serialize.invoke(result) as ByteArray
                        if (hasFiles(bytes)) result else {
                            onEmpty()
                            null
                        }
                    }
                }
            }
        }
    }
}
