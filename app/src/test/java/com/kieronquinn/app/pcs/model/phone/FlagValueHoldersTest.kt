package com.kieronquinn.app.pcs.model.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class FlagValueHoldersTest {

    // Like Dialer, metadata field order differs from constructor order. Equality deliberately
    // ignores identity so a normal HashMap would also mix distinct cached holders together.
    private class Holder(supplier: () -> Any, kind: Int, metadata: String?, enabled: Boolean) {
        val metadata = metadata
        val enabled = enabled
        val supplier = supplier
        val kind = kind

        override fun equals(other: Any?) = other is Holder
        override fun hashCode() = 0
    }

    @Test
    fun sharedDefaultsDoNotMixFlagsOrModifyUntrackedReads() {
        for (order in listOf(listOf("sharpie", "nautilus"), listOf("nautilus", "sharpie"))) {
            val registry = FlagValueHolders<String>(Holder::class.java)
            val sharedDefault = Holder({ false }, 2, null, false)
            val copies = order.associateWith { registry.isolate(it, sharedDefault) as Holder }
            val read: (Holder) -> Boolean = { holder ->
                if (registry.flagFor(holder) == "sharpie") true else holder.supplier() as Boolean
            }

            assertNotSame(copies.getValue("sharpie"), copies.getValue("nautilus"))
            assertTrue(read(copies.getValue("sharpie")))
            assertFalse(read(copies.getValue("nautilus")))
            assertFalse(read(sharedDefault))
            assertNull(registry.flagFor(sharedDefault))
        }
    }

    @Test
    fun copiesPreserveLiveSupplierAndMetadata() {
        val registry = FlagValueHolders<String>(Holder::class.java)
        var value = "before"
        val supplier = { value }
        val original = Holder(supplier, 7, "proto metadata", true)
        val copy = registry.isolate("manifest", original) as Holder

        assertNotSame(original, copy)
        assertSame(original.supplier, copy.supplier)
        assertEquals(7, copy.kind)
        assertEquals("proto metadata", copy.metadata)
        assertTrue(copy.enabled)
        value = "after"
        assertEquals("after", copy.supplier())
    }

    @Test
    fun cachesByFlagAndOriginalIdentity() {
        val registry = FlagValueHolders<String>(Holder::class.java)
        val old = Holder({ false }, 2, null, false)
        val updated = Holder({ true }, 2, null, false)
        val oldCopy = registry.isolate("sharpie", old)
        val updatedCopy = registry.isolate("sharpie", updated)

        assertSame(oldCopy, registry.isolate("sharpie", old))
        assertNotSame(oldCopy, updatedCopy)
        assertEquals("sharpie", registry.flagFor(oldCopy))
        assertEquals("sharpie", registry.flagFor(updatedCopy))
        assertNull(registry.flagFor(old))
        assertNull(registry.flagFor(updated))
    }

    @Test
    fun concurrentReadsKeepOneCopyPerFlag() {
        val registry = FlagValueHolders<String>(Holder::class.java)
        val original = Holder({ false }, 2, null, false)
        val executor = Executors.newFixedThreadPool(4)
        try {
            val copies = executor.invokeAll((0 until 100).map { i ->
                Callable {
                    val flag = if (i % 2 == 0) "sharpie" else "nautilus"
                    val copy = registry.isolate(flag, original)
                    assertEquals(flag, registry.flagFor(copy))
                    flag to copy
                }
            }).map { it.get() }
            copies.forEach { (flag, copy) ->
                assertSame(registry.isolate(flag, original), copy)
            }
        } finally {
            executor.shutdownNow()
        }
    }

}
