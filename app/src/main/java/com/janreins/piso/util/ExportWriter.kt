package com.janreins.piso.util

import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ExportWriter {
    /** Opening, writing and closing all run on IO; a missing stream is a failed export. */
    suspend fun write(text: String, openStream: () -> OutputStream?) = withContext(Dispatchers.IO) {
        val stream = openStream() ?: throw IOException("Cannot open export destination")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }
}
