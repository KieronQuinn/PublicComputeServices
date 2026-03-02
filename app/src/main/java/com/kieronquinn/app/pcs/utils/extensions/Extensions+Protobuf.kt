package com.kieronquinn.app.pcs.utils.extensions

/**
 *  Generic Protobuf parser which takes the type of the Protobuf message and parses it using
 *  reflection. This is used when we don't have the spec or when we need a specific return type
 *  that cannot be reparsed with our own message.
 */
fun ByteArray.reflectParseProto(protoClass: Class<*>): Any? {
    val defaultInstance = protoClass.fields.first { it.type == protoClass }.get(null)
    val parent = protoClass.superclass
    val creator = parent.methods.first {
        it.parameterCount == 5 && it.parameterTypes.first() == parent
    }
    val defaultCreator = creator.parameterTypes.last().let {
        it.fields.first { f -> f.type == it }.get(null)
    }
    return creator.invoke(null, defaultInstance, this, 0, this.size, defaultCreator)
}