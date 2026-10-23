package com.janreins.piso

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.janreins.piso.data.local.AppDatabase
import com.janreins.piso.data.models.Account
import com.janreins.piso.data.models.BackupData
import com.janreins.piso.data.repository.BackupSafety
import com.janreins.piso.data.repository.FinanceRepository
import com.janreins.piso.ui.settings.SettingsViewModel
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.SequenceInputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsImportTest {
    private lateinit var database: AppDatabase
    private var previous: AppDatabase? = null
    private val store = ViewModelStore()
    private lateinit var viewModel: SettingsViewModel
    private lateinit var safety: BackupSafety
    private val limit = 20 * 1024 * 1024
    private val backup = BackupData(listOf(Account(id = 1, name = "Wallet", kind = "Cash", balance = 10.0)),
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        val application = ApplicationProvider.getApplicationContext<Application>()
        safety = BackupSafety(File(application.filesDir, "backups"))
        safety.deleteAll()
        database = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).allowMainThreadQueries().build()
        previous = ReflectionHelpers.getStaticField(AppDatabase::class.java, "INSTANCE")
        ReflectionHelpers.setStaticField(AppDatabase::class.java, "INSTANCE", database)
        viewModel = SettingsViewModel(application).also { store.put("settings", it) }
    }

    @After
    fun tearDown() {
        store.clear()
        safety.deleteAll()
        ReflectionHelpers.setStaticField(AppDatabase::class.java, "INSTANCE", previous)
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun validFileIsOpenedAndReadOffMainClosedAndHeldWithSummary() = runTest {
        val main = Thread.currentThread()
        var closed = false
        val stream = object : ByteArrayInputStream(backup.toJsonString().toByteArray()) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                assertNotSame(main, Thread.currentThread())
                return super.read(b, off, len)
            }
            override fun close() { closed = true; super.close() }
        }
        viewModel.reviewBackup {
            assertNotSame(main, Thread.currentThread())
            stream
        }
        assertTrue(viewModel.backupImportState.value.isLoading)
        val state = viewModel.backupImportState.first { !it.isLoading }
        assertNull(state.error)
        assertEquals(backup, state.data)
        assertEquals(backup.summary, state.summary)
        assertTrue(closed)
        // Confirmation consumes the parsed object even though the source is now closed.
        val restored = CompletableDeferred<Boolean>()
        viewModel.importBackup { restored.complete(it) }
        assertTrue(restored.await())
        assertEquals(backup.accounts, FinanceRepository(database).allAccounts.first())
    }

    @Test
    fun invalidJsonClearsOldPreviewAndGivesErrorState() = runTest {
        viewModel.reviewBackup { backup.toJsonString().byteInputStream() }
        viewModel.backupImportState.first { !it.isLoading }
        viewModel.reviewBackup { "not JSON".byteInputStream() }
        val state = viewModel.backupImportState.first { !it.isLoading }
        assertEquals("This is not a valid Piso backup.", state.error)
        assertNull(state.data)
        assertNull(state.summary)
    }

    @Test
    fun oversizedStreamIsRejectedAfterAtMostLimitPlusOneBytes() = runTest {
        var bytesRead = 0
        var closed = false
        val stream = object : InputStream() {
            override fun read(): Int { bytesRead++; return 32 }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                b.fill(32.toByte(), off, off + len)
                bytesRead += len
                return len
            }
            override fun close() { closed = true }
        }
        viewModel.reviewBackup { stream }
        val state = viewModel.backupImportState.first { !it.isLoading }
        assertTrue(requireNotNull(state.error).contains("20 MB"))
        assertNull(state.data)
        assertNull(state.summary)
        assertTrue(bytesRead <= limit + 1)
        assertTrue(closed)
    }

    @Test
    fun validBackupAtExactlyTwentyMegabytesIsAccepted() = runTest {
        val json = backup.toJsonString().toByteArray()
        viewModel.reviewBackup {
            SequenceInputStream(ByteArrayInputStream(json), ByteArrayInputStream(ByteArray(limit - json.size) { 32 }))
        }
        val state = viewModel.backupImportState.first { !it.isLoading }
        assertNull(state.error)
        assertEquals(backup.summary, state.summary)
    }

    @Test
    fun unreadableFileGivesErrorState() = runTest {
        viewModel.reviewBackup { throw java.io.IOException("unreadable") }
        val state = viewModel.backupImportState.first { !it.isLoading }
        assertNotNull(state.error)
        assertNull(state.data)
    }
}
