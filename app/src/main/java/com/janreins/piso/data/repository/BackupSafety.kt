package com.janreins.piso.data.repository

import com.janreins.piso.data.models.BackupSummary
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

data class UndoRestoreInfo(val fileName: String, val summary: BackupSummary) {
    val action: String
        get() = if (fileName.startsWith("pre-clear-")) "clear" else "restore"

    val label: String
        get() = "Undo $action of ${summary.date}"
}

/** App-private, complete files only. Call from IO and serialize access with the DB transaction. */
class BackupSafety(
    private val directory: File,
    private val deleteFile: (File) -> Boolean = { it.delete() }
) {
    private fun copies(): List<File> = directory.listFiles()?.filter {
        it.isFile && it.name.matches(Regex("pre-(import|clear)-[0-9]{19}\\.json"))
    }?.sortedByDescending { it.name.takeLast(24) } ?: emptyList()

    private fun consumedMarker(file: File) = File(directory, "consumed-${file.name}")

    private fun backups() = copies().filterNot { consumedMarker(it).exists() }

    fun latestBackup(): File? = backups().firstOrNull()

    // Persist before changing data: cleanup failure (or process death after commit) cannot
    // expose the same undo again. The complete snapshot itself survives until after commit.
    fun markConsumed(file: File) {
        FileOutputStream(consumedMarker(file)).use { it.fd.sync() }
    }

    fun cancelConsumption(file: File) {
        if (!deleteFile(consumedMarker(file))) throw IOException("Cannot release safety backup")
    }

    fun deleteConsumed(file: File) {
        try {
            if (deleteFile(file) || !file.exists()) deleteFile(consumedMarker(file))
        } catch (_: IOException) {
            // Keep the marker so the committed undo remains one-shot.
        } catch (_: SecurityException) {
            // Keep the marker so the committed undo remains one-shot.
        }
    }

    fun save(json: String, beforeClear: Boolean = false): File {
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Cannot create safety backup directory")
        }
        val previousTime = copies().firstOrNull()?.name?.substringAfterLast('-')
            ?.removeSuffix(".json")?.toLongOrNull() ?: 0L
        var timestamp = maxOf(System.currentTimeMillis(), previousTime + 1)
        val prefix = if (beforeClear) "pre-clear-" else "pre-import-"
        var destination = File(directory, "$prefix${timestamp.toString().padStart(19, '0')}.json")
        while (consumedMarker(destination).exists()) {
            timestamp++
            destination = File(directory, "$prefix${timestamp.toString().padStart(19, '0')}.json")
        }
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
        val copies = copies().filterNot { it == except }
        // Invalidate every discarded copy before deletion, including on RESET. A failed
        // delete must not make old data available to a newly created profile.
        copies.forEach { markConsumed(it) }
        val pending = files.filter { it.name.startsWith("pending-") && it.name.endsWith(".tmp") }
        (copies + pending).forEach {
            if (!deleteFile(it)) throw IOException("Cannot delete safety backup")
        }
        directory.listFiles()?.filter { it.name.startsWith("consumed-pre-") &&
            !File(directory, it.name.removePrefix("consumed-")).exists() }?.forEach {
            if (!deleteFile(it)) throw IOException("Cannot delete consumed backup marker")
        }
    }

    fun deleteAllBestEffort(except: File? = null) {
        try {
            deleteAll(except)
        } catch (_: IOException) {
            // Cleanup must not turn an already committed data change into a failure.
        } catch (_: SecurityException) {
            // Storage may no longer permit cleanup.
        }
    }

    /** Prune only after the import commits, keeping the most recent three safety copies. */
    fun prune() {
        // Retention is best effort: cleanup must not turn a committed restore into a failure.
        try {
            copies().filter { consumedMarker(it).exists() }.forEach { deleteConsumed(it) }
            backups().drop(3).forEach { it.delete() }
        } catch (_: SecurityException) {
            // Leave extra copies if storage no longer permits cleanup.
        }
    }
}
