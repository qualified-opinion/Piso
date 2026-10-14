package com.janreins.piso

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.janreins.piso.data.local.AppDatabase
import com.janreins.piso.data.models.*
import com.janreins.piso.data.repository.BackupSafety
import com.janreins.piso.data.repository.FinanceRepository
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRestoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private lateinit var database: AppDatabase
    private lateinit var repository: FinanceRepository
    private lateinit var safety: BackupSafety
    private lateinit var directory: File
    private var observeQueries = false
    private var onDeleteAccounts: (() -> Unit)? = null
    private val coreTables = setOf("accounts", "transactions", "budgets", "goals", "debts",
        "investments", "user_categories", "user_subcategories")
    private val transactionReads = mutableMapOf<String, Boolean>()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        directory = temporaryFolder.newFolder("backups")
        safety = BackupSafety(directory)
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCallback({ sql, _ ->
                if (sql == "DELETE FROM accounts") onDeleteAccounts?.invoke()
                val table = sql.removePrefix("SELECT * FROM ").substringBefore(' ')
                if (observeQueries && sql.startsWith("SELECT * FROM ") && table in coreTables) {
                    transactionReads[table] = database.inTransaction()
                }
            }, { it.run() })
            .build()
        repository = FinanceRepository(database, safety)
    }

    @After
    fun tearDown() { database.close() }

    private fun fixture(name: String) = BackupData(
        accounts = listOf(Account(id = 1, name = name, kind = "Cash", balance = 900.0)),
        transactions = listOf(Transaction(id = 1, dateMillis = 1234, type = "INCOME", amount = 100.0, accountId = 1)),
        budgets = listOf(Budget(id = 1, category = name, limitAmount = 500.0, monthKey = "2026-10")),
        goals = listOf(Goal(id = 1, name = name, targetAmount = 1000.0, currentAmount = 50.0)),
        debts = listOf(Debt(id = 1, name = name, kind = "Other", originalAmount = 500.0, remainingAmount = 300.0)),
        investments = listOf(Investment(id = 1, name = name, kind = "Stocks", currentValue = 300.0, lastUpdatedMillis = 1234)),
        categories = listOf(UserCategory(id = 1, name = name, kind = "EXPENSE")),
        subcategories = listOf(UserSubcategory(id = 1, parentCategoryName = name, name = "Sub"))
    )

    private suspend fun snapshot(): BackupData = BackupData.fromJsonString(repository.exportBackupJson())!!

    private fun assertSameData(expected: BackupData, actual: BackupData) {
        assertEquals(expected.copy(exportedAt = null), actual.copy(exportedAt = null))
    }

    @Test
    fun exportReadsEveryTableInTransactionAndRoundTrips() = runBlocking {
        val original = fixture("Prior")
        assertTrue(repository.importBackupJson(original.toJsonString()))
        observeQueries = true
        val json = repository.exportBackupJson()
        observeQueries = false
        assertEquals(coreTables, transactionReads.keys)
        assertTrue(transactionReads.values.all { it })
        assertSameData(original, BackupData.fromJsonString(json)!!)
        assertTrue(repository.importBackupJson(json))
        assertSameData(original, snapshot())
    }

    @Test
    fun savesPriorDataBeforeDeletingAndUndoRestoresAllTables() = runBlocking {
        val prior = fixture("Prior")
        assertTrue(repository.importBackupJson(prior.toJsonString()))
        // At the first DELETE, the safety file must already contain the complete prior state.
        var checkedBeforeDelete = false
        onDeleteAccounts = {
            assertSameData(prior, BackupData.fromJsonString(safety.latestBackup()!!.readText())!!)
            checkedBeforeDelete = true
        }
        assertTrue(repository.importBackupJson(fixture("Imported").toJsonString()))
        onDeleteAccounts = null
        assertTrue(checkedBeforeDelete)
        assertSameData(prior, BackupData.fromJsonString(safety.latestBackup()!!.readText())!!)
        assertTrue(repository.hasUndoRestore())
        // A new instance still finds the app-private undo file.
        assertTrue(FinanceRepository(database, BackupSafety(directory)).undoRestore())
        assertSameData(prior, snapshot())
    }

    @Test
    fun unwritableSafetyDirectoryAbortsWithoutChanges() = runBlocking {
        assertTrue(repository.importBackupJson(fixture("Prior").toJsonString()))
        val before = snapshot()
        val blocked = temporaryFolder.newFile("not-a-directory")
        val unsafeRepository = FinanceRepository(database, BackupSafety(blocked))
        assertNotNull(runCatching { unsafeRepository.importBackupJson(fixture("Imported").toJsonString()) }.exceptionOrNull())
        assertSameData(before, snapshot())
    }

    @Test
    fun invalidImportDoesNotWriteBackupOrChangeData() = runBlocking {
        assertTrue(repository.importBackupJson(fixture("Prior").toJsonString()))
        val before = snapshot()
        val latest = safety.latestBackup()
        assertFalse(repository.importBackupJson("""{"accounts":[]}"""))
        val malformed = org.json.JSONObject(fixture("Bad").toJsonString())
            .put("transactions", org.json.JSONArray("""[{"type":"INCOME"}]"""))
        assertFalse(repository.importBackupJson(malformed.toString()))
        assertSameData(before, snapshot())
        assertEquals(latest, safety.latestBackup())
    }

    @Test
    fun insertFailureRollsBackDeletesAndEarlierInsertsAndKeepsUndoTarget() = runBlocking {
        assertTrue(repository.importBackupJson(fixture("Prior").toJsonString()))
        val before = snapshot()
        val latest = safety.latestBackup()
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_import BEFORE INSERT ON transactions BEGIN SELECT RAISE(ABORT, 'test failure'); END"
        )
        assertNotNull(runCatching { repository.importBackupJson(fixture("Imported").toJsonString()) }.exceptionOrNull())
        assertSameData(before, snapshot())
        assertEquals(latest, safety.latestBackup())
    }

    @Test
    fun keepsOnlyThreeMostRecentBackupsAndUndoAlsoSavesCurrentData() = runBlocking {
        repeat(5) { assertTrue(repository.importBackupJson(fixture("Data $it").toJsonString())) }
        assertEquals(3, directory.listFiles()!!.size)
        assertSameData(fixture("Data 3"), BackupData.fromJsonString(safety.latestBackup()!!.readText())!!)
        assertTrue(repository.undoRestore())
        assertSameData(fixture("Data 3"), snapshot())
        assertSameData(fixture("Data 4"), BackupData.fromJsonString(safety.latestBackup()!!.readText())!!)
        assertEquals(3, directory.listFiles()!!.size)
    }

    @Test
    fun missingOrCorruptUndoFileLeavesDataAlone() = runBlocking {
        assertFalse(repository.undoRestore())
        assertTrue(repository.importBackupJson(fixture("Prior").toJsonString()))
        val before = snapshot()
        safety.latestBackup()!!.writeText("corrupt")
        assertFalse(repository.undoRestore())
        assertSameData(before, snapshot())
    }
    @Test
    fun emptyBackupReplacesCategoriesAndUndoRestoresThem() = runBlocking {
        val prior = fixture("Prior")
        assertTrue(repository.importBackupJson(prior.toJsonString()))
        val empty = BackupData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        assertTrue(repository.importBackupJson(empty.toJsonString()))
        assertSameData(empty, snapshot())
        assertTrue(repository.undoRestore())
        assertSameData(prior, snapshot())
    }

    @Test
    fun legacyBackupWithoutCategoryArraysPreservesLocalCategories() = runBlocking {
        val prior = fixture("Prior")
        assertTrue(repository.importBackupJson(prior.toJsonString()))
        val legacy = org.json.JSONObject(fixture("Legacy").toJsonString()).apply {
            put("version", 1)
            remove("categories")
            remove("subcategories")
        }
        assertTrue(repository.importBackupJson(legacy.toString()))
        val after = snapshot()
        assertSameData(fixture("Legacy").copy(categories = prior.categories, subcategories = prior.subcategories), after)
    }

    @Test
    fun repositoryWithoutSafetyStorageFailsClosed() = runBlocking {
        assertTrue(repository.importBackupJson(fixture("Prior").toJsonString()))
        val before = snapshot()
        val unsafe = FinanceRepository(database)
        assertNotNull(runCatching { unsafe.importBackupJson(fixture("Imported").toJsonString()) }.exceptionOrNull())
        assertSameData(before, snapshot())
    }

}
