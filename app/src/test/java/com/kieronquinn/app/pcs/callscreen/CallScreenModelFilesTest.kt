package com.kieronquinn.app.pcs.callscreen

import com.google.protobuf.ByteString
import com.kieronquinn.app.pcs.model.gacs.GacsLocalization.DownloadedCallScreenGroup
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

class CallScreenModelFilesTest {
    interface NativeFuture : Future<DownloadedCallScreenGroup?> {
        fun c(listener: Runnable, executor: Executor)
    }

    class Pending : CompletableFuture<DownloadedCallScreenGroup?>(), NativeFuture {
        override fun c(listener: Runnable, executor: Executor) {
            whenComplete { _, _ -> executor.execute(listener) }
        }
    }

    private val empty = DownloadedCallScreenGroup.newBuilder()
        .setName(CallScreenModelFiles.GROUP).setVersion(0).build()
    private val downloaded = empty.toBuilder().setVersion(1790632200)
        .addFiles(ByteString.copyFromUtf8("native-file-metadata")).build()

    private fun wrap(original: Pending, onEmpty: () -> Unit = {}) =
        CallScreenModelFiles.guard(NativeFuture::class.java, original, onEmpty) as NativeFuture

    @Test fun `named empty group is missing for both Future get overloads`() {
        val calls = AtomicInteger()
        val original = Pending().apply { complete(empty) }
        val guarded = wrap(original) { calls.incrementAndGet() }
        assertNull(guarded.get())
        assertNull(guarded.get(1, TimeUnit.SECONDS))
        assertSame(empty, original.get())
        assertEquals(2, calls.get())
    }

    @Test fun `downloaded group retains its native object and all fields`() {
        val guarded = wrap(Pending().apply { complete(downloaded) }) { fail("not empty") }
        assertSame(downloaded, guarded.get())
        assertSame(downloaded, guarded.get(1, TimeUnit.SECONDS))
        assertTrue(guarded.isDone)
    }

    @Test fun `native listener sees filtered result after asynchronous completion`() {
        val original = Pending()
        val guarded = wrap(original)
        val callbacks = AtomicInteger()
        guarded.c(Runnable {
            assertNull(guarded.get())
            callbacks.incrementAndGet()
        }, Executor { it.run() })
        assertFalse(guarded.isDone)
        assertThrows(TimeoutException::class.java) { guarded.get(1, TimeUnit.MILLISECONDS) }
        original.complete(empty)
        assertEquals(1, callbacks.get())
    }

    @Test fun `missing groups errors and cancellation preserve the Future contract`() {
        assertNull(wrap(Pending().apply { complete(null) }).get())
        val error = IllegalStateException("native download failed")
        val failed = wrap(Pending().apply { completeExceptionally(error) })
        assertSame(error, assertThrows(ExecutionException::class.java) { failed.get() }.cause)
        val original = Pending()
        val cancelled = wrap(original)
        assertTrue(cancelled.cancel(true))
        assertTrue(original.isCancelled)
        assertTrue(cancelled.isCancelled)
        assertThrows(CancellationException::class.java) { cancelled.get() }
    }
}
