package com.kieronquinn.app.pcs.xposed

import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Locate the shared mode selector, not the final result which also checks SDK and Direct Boot. */
internal object FermatFeatureResolver {
    // Internal selector enum in Dialer 238/242: 4 = notes with summaries, 5 = notes only,
    // 6 = recording, 3 = unavailable. These are NOT the old supportedFeatures flag values.
    const val CALL_NOTES_WITH_SUMMARIES = 4

    fun find(dexKit: DexKitBridge, classLoader: ClassLoader): Method {
        val resolver = dexKit.findClass {
            matcher {
                usingStrings("com/android/dialer/fermat/enabledfn/FermatFeatureResolver")
            }
        }.singleOrNull() ?: error("Fermat feature resolver not found")
        val type = resolver.getInstance(classLoader)
        val callers = listOf("isCallNotesEnabled", "isCallRecordingEnabled", "supportsSummarization")
            .map { marker ->
                resolver.findMethod {
                    matcher { usingStrings(marker) }
                }.singleOrNull() ?: error("Fermat resolver method missing or ambiguous: $marker")
            }
        return selectModeMethod(type, callers.map { caller ->
            caller.invokes.filter { it.declaredClassName == resolver.name }
                .map { it.getMethodInstance(classLoader) }
        })
    }

    internal fun selectModeMethod(type: Class<*>, callers: List<List<Method>>): Method {
        require(callers.size == 3)
        val shared = callers.map { it.toSet() }.reduce { first, next -> first intersect next }
        return shared.singleOrNull {
            it.declaringClass == type && Modifier.isPrivate(it.modifiers) &&
                !Modifier.isStatic(it.modifiers) && it.parameterCount == 0 &&
                it.returnType == Int::class.javaPrimitiveType
        } ?: error("Unsupported Fermat mode selector in ${type.name}")
    }
}
