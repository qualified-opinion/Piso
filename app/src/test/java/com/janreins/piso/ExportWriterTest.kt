package com.janreins.piso

import com.janreins.piso.util.ExportWriter
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportWriterTest {
    @Test
    fun nullStreamFails() = runBlocking {
        val failure = runCatching { ExportWriter.write("backup") { null } }.exceptionOrNull()
        assertTrue(failure is IOException)
    }

    @Test
    fun writesUtf8ClosesAndOpensOnIo() = runBlocking {
        val caller = Thread.currentThread()
        var closed = false
        val stream = object : ByteArrayOutputStream() {
            override fun close() { closed = true; super.close() }
        }
        ExportWriter.write("Piso ₱") {
            assertNotEquals(caller, Thread.currentThread())
            stream
        }
        assertEquals("Piso ₱", stream.toString("UTF-8"))
        assertTrue(closed)
    }

    @Test
    fun writeAndCloseErrorsAreFailures() = runBlocking {
        val brokenWrite = object : OutputStream() {
            override fun write(value: Int) { throw IOException("Full disk") }
        }
        assertTrue(runCatching { ExportWriter.write("backup") { brokenWrite } }.exceptionOrNull() is IOException)
        val brokenClose = object : ByteArrayOutputStream() {
            override fun close() { throw IOException("Close failed") }
        }
        assertTrue(runCatching { ExportWriter.write("backup") { brokenClose } }.exceptionOrNull() is IOException)
    }
}
