package com.kieronquinn.app.pcs.xposed

import com.kieronquinn.app.pcs.directmycall.MenuParser
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Locale

/**
 *  Gives Direct My Call buttons for phone menus in the phone's language.
 *
 *  `TimingPuncParserDecorator` turns the menu transcript Phone has not handled yet into
 *  `ParsedKeyOption`s; its parsers only read English, so a Turkish menu returns none. When it
 *  returns none, the transcript is read with the [MenuParser] for the phone's language and the
 *  options are built as Phone's own `ParsedKeyOption(IvrOption)` objects, which Phone shows and
 *  dials as usual. English menus are left to Phone.
 *
 *  Phone's classes are found with DexKit from their log tag and `toString` text; their fields from
 *  the protobuf structure (IvrOption: key, action, text, span).
 */
internal object DirectMyCallMenuHooks {

    private const val TAG = "PhoneHooks"
    private const val DECORATOR =
        "com/google/quality/views/extraction/kcube/bg/ondevice/modules/vc/TimingPuncParserDecorator"
    private const val PARSED_KEY_OPTION = "ParsedKeyOption{ivrOption="

    fun install(dexKit: DexKitBridge, classLoader: ClassLoader, phoneLocale: Locale) {
        val parser = MenuParser.forLocale(phoneLocale) ?: run {
            log("Direct My Call has no menu parser for ${phoneLocale.toLanguageTag()}")
            return
        }
        try {
            // The log tag is also used by a lambda class; the decorator is the one with
            // parse(AnnotatedText, Span, int offset): ImmutableList<ParsedKeyOption>.
            val parse = dexKit.findClass { matcher { usingStrings(DECORATOR) } }
                .map { it.getInstance(classLoader) }
                .mapNotNull { type ->
                    type.declaredMethods.singleOrNull {
                        !Modifier.isStatic(it.modifiers) && it.parameterCount == 3 &&
                            it.parameterTypes[2] == Int::class.javaPrimitiveType &&
                            List::class.java.isAssignableFrom(it.returnType)
                    }
                }.single()
            val parsedKeyOption = dexKit.findClass { matcher { usingStrings(PARSED_KEY_OPTION) } }
                .single().getInstance(classLoader)
            val options = OptionFactory(parse, parsedKeyOption)
            XposedBridge.hookMethod(parse, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if ((param.result as? List<*>)?.isNotEmpty() == true) return
                    try {
                        val annotatedText = param.args[0] ?: return
                        val span = param.args[1] ?: return
                        val offset = param.args[2] as Int
                        val text = options.text(annotatedText) ?: return
                        val (start, end) = options.range(span, text.length)
                        val found = parser.parse(text, start, end)
                        if (found.isEmpty()) return
                        param.result = options.list(found.map { options.build(annotatedText, text, it, offset) })
                        log("Direct My Call read ${found.size} menu option(s) in ${phoneLocale.toLanguageTag()}")
                    } catch (error: Throwable) {
                        log("Direct My Call menu parsing failed: $error")
                    }
                }
            })
            log("Direct My Call menu parser installed for ${phoneLocale.toLanguageTag()}")
        } catch (error: Throwable) {
            log("Direct My Call menu parser unavailable: $error")
        }
    }

    /** Builds Phone's option objects; every member is resolved once, at install. */
    private class OptionFactory(parse: Method, parsedKeyOption: Class<*>) {

        private val annotatedTextClass = parse.parameterTypes[0]
        private val spanClass = parse.parameterTypes[1]
        private val listClass = parse.returnType

        private val textField = annotatedTextClass.instanceFields(String::class.java).first()
        /** The transcript's word annotations (timing, confidence) for a span of it, if Phone has the method. */
        private val annotationsFor = annotatedTextClass.declaredMethods.firstOrNull {
            !Modifier.isStatic(it.modifiers) && it.returnType == listClass &&
                it.parameterTypes.contentEquals(arrayOf(spanClass))
        }?.apply { isAccessible = true }
        private val spanInts = spanClass.instanceFields(Int::class.javaPrimitiveType!!).also {
            check(it.size >= 2) { "Span has no start and end" }
        }
        private val newSpan = spanClass.staticMethod(spanClass, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)
        private val copyOf = listClass.staticMethod(listClass, Collection::class.java)
            ?: error("No ImmutableList.copyOf")

        private val newParsedKeyOption: Constructor<*> = parsedKeyOption.declaredConstructors
            .single { it.parameterCount == 1 }.apply { isAccessible = true }
        private val ivrOptionClass = newParsedKeyOption.parameterTypes[0]
        private val ivrOption = ProtoMessage(ivrOptionClass)
        private val ivrStrings = ivrOptionClass.instanceFields(String::class.java).also {
            check(it.size >= 2) { "IvrOption has no key and action" }
        }
        private val ivrMessages = ivrOptionClass.declaredFields.filter {
            !Modifier.isStatic(it.modifiers) && !it.type.isPrimitive && it.type != String::class.java
        }.onEach { it.isAccessible = true }
        /** The option's text: a message with a string. */
        private val ivrText = ivrMessages.first { it.type.instanceFields(String::class.java).isNotEmpty() }
        /** The option's span: a message with a has-bits int, a start and an end. */
        private val ivrSpan = ivrMessages.first { it.type.instanceFields(Int::class.javaPrimitiveType!!).size >= 3 }
        private val protoText = ProtoMessage(ivrText.type)
        private val protoTextString = ivrText.type.instanceFields(String::class.java).first()
        private val protoTextList = ivrText.type.declaredFields.firstOrNull {
            !Modifier.isStatic(it.modifiers) && List::class.java.isAssignableFrom(it.type)
        }?.apply { isAccessible = true }
        /** The generated method that makes a repeated field mutable before adding to it. */
        private val protoTextMutableList = ivrText.type.declaredMethods.firstOrNull {
            !Modifier.isStatic(it.modifiers) && it.parameterCount == 0 && it.returnType == Void.TYPE
        }?.apply { isAccessible = true }
        private val protoSpan = ProtoMessage(ivrSpan.type)
        private val protoSpanInts = ivrSpan.type.instanceFields(Int::class.javaPrimitiveType!!)

        fun text(annotatedText: Any) = textField.get(annotatedText) as? String

        fun range(span: Any, length: Int): Pair<Int, Int> {
            val start = spanInts[0].getInt(span).coerceIn(0, length)
            val end = spanInts[1].getInt(span).coerceIn(start, length)
            return start to end
        }

        fun list(items: List<Any>): Any = copyOf.invoke(null, items)!!

        fun build(annotatedText: Any, text: String, option: MenuParser.Option, offset: Int): Any {
            // Span(has-bits: start | end, start, end), in the call's transcript.
            val span = protoSpan.newInstance()
            protoSpanInts[0].setInt(span, protoSpanInts[0].getInt(span) or 0b11)
            protoSpanInts[1].setInt(span, option.start + offset)
            protoSpanInts[2].setInt(span, option.end + offset)
            // AnnotatedText(has-bits: text, text, word annotations).
            val optionText = protoText.newInstance()
            protoText.setBits(optionText, 0b1)
            protoTextString.set(optionText, text.substring(option.start, option.end).trim())
            annotations(annotatedText, option)?.let { words ->
                protoTextMutableList?.invoke(optionText)
                @Suppress("UNCHECKED_CAST")
                (protoTextList?.get(optionText) as? MutableList<Any?>)?.addAll(words)
            }
            // IvrOption(has-bits: key 1, action 4, text 8, span 16).
            val ivr = ivrOption.newInstance()
            ivrOption.setBits(ivr, 1 or 4 or 8 or 16)
            ivrStrings[0].set(ivr, option.key)
            ivrStrings[1].set(ivr, option.action)
            ivrText.set(ivr, optionText)
            ivrSpan.set(ivr, span)
            return newParsedKeyOption.newInstance(ivr)
        }

        private fun annotations(annotatedText: Any, option: MenuParser.Option): Collection<*>? {
            val annotationsFor = annotationsFor ?: return null
            val newSpan = newSpan ?: return null
            return (annotationsFor.invoke(annotatedText, newSpan.invoke(null, option.start, option.end)) as? Collection<*>)
                ?.takeIf { it.isNotEmpty() }
        }
    }

    /** A protobuf-lite message type: new builders come from its default instance. */
    private class ProtoMessage(type: Class<*>) {
        private val defaultInstance = type.declaredFields.first {
            Modifier.isStatic(it.modifiers) && it.type == type
        }.apply { isAccessible = true }.get(null)
        /** GeneratedMessageLite.dynamicMethod(int, Object); 4 is NEW_MUTABLE_INSTANCE. */
        private val dynamicMethod = type.superclass.declaredMethods.first {
            !Modifier.isStatic(it.modifiers) && it.returnType == Any::class.java &&
                it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType, Any::class.java))
        }.apply { isAccessible = true }
        private val bits = type.instanceFields(Int::class.javaPrimitiveType!!).first()

        fun newInstance(): Any = dynamicMethod.invoke(defaultInstance, 4, null)!!

        fun setBits(message: Any, mask: Int) = bits.setInt(message, bits.getInt(message) or mask)
    }

    private fun Class<*>.instanceFields(type: Class<*>): List<Field> = declaredFields.filter {
        !Modifier.isStatic(it.modifiers) && it.type == type
    }.onEach { it.isAccessible = true }

    private fun Class<*>.staticMethod(returns: Class<*>, vararg parameters: Class<*>): Method? =
        declaredMethods.firstOrNull {
            Modifier.isStatic(it.modifiers) && it.returnType == returns && it.parameterTypes.contentEquals(parameters)
        }?.apply { isAccessible = true }

    private fun log(message: String) = XposedBridge.log("$TAG: $message")

}
