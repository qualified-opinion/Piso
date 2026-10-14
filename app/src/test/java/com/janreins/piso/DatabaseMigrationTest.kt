package com.janreins.piso

import android.content.Context
import android.content.res.AssetManager
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.janreins.piso.data.local.AppDatabase
import com.janreins.piso.data.local.MIGRATION_1_2
import com.janreins.piso.data.local.MIGRATION_2_3
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Creates historical databases from Room's exported schemas and validates the real migrations. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseMigrationTest {
    private val dbName = "migration-test.db"
    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Before
    fun setup() {
        context.deleteDatabase(dbName)
        // AGP's Robolectric resource APK omits test assets. Mount the same exported schemas
        // from JVM test resources as a test-only asset archive for MigrationTestHelper.
        val schemaAssets = temporaryFolder.newFile("schemas.zip")
        ZipOutputStream(schemaAssets.outputStream()).use { zip ->
            for (version in 1..3) {
                val path = "${AppDatabase::class.java.name}/$version.json"
                zip.putNextEntry(ZipEntry("assets/$path"))
                checkNotNull(javaClass.classLoader!!.getResourceAsStream(path)) {
                    "Missing exported schema: $path"
                }.use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        val cookie = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
            .invoke(InstrumentationRegistry.getInstrumentation().context.assets, schemaAssets.absolutePath) as Int
        assertTrue("Could not mount schema assets", cookie > 0)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrate1To2_preservesFinancialDataAndAddsCategories() {
        helper.createDatabase(dbName, 1).use { insertFinancialData(it, 1) }

        helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2).use { db ->
            assertFinancialData(db, "")
            assertRow(db, "SELECT COUNT(*) FROM user_categories", 0L)
            assertRow(db, "SELECT COUNT(*) FROM user_subcategories", 0L)
            // Check that the intermediate schema also accepts category data before upgrading.
            insertCategories(db)
        }
        helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3).use { db ->
            assertFinancialData(db, "")
            assertCategories(db)
        }
    }

    @Test
    fun migrate1To3_preservesFinancialDataAcrossBothMigrations() {
        helper.createDatabase(dbName, 1).use { insertFinancialData(it, 1) }

        helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_1_2, MIGRATION_2_3)
            .use { db ->
                assertFinancialData(db, "")
                assertRow(db, "SELECT COUNT(*) FROM user_categories", 0L)
                assertRow(db, "SELECT COUNT(*) FROM user_subcategories", 0L)
            }
    }

    @Test
    fun migrate2To3_assignsSubcategoryKindsAndPreservesData() {
        helper.createDatabase(dbName, 2).use { db ->
            insertFinancialData(db, 2)
            insertCategories(db)
        }

        helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3).use { db ->
            assertFinancialData(db, "Groceries")
            assertCategories(db)
        }
    }

    private fun insertFinancialData(db: SupportSQLiteDatabase, version: Int) {
        val subcategoryColumn = if (version == 2) ", subcategory" else ""
        val subcategoryValue = if (version == 2) ", 'Groceries'" else ""
        val emptySubcategoryValue = if (version == 2) ", ''" else ""
        db.execSQL("INSERT INTO accounts VALUES (1, 'Wallet', 'Cash', 1234.5, 'Cash notes')")
        db.execSQL("INSERT INTO accounts VALUES (2, 'Savings', 'Bank', 500.0, '')")
        db.execSQL("INSERT INTO budgets VALUES (1, 'Food', 750.0, '2026-09')")
        db.execSQL("INSERT INTO goals VALUES (1, 'Emergency', 10000.0, 500.0, 0, 1800000000000, 2)")
        db.execSQL("INSERT INTO debts VALUES (1, 'Loan', 'Personal', 2000.0, 1500.0, 'Debt notes', 1800000000000)")
        db.execSQL("INSERT INTO investments VALUES (1, 'Fund', 'Stocks', 3000.0, 'Investment notes', '10', 1700000000000)")
        db.execSQL(
            """
            INSERT INTO transactions
                (id, dateMillis, type, category, amount, note, accountId, transferToId, goalId, goalFlow, debtId$subcategoryColumn)
            VALUES (1, 1700000000000, 'EXPENSE', 'Food', 125.5, 'Transaction notes', 1, 2, 1, 'IN', 1$subcategoryValue)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO transactions
                (id, dateMillis, type, category, amount, note, accountId, transferToId, goalId, goalFlow, debtId$subcategoryColumn)
            VALUES (2, 1700000000001, 'INCOME', 'Salary', 100.0, '', NULL, NULL, NULL, NULL, NULL$emptySubcategoryValue)
            """.trimIndent()
        )
    }

    private fun insertCategories(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO user_categories (id, name, kind, isArchived) VALUES
                (1, 'Food', 'EXPENSE', 0), (2, 'Other', 'EXPENSE', 1),
                (3, 'Salary', 'INCOME', 0), (4, 'Other', 'INCOME', 0)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO user_subcategories (id, parentCategoryName, name, isArchived) VALUES
                (1, 'Food', 'Groceries', 0), (2, 'Salary', 'Bonus', 0),
                (3, 'Other', 'Misc', 1), (4, 'Gone', 'Orphan', 0)
            """.trimIndent()
        )
    }

    private fun assertFinancialData(db: SupportSQLiteDatabase, subcategory: String) {
        assertRow(db, "SELECT COUNT(*) FROM accounts", 2L)
        assertRow(db, "SELECT * FROM accounts WHERE id = 1", 1L, "Wallet", "Cash", 1234.5, "Cash notes")
        assertRow(db, "SELECT * FROM accounts WHERE id = 2", 2L, "Savings", "Bank", 500.0, "")
        assertRow(db, "SELECT * FROM budgets", 1L, "Food", 750.0, "2026-09")
        assertRow(db, "SELECT * FROM goals", 1L, "Emergency", 10000.0, 500.0, 0L, 1800000000000L, 2L)
        assertRow(db, "SELECT * FROM debts", 1L, "Loan", "Personal", 2000.0, 1500.0, "Debt notes", 1800000000000L)
        assertRow(db, "SELECT * FROM investments", 1L, "Fund", "Stocks", 3000.0, "Investment notes", "10", 1700000000000L)
        assertRow(db, "SELECT COUNT(*) FROM transactions", 2L)
        val columns = "id, dateMillis, type, category, subcategory, amount, note, accountId, transferToId, goalId, goalFlow, debtId"
        assertRow(
            db, "SELECT $columns FROM transactions WHERE id = 1",
            1L, 1700000000000L, "EXPENSE", "Food", subcategory, 125.5, "Transaction notes", 1L, 2L, 1L, "IN", 1L
        )
        assertRow(
            db, "SELECT $columns FROM transactions WHERE id = 2",
            2L, 1700000000001L, "INCOME", "Salary", "", 100.0, "", null, null, null, null, null
        )
    }

    private fun assertCategories(db: SupportSQLiteDatabase) {
        assertRow(db, "SELECT COUNT(*) FROM user_categories", 4L)
        assertRow(db, "SELECT * FROM user_categories WHERE id = 1", 1L, "Food", "EXPENSE", 0L)
        assertRow(db, "SELECT * FROM user_categories WHERE id = 2", 2L, "Other", "EXPENSE", 1L)
        assertRow(db, "SELECT * FROM user_categories WHERE id = 3", 3L, "Salary", "INCOME", 0L)
        assertRow(db, "SELECT * FROM user_categories WHERE id = 4", 4L, "Other", "INCOME", 0L)
        assertRow(db, "SELECT COUNT(*) FROM user_subcategories", 5L)
        assertRow(db, "SELECT * FROM user_subcategories WHERE id = 1", 1L, "Food", "Groceries", 0L, "EXPENSE")
        assertRow(db, "SELECT * FROM user_subcategories WHERE id = 2", 2L, "Salary", "Bonus", 0L, "INCOME")
        assertRow(db, "SELECT * FROM user_subcategories WHERE id = 3", 3L, "Other", "Misc", 1L, "EXPENSE")
        assertRow(db, "SELECT * FROM user_subcategories WHERE id = 4", 4L, "Gone", "Orphan", 0L, "EXPENSE")
        assertRow(
            db, "SELECT parentCategoryName, name, isArchived, parentKind FROM user_subcategories WHERE id > 4",
            "Other", "Misc", 1L, "INCOME"
        )
    }

    private fun assertRow(db: SupportSQLiteDatabase, sql: String, vararg expected: Any?) {
        db.query(sql).use { cursor ->
            assertTrue("Missing row: $sql", cursor.moveToFirst())
            assertEquals("Column count: $sql", expected.size, cursor.columnCount)
            expected.forEachIndexed { index, value ->
                val message = "$sql: ${cursor.getColumnName(index)}"
                when (value) {
                    null -> assertTrue(message, cursor.isNull(index))
                    is Long -> assertEquals(message, value, cursor.getLong(index))
                    is Double -> assertEquals(message, value, cursor.getDouble(index), 0.0)
                    else -> assertEquals(message, value, cursor.getString(index))
                }
            }
            assertFalse("Unexpected extra row: $sql", cursor.moveToNext())
        }
    }
}
