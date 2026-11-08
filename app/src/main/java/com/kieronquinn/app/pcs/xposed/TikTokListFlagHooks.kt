package com.kieronquinn.app.pcs.xposed

import com.kieronquinn.app.pcs.model.phone.FlagValueHolders
import com.kieronquinn.app.pcs.tiktok.TikTokListFlag
import com.kieronquinn.app.pcs.utils.extensions.loadDexKit
import com.kieronquinn.app.pcs.utils.extensions.reflectParseProto
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.IdentityHashMap

/**
 *  Adds the phone's entry to string list flags ([TikTokListFlag]) an app reads through TikTok's
 *  flag store, the way Phone reads its flags.
 *
 *  A creator takes the Mendel package and the flag name and returns a value holder, and the value
 *  is read from the holder. The creator is found from its log line and its (package, name)
 *  signature; the holder is its return type. Holders for [flags] are copied ([FlagValueHolders]),
 *  so the shared default holders other flags use are never changed, and each copy gets a value
 *  supplier that returns the served list with the phone's entry added.
 *
 *  @param entryFor the entry a flag of [mendelPackage] needs, read when the value is, or null for
 *  a flag that is left alone.
 */
class TikTokListFlagHooks(
    private val owner: XposedHooks,
    private val mendelPackage: String,
    private val flags: Set<String>,
    private val entryFor: (flag: String) -> String?
) {

    companion object {
        private const val BLOCKING_LOG = "Blocking for: "
    }

    /** The changed value per flag and original value, so every read returns the same instance. */
    private val values = HashMap<String, IdentityHashMap<Any, Any>>()
    private val applied = Collections.synchronizedSet(HashSet<String>())
    private val wrapped: MutableSet<Any> = Collections.newSetFromMap(IdentityHashMap())

    fun install(loadPackageParam: LoadPackageParam) {
        val classLoader = loadPackageParam.classLoader
        val creators = loadDexKit(loadPackageParam.appInfo.sourceDir).findMethod {
            matcher {
                usingStrings(BLOCKING_LOG)
                paramTypes(String::class.java.name, String::class.java.name)
            }
        }.mapNotNull { runCatching { it.getMethodInstance(classLoader) }.getOrNull() }
            .filter { !it.returnType.isPrimitive && it.returnType != String::class.java }
        check(creators.isNotEmpty()) { "No flag creator" }
        val holderClass = creators.map { it.returnType }.distinct().single()
        val holders = FlagValueHolders<String>(holderClass)
        creators.forEach { creator ->
            XposedBridge.hookMethod(creator, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.hasThrowable()) return
                    if (param.args[0] != mendelPackage) return
                    val flag = param.args[1] as? String ?: return
                    if (flag !in flags) return
                    val original = param.result ?: return
                    param.result = withChangedSupplier(flag, holders.isolate(flag, original))
                }
            })
        }
        check(supplierField(holderClass) != null) { "No value supplier on ${holderClass.name}" }
        owner.log("$mendelPackage list flags hooked on ${creators.size} creator(s), holder ${holderClass.name}")
    }

    /**
     *  The field holding the holder's value supplier: an interface whose single method takes no
     *  argument and returns the value (a Kotlin function or a TikTok supplier).
     */
    private fun supplierField(holderClass: Class<*>): Field? {
        return holderClass.declaredFields.singleOrNull {
            !Modifier.isStatic(it.modifiers) && it.type.isInterface && supplierMethod(it.type) != null
        }?.apply { isAccessible = true }
    }

    private fun supplierMethod(type: Class<*>): Method? {
        return type.methods.singleOrNull {
            Modifier.isAbstract(it.modifiers) && it.parameterCount == 0 && it.returnType == Any::class.java
        }
    }

    /**
     *  Gives [holder], a copy only this flag uses, a supplier that returns the served value with
     *  the phone's entry. The holder's accessors are small enough to be inlined into their callers,
     *  so the value is changed where they read it instead of hooking them.
     */
    private fun withChangedSupplier(flag: String, holder: Any): Any {
        synchronized(wrapped) {
            if (!wrapped.add(holder)) return holder
        }
        val field = supplierField(holder.javaClass) ?: return holder
        val original = field.get(holder) ?: return holder
        val get = supplierMethod(field.type) ?: return holder
        val supplier = Proxy.newProxyInstance(field.type.classLoader, arrayOf(field.type)) { _, method, args ->
            val value = method.invoke(original, *(args ?: emptyArray()))
            if (method.name != get.name || method.parameterCount != 0 || value == null) return@newProxyInstance value
            try {
                changedValue(flag, value)?.also {
                    if (applied.add(flag)) owner.log("Added the phone's entry to $flag")
                } ?: value
            } catch (error: Throwable) {
                if (applied.add(flag)) owner.log("Unable to change $flag: $error")
                value
            }
        }
        field.set(holder, supplier)
        return holder
    }

    private fun changedValue(flag: String, original: Any): Any? {
        val entry = entryFor(flag) ?: return null
        return synchronized(values) {
            val changed = values.getOrPut("$flag/$entry") { IdentityHashMap() }
            changed[original] ?: withEntry(original, entry)?.also { changed[original] = it }
        }
    }

    /** The served list with [entry], or null when it is listed or cannot be changed. */
    private fun withEntry(original: Any, entry: String): Any? {
        val bytes = VoicePackHooks.serialize(original) ?: return null
        return TikTokListFlag.withEntry(bytes, entry)?.reflectParseProto(original.javaClass)
    }

}
