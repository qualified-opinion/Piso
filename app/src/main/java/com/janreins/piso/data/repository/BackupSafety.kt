package com.janreins.piso.data.repository

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** App-private, complete files only. Call from IO and serialize access with the DB transaction. */
class BackupSafety(private val directory: File) {
    private fun backups(): List<File> = directory.listFiles()?.filter {
        it.isFile && it.name.matches(Regex("pre-import-[0-9]{19}\\.json"))
    }?.sortedByDescending { it.name } ?: emptyList()

    fun latestBackup(): File? = backups().firstOrNull()

    fun save(json: String): File {
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Cannot create safety backup directory")
        }
        val previousTime = latestBackup()?.name?.removePrefix("pre-import-")
            ?.removeSuffix(".json")?.toLongOrNull() ?: 0L
        val timestamp = maxOf(System.currentTimeMillis(), previousTime + 1)
        val destination = File(directory, "pre-import-${timestamp.toString().padStart(19, '0')}.json")
        val temporary = File.createTempFile("pending-", ".tmp", directory)
        try {
            FileOutputStream(temporary).use {
                it.write(json.toByteArray(Charsets.UTF_8))
                it.flush()
                it.fd.sync()
            }
            if (!temporary.renameTo(destination)) throw IOException("Cannot finish safety backup")
            return destination
        } finally {
            temporary.delete()
        }
    }

    /** Prune only after the import commits, keeping the most recent three safety copies. */
    fun prune() {
        // Retention is best effort: cleanup must not turn a committed restore into a failure.
        try {
            backups().drop(3).forEach { it.delete() }
        } catch (_: SecurityException) {
            // Leave extra copies if storage no longer permits cleanup.
        }
    }
}
