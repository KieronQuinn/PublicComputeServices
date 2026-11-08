package com.kieronquinn.app.pcs.model.phone

import java.lang.reflect.Modifier
import java.util.IdentityHashMap

/**
 * Dialer shares default value holders between unrelated flags. Give each overridden flag its own
 * holder, preserving the original value supplier and metadata without modifying Dialer's defaults.
 */
internal class FlagValueHolders<F : Any>(holderClass: Class<*>) {

    private val fields = holderClass.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }
    private val constructor = holderClass.declaredConstructors.singleOrNull { constructor ->
        val types = constructor.parameterTypes.toList()
        types.size == fields.size && types.distinct().size == types.size &&
                types.all { type -> fields.count { it.type == type } == 1 }
    } ?: error("Unable to copy ${holderClass.name}: ambiguous holder constructor or fields")
    private val constructorFields = constructor.parameterTypes.map { type ->
        fields.single { it.type == type }.apply { isAccessible = true }
    }
    private val copies = mutableMapOf<F, IdentityHashMap<Any, Any>>()
    private val flags = IdentityHashMap<Any, F>()

    init {
        constructor.isAccessible = true
    }

    @Synchronized
    fun isolate(flag: F, original: Any): Any {
        val holders = copies.getOrPut(flag) { IdentityHashMap() }
        return holders.getOrPut(original) {
            constructor.newInstance(*constructorFields.map { it.get(original) }.toTypedArray())
                .also { flags[it] = flag }
        }
    }

    @Synchronized
    fun flagFor(holder: Any): F? = flags[holder]

}
