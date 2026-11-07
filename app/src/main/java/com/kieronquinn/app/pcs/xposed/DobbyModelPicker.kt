package com.kieronquinn.app.pcs.xposed

import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Constructor

/** The model ZIP version is data, not a stable identifier for the picker. */
internal class DobbyModelPicker private constructor(val constructor: Constructor<*>) {
    val localeProvider: Class<*> = constructor.parameterTypes[0]
    val flagType: Class<*> = constructor.parameterTypes[2]
    private val regionStart = constructor.parameterCount - 7

    fun overrideProviders(
        args: Array<Any?>,
        agentic: Any,
        regionOffset: Int,
        enabled: Any,
        disabled: Any
    ) {
        require(args.size == constructor.parameterCount && regionOffset in 0..6)
        // 238: agentic + manual-screening flags; 242 removed the manual-screening flag.
        for (index in 2 until regionStart) args[index] = agentic
        for (offset in 0..6) args[regionStart + offset] =
            if (offset == regionOffset) enabled else disabled
    }

    companion object {
        fun find(dexKit: DexKitBridge, classLoader: ClassLoader): DobbyModelPicker {
            val type = dexKit.findClass {
                matcher {
                    usingStrings("dobby-duplex-files", "dobbySingletonLocaleProvider",
                        "downloadPath", "enableAgenticCallScreen", "isUserInUs", "isUserInIn")
                }
            }.singleOrNull()?.getInstance(classLoader) ?: error("Dobby model picker not found")
            return resolve(type)
        }

        fun resolve(type: Class<*>): DobbyModelPicker {
            val constructor = type.declaredConstructors.singleOrNull {
                val types = it.parameterTypes
                types.size in 10..11 && !types[0].isInterface &&
                    types[1].isInterface && types.drop(1).distinct().size == 1 &&
                    types[1].methods.any { method ->
                        method.parameterCount == 0 && method.returnType == Any::class.java
                    }
            } ?: error("Unsupported Dobby model picker constructor in ${type.name}")
            return DobbyModelPicker(constructor)
        }
    }
}
