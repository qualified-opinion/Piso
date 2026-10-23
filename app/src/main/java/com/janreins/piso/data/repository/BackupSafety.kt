package com.janreins.piso.data.repository

import com.janreins.piso.data.models.BackupSummary
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

data class UndoRestoreInfo(val fileName: String, val summary: BackupSummary) {
    val label: String
        get() = "Undo ${if (fileName.startsWith("pre-clear-")) "clear" else "restore"} of ${summary.date}"
}

/** App-private, complete files only. Call from IO and serialize access with the DB transaction. */
class BackupSafety(private val directory: File) {
    private fun backups(): List<File> = directory.listFiles()?.filter {
        it.isFile && it.name.matches(Regex("pre-(import|clear)-[0-9]{19}\\.json"))
    }?.sortedByDescending { it.name.takeLast(24) } ?: emptyList()

    fun latestBackup(): File? = backups().firstOrNull()

    fun save(json: String, beforeClear: Boolean = false): File {
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Cannot create safety backup directory")
        }
        val previousTime = latestBackup()?.name?.substringAfterLast('-')
            ?.removeSuffix(".json")?.toLongOrNull() ?: 0L
        val timestamp = maxOf(System.currentTimeMillis(), previousTime + 1)
        val prefix = if (beforeClear) "pre-clear-" else "pre-import-"
        val destination = File(directory, "$prefix${timestamp.toString().padStart(19, '0')}.json")
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

    /** Reset removes complete snapshots and interrupted writes; clear can retain its new copy. */
    fun deleteAll(except: File? = null) {
        val files = directory.listFiles() ?: if (!directory.exists()) return
            else throw IOException("Cannot list safety backups")
        val copies = backups().toSet()
        files.filter { it != except && (it in copies ||
            (it.name.startsWith("pending-") && it.name.endsWith(".tmp"))) }.forEach {
            if (!it.delete()) throw IOException("Cannot delete safety backup")
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
