package com.janreins.piso

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.janreins.piso.data.local.AppDatabase
import com.janreins.piso.data.local.UserProfile
import com.janreins.piso.data.local.UserProfileManager
import com.janreins.piso.ui.app.AppViewModel
import com.janreins.piso.ui.settings.SettingsViewModel
import com.janreins.piso.data.models.*
import com.janreins.piso.data.repository.BackupSafety
import com.janreins.piso.data.repository.FinanceRepository
import com.janreins.piso.ui.state.MainTab
import com.janreins.piso.ui.state.MoreSubScreen
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

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
        assertTrue(FinanceRepository(database, BackupSafety(directory)).undoRestore(safety.latestBackup()!!.name))
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
    fun keepsOnlyThreeMostRecentBackupsAndUndoConsumesEachCopyOnce() = runBlocking {
        repeat(5) { assertTrue(repository.importBackupJson(fixture("Data $it").toJsonString())) }
        assertEquals(3, directory.listFiles()!!.size)
        assertSameData(fixture("Data 3"), BackupData.fromJsonString(safety.latestBackup()!!.readText())!!)
        val confirmed = repository.undoRestoreInfo()!!
        assertEquals("Undo restore of ${confirmed.summary.date}", confirmed.label)
        assertTrue(confirmed.summary.confirmationText(isUndo = true).contains("1 accounts, 1 transactions"))
        val consumed = safety.latestBackup()!!
        onDeleteAccounts = { assertTrue(consumed.exists()) }
        assertTrue(repository.undoRestore(confirmed.fileName))
        onDeleteAccounts = null
        assertFalse(consumed.exists())
        assertSameData(fixture("Data 3"), snapshot())
        assertSameData(fixture("Data 2"), BackupData.fromJsonString(safety.latestBackup()!!.readText())!!)
        assertEquals(2, directory.listFiles()!!.size)
        // Reusing the confirmation cannot consume the next older copy.
        assertFalse(repository.undoRestore(confirmed.fileName))
        assertSameData(fixture("Data 3"), snapshot())
    }

    @Test
    fun missingOrCorruptUndoFileLeavesDataAlone() = runBlocking {
        assertFalse(repository.undoRestore("pre-import-0000000000000000000.json"))
        assertTrue(repository.importBackupJson(fixture("Prior").toJsonString()))
        val before = snapshot()
        val corrupt = safety.latestBackup()!!
        corrupt.writeText("corrupt")
        assertFalse(repository.undoRestore(corrupt.name))
        assertSameData(before, snapshot())
        assertTrue(corrupt.exists())
        assertEquals("corrupt", corrupt.readText())
    }

    @Test
    fun restoreUndoThenUndoAgainLeavesPreImportData() = runBlocking {
        assertTrue(repository.importBackupJson(fixture("Prior").toJsonString()))
        safety.deleteAll() // Initial fixture is not an import the user needs to undo.
        assertTrue(repository.importBackupJson(fixture("Imported").toJsonString()))
        val confirmed = repository.undoRestoreInfo()!!
        assertTrue(repository.undoRestore(confirmed.fileName))
        assertSameData(fixture("Prior"), snapshot())
        assertFalse(repository.hasUndoRestore())
        assertFalse(repository.undoRestore(confirmed.fileName))
        assertSameData(fixture("Prior"), snapshot())
    }

    @Test
    fun undoSucceedsWhenPostCommitDeleteFailsAndCopyCannotBeReused() = runBlocking {
        repeat(3) { failureMode ->
            val prior = fixture("Prior $failureMode")
            assertTrue(repository.importBackupJson(prior.toJsonString()))
            safety.deleteAll()
            assertTrue(repository.importBackupJson(fixture("Imported").toJsonString()))
            val copy = safety.latestBackup()!!
            var deletionAttempted = false
            val failingSafety = BackupSafety(directory) {
                assertFalse(database.inTransaction())
                deletionAttempted = true
                when (failureMode) {
                    0 -> false
                    1 -> throw IOException("Simulated delete failure")
                    else -> throw SecurityException("Simulated delete denial")
                }
            }
            assertTrue(FinanceRepository(database, failingSafety).undoRestore(copy.name))
            assertTrue(deletionAttempted)
            assertTrue(copy.exists())
            assertSameData(prior, snapshot())
            // A later tap, even through a new repository, must not overwrite subsequent edits.
            repository.updateAccount(prior.accounts.single().copy(name = "Edited after undo"))
            val edited = snapshot()
            val reopened = FinanceRepository(database, BackupSafety(directory))
            assertFalse(reopened.hasUndoRestore())
            assertNull(reopened.undoRestoreInfo())
            assertFalse(reopened.undoRestore(copy.name))
            assertSameData(edited, snapshot())
        }
    }

    @Test
    fun nextSuccessfulImportDeletesConsumedCopyLeftByFailedUndoDeletion() = runBlocking {
        assertTrue(repository.importBackupJson(fixture("Prior").toJsonString()))
        safety.deleteAll()
        assertTrue(repository.importBackupJson(fixture("Imported").toJsonString()))
        val consumed = safety.latestBackup()!!
        val failingDelete = FinanceRepository(database, BackupSafety(directory) { false })
        assertTrue(failingDelete.undoRestore(consumed.name))
        val marker = File(directory, "consumed-${consumed.name}")
        assertTrue(consumed.exists())
        assertTrue(marker.exists())
        assertFalse(repository.importBackupJson("invalid"))
        assertTrue(consumed.exists())
        assertTrue(repository.importBackupJson(fixture("Next").toJsonString()))
        assertFalse(consumed.exists())
        assertFalse(marker.exists())
        assertSameData(fixture("Prior"), BackupData.fromJsonString(safety.latestBackup()!!.readText())!!)
        assertSameData(fixture("Next"), snapshot())
    }

    @Test
    fun clearSucceedsWhenPostCommitCleanupThrows() = runBlocking {
        repeat(2) { failureMode ->
            val prior = fixture("Prior $failureMode")
            assertTrue(repository.importBackupJson(prior.toJsonString()))
            var deletionAttempted = false
            val failingSafety = BackupSafety(directory) {
                assertFalse(database.inTransaction())
                deletionAttempted = true
                if (failureMode == 0) throw IOException("Simulated delete failure")
                else throw SecurityException("Simulated delete denial")
            }
            val result = runCatching { FinanceRepository(database, failingSafety).clearAllData() }
            assertTrue(result.isSuccess)
            assertTrue(deletionAttempted)
            val empty = snapshot()
            assertTrue(listOf(empty.accounts, empty.transactions, empty.budgets, empty.goals,
                empty.debts, empty.investments).all { it.isEmpty() })
            val copy = safety.latestBackup()!!
            assertTrue(copy.name.startsWith("pre-clear-"))
            assertSameData(prior, BackupData.fromJsonString(copy.readText())!!)
        }
    }

    @Test
    fun clearWithOnlyCustomCategorySavesCopyAndUndoRestoresIt() = runBlocking {
        repository.insertCategory(UserCategory(name = "Custom", kind = "EXPENSE"))
        val prior = snapshot()
        assertFalse(repository.hasUndoRestore())
        repository.clearAllData()
        val copy = directory.listFiles()!!.single()
        assertTrue(copy.name.startsWith("pre-clear-"))
        assertSameData(prior, BackupData.fromJsonString(copy.readText())!!)
        assertFalse(snapshot().categories.any { it.name == "Custom" })
        assertTrue(repository.undoRestore(copy.name))
        assertSameData(prior, snapshot())
        assertFalse(repository.hasUndoRestore())
    }

    @Test
    fun clearWithOnlyCustomSubcategorySavesCopyAndUndoRestoresIt() = runBlocking {
        repository.seedDefaultCategoriesIfEmpty()
        repository.insertSubcategory(UserSubcategory(parentCategoryName = "Food", name = "Custom"))
        val prior = snapshot()
        assertFalse(repository.hasUndoRestore())
        repository.clearAllData()
        val copy = directory.listFiles()!!.single()
        assertTrue(copy.name.startsWith("pre-clear-"))
        assertSameData(prior, BackupData.fromJsonString(copy.readText())!!)
        assertFalse(snapshot().subcategories.any { it.name == "Custom" })
        assertTrue(repository.undoRestore(copy.name))
        assertSameData(prior, snapshot())
        assertFalse(repository.hasUndoRestore())
    }

    @Test
    fun clearPreservesEditsAndRemovalsOfDefaultCategoriesAndSubcategories() = runBlocking {
        repository.seedDefaultCategoriesIfEmpty()
        val defaults = snapshot()
        val category = defaults.categories.first()
        val subcategory = defaults.subcategories.first()
        val changedCategories = listOf(
            category.copy(name = "Renamed"),
            category.copy(kind = "INCOME"),
            category.copy(isArchived = true)
        ).map { edited ->
            defaults.copy(categories = defaults.categories.map { if (it.id == category.id) edited else it })
        }
        val changedSubcategories = listOf(
            subcategory.copy(name = "Renamed"),
            subcategory.copy(parentCategoryName = "Transport"),
            subcategory.copy(parentKind = "INCOME"),
            subcategory.copy(isArchived = true)
        ).map { edited ->
            defaults.copy(subcategories = defaults.subcategories.map { if (it.id == subcategory.id) edited else it })
        }
        val removedDefaults = listOf(
            defaults.copy(categories = defaults.categories.drop(1)),
            defaults.copy(subcategories = defaults.subcategories.drop(1))
        )
        (changedCategories + changedSubcategories + removedDefaults).forEach { prior ->
            assertTrue(repository.importBackupJson(prior.toJsonString()))
            safety.deleteAll() // Only the clear should create an undo target.
            repository.clearAllData()
            val copy = directory.listFiles()!!.single()
            assertTrue(copy.name.startsWith("pre-clear-"))
            assertSameData(prior, BackupData.fromJsonString(copy.readText())!!)
            assertTrue(repository.undoRestore(copy.name))
            assertSameData(prior, snapshot())
        }
    }

    @Test
    fun clearWithDefaultsInDifferentIdOrderSkipsCopyAndCleanup() = runBlocking {
        repository.seedDefaultCategoriesIfEmpty()
        val defaults = snapshot()
        val reordered = defaults.copy(
            categories = defaults.categories.reversed().mapIndexed { index, category -> category.copy(id = 100L + index) },
            subcategories = defaults.subcategories.reversed().mapIndexed { index, subcategory -> subcategory.copy(id = 100L + index) }
        )
        assertTrue(repository.importBackupJson(reordered.toJsonString()))
        safety.deleteAll()
        File(directory, "pending-leftover.tmp").writeText("partial ledger")
        var deletionAttempted = false
        val tracked = FinanceRepository(database, BackupSafety(directory) {
            deletionAttempted = true
            it.delete()
        })
        tracked.clearAllData()
        assertFalse(repository.hasUndoRestore())
        assertFalse(deletionAttempted)
        assertEquals("partial ledger", directory.listFiles()!!.single().readText())
    }

    @Test
    fun clearingEmptyLedgerKeepsRealDataCopyAndSkipsCleanup() = runBlocking {
        val prior = fixture("Prior")
        assertTrue(repository.importBackupJson(prior.toJsonString()))
        repository.clearAllData()
        val copy = safety.latestBackup()!!
        File(directory, "pending-leftover.tmp").writeText("partial ledger")
        val before = directory.listFiles()!!.associate { it.name to it.readText() }
        var deletionAttempted = false
        val tracked = FinanceRepository(database, BackupSafety(directory) {
            deletionAttempted = true
            it.delete()
        })
        repeat(2) { tracked.clearAllData(keepUndo = true) }
        assertFalse(deletionAttempted)
        assertEquals(before, directory.listFiles()!!.associate { it.name to it.readText() })
        assertEquals(copy, safety.latestBackup())
        assertTrue(repository.undoRestore(copy.name))
        assertSameData(prior, snapshot())
    }

    @Test
    fun clearKeepsExactlyOnePreClearCopyAndUndoRestoresEveryTable() = runBlocking {
        val prior = fixture("Prior")
        repeat(3) { assertTrue(repository.importBackupJson(prior.toJsonString())) }
        onDeleteAccounts = {
            assertTrue(database.inTransaction())
            assertSameData(prior, BackupData.fromJsonString(safety.latestBackup()!!.readText())!!)
        }
        repository.clearAllData()
        onDeleteAccounts = null
        val copy = directory.listFiles()!!.single()
        assertTrue(copy.name.matches(Regex("pre-clear-[0-9]{19}\\.json")))
        assertTrue(repository.undoRestoreInfo()!!.label.startsWith("Undo clear of "))
        val empty = snapshot()
        assertTrue(listOf(empty.accounts, empty.transactions, empty.budgets, empty.goals,
            empty.debts, empty.investments).all { it.isEmpty() })
        assertTrue(repository.undoRestore(copy.name))
        assertSameData(prior, snapshot())
        assertFalse(repository.hasUndoRestore())
    }

    @Test
    fun mixedCopyPrefixesShareChronologicalOrderingAndKeepThree() {
        val copies = (0..4).map {
            safety.save(fixture("Data $it").toJsonString(), beforeClear = it % 2 == 0)
        }
        assertEquals(copies.last(), safety.latestBackup())
        safety.prune()
        assertEquals(copies.takeLast(3).toSet(), directory.listFiles()!!.toSet())
    }

    @Test
    fun failedUndoRollsBackAndKeepsTheCopy() = runBlocking {
        assertTrue(repository.importBackupJson(fixture("Prior").toJsonString()))
        assertTrue(repository.importBackupJson(fixture("Imported").toJsonString()))
        val before = snapshot()
        val copy = safety.latestBackup()!!
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_undo BEFORE INSERT ON transactions BEGIN SELECT RAISE(ABORT, 'test failure'); END"
        )
        assertNotNull(runCatching { repository.undoRestore(copy.name) }.exceptionOrNull())
        assertSameData(before, snapshot())
        assertEquals(copy, safety.latestBackup())
        assertTrue(copy.exists())
    }

    @Test
    fun appResetDeletesAllCopiesAndPendingFilesAndClearsSettingsUndo() = verifyAppReset(deleteFails = false)

    @Test
    fun appResetStillClearsProfileAndNavigationWhenCleanupThrows() = verifyAppReset(deleteFails = true)

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun verifyAppReset(deleteFails: Boolean) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val application = ApplicationProvider.getApplicationContext<Application>()
        val appSafety = BackupSafety(File(application.filesDir, "backups"))
        val appRepository = FinanceRepository(database, appSafety)
        val previous: AppDatabase? = ReflectionHelpers.getStaticField(AppDatabase::class.java, "INSTANCE")
        val store = ViewModelStore()
        val profile = UserProfileManager.getInstance(application)
        try {
            ReflectionHelpers.setStaticField(AppDatabase::class.java, "INSTANCE", database)
            profile.createProfile("Juan", null)
            appSafety.deleteAll()
            val app = AppViewModel(application).also { store.put("app", it) }
            var deletionAttempted = false
            if (deleteFails) {
                ReflectionHelpers.setField(app, "backupSafety", BackupSafety(File(application.filesDir, "backups")) {
                    assertFalse(database.inTransaction())
                    deletionAttempted = true
                    throw IOException("Simulated delete failure")
                })
            }
            app.selectTab(MainTab.MORE)
            app.openMoreSubScreen(MoreSubScreen.SETTINGS)
            assertTrue(appRepository.importBackupJson(fixture("Prior").toJsonString()))
            appRepository.clearAllData()
            assertTrue(appRepository.importBackupJson(fixture("Imported").toJsonString()))
            File(application.filesDir, "backups/pending-leftover.tmp").writeText("partial ledger")
            val settings = SettingsViewModel(application).also { store.put("settings", it) }
            settings.canUndoRestore.first { it }
            app.clearAllDataAndReset()
            profile.userProfile.first { it == UserProfile() }
            settings.canUndoRestore.first { !it }
            app.currentTab.first { it == MainTab.HOME }
            app.moreSubScreen.first { it == null }
            assertFalse(appRepository.hasUndoRestore())
            assertEquals(MainTab.HOME, app.currentTab.value)
            assertNull(app.moreSubScreen.value)
            if (deleteFails) assertTrue(deletionAttempted)
            else assertTrue(File(application.filesDir, "backups").listFiles()!!.isEmpty())
            assertTrue(BackupData.fromJsonString(appRepository.exportBackupJson())!!.accounts.isEmpty())
            profile.createProfile("New profile", null)
            assertNull(appRepository.undoRestoreInfo())
        } finally {
            store.clear()
            profile.clearProfile()
            appSafety.deleteAll()
            ReflectionHelpers.setStaticField(AppDatabase::class.java, "INSTANCE", previous)
            Dispatchers.resetMain()
        }
    }

    @Test
    fun emptyBackupReplacesCategoriesAndUndoRestoresThem() = runBlocking {
        val prior = fixture("Prior")
        assertTrue(repository.importBackupJson(prior.toJsonString()))
        val empty = BackupData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        assertTrue(repository.importBackupJson(empty.toJsonString()))
        assertSameData(empty, snapshot())
        assertTrue(repository.undoRestore(safety.latestBackup()!!.name))
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
