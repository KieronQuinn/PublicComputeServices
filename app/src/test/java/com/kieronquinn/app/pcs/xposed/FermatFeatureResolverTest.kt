package com.kieronquinn.app.pcs.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.lang.reflect.Method

class FermatFeatureResolverTest {
    // Both int-returning methods exist in 238/242. Hooking the public result would skip
    // the native SDK/Direct Boot checks; only the shared private selector is eligible.
    class Resolver {
        fun result() = 1
        private fun mode() = 3
        private fun unrelated() = 0
        private fun withArgument(value: Int) = value
        private fun booleanGate() = true
        companion object { @JvmStatic private fun staticMode() = 3 }
    }

    private fun method(name: String): Method = Resolver::class.java.declaredMethods.single { it.name == name }
    private fun select(vararg calls: List<Method>) =
        FermatFeatureResolver.selectModeMethod(Resolver::class.java, calls.toList())

    @Test fun `select shared mode without replacing the final availability result`() {
        val mode = method("mode")
        val extra = method("unrelated")
        assertEquals(mode, select(
            listOf(mode, method("result"), extra),
            listOf(method("result"), mode),
            listOf(mode, extra, method("result"))
        ))
    }

    @Test fun `selector must feed notes recording and summarization together`() {
        val mode = method("mode")
        assertThrows(IllegalStateException::class.java) {
            select(listOf(mode), listOf(mode), listOf(method("unrelated")))
        }
    }

    @Test fun `ambiguous shared selectors are rejected`() {
        val methods = listOf(method("mode"), method("unrelated"))
        assertThrows(IllegalStateException::class.java) { select(methods, methods, methods) }
    }

    @Test fun `wrong signatures cannot become mode hooks`() {
        for (name in listOf("result", "staticMode", "withArgument", "booleanGate")) {
            val methods = listOf(method(name))
            assertThrows(IllegalStateException::class.java) { select(methods, methods, methods) }
        }
    }

    @Test fun `method owned by another resolver is rejected`() {
        val methods = listOf(method("mode"))
        assertThrows(IllegalStateException::class.java) {
            FermatFeatureResolver.selectModeMethod(String::class.java, listOf(methods, methods, methods))
        }
    }
}
