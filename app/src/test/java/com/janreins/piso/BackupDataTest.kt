package com.janreins.piso

import com.janreins.piso.data.models.Account
import com.janreins.piso.data.models.BackupData
import com.janreins.piso.data.models.Investment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupDataTest {

    @Test
    fun fromJsonString_rejectsJsonThatIsNotAPisoBackup() {
        assertNull(BackupData.fromJsonString("{}"))
        assertNull(BackupData.fromJsonString("""{"name": "some other app config"}"""))
        assertNull(BackupData.fromJsonString("not json"))
    }

    @Test
    fun fromJsonString_acceptsExportedBackup() {
        val exported = BackupData(
            accounts = listOf(Account(id = 1, name = "Wallet", kind = "Cash", balance = 250.0)),
            transactions = emptyList(),
            budgets = emptyList(),
            goals = emptyList(),
            debts = emptyList(),
            investments = emptyList()
        ).toJsonString()

        val parsed = BackupData.fromJsonString(exported)
        assertNotNull(parsed)
        assertEquals("Wallet", parsed!!.accounts.single().name)
    }

    @Test
    fun investmentLastUpdatedSurvivesRoundTrip() {
        val exported = BackupData(
            accounts = emptyList(),
            transactions = emptyList(),
            budgets = emptyList(),
            goals = emptyList(),
            debts = emptyList(),
            investments = listOf(
                Investment(id = 1, name = "PSEi fund", kind = "Stocks", currentValue = 5000.0, lastUpdatedMillis = 1_700_000_000_000)
            )
        ).toJsonString()

        val parsed = BackupData.fromJsonString(exported)!!
        assertEquals(1_700_000_000_000, parsed.investments.single().lastUpdatedMillis)
    }

    @Test
    fun olderBackupWithoutSubcategoryKindIsAssignedFromCategories() {
        val json = """
            {
              "appName": "Piso",
              "version": 2,
              "accounts": [], "transactions": [], "budgets": [],
              "goals": [], "debts": [], "investments": [],
              "categories": [
                {"id": 1, "name": "Food", "kind": "EXPENSE"},
                {"id": 2, "name": "Other", "kind": "EXPENSE"},
                {"id": 3, "name": "Other", "kind": "INCOME"}
              ],
              "subcategories": [
                {"id": 10, "parentCategoryName": "Food", "name": "Groceries"},
                {"id": 11, "parentCategoryName": "Other", "name": "Misc"}
              ]
            }
        """.trimIndent()

        val subs = BackupData.fromJsonString(json)!!.subcategories
        assertEquals("EXPENSE", subs.single { it.name == "Groceries" }.parentKind)
        assertEquals(setOf("EXPENSE", "INCOME"), subs.filter { it.name == "Misc" }.map { it.parentKind }.toSet())
        assertEquals(setOf(11L, 0L), subs.filter { it.name == "Misc" }.map { it.id }.toSet())
    }

    @Test
    fun fromJsonString_requiresCompleteEmptyPisoBackup() {
        assertNull(BackupData.fromJsonString("""{"appName": "Piso", "version": 2}"""))
        assertNotNull(BackupData.fromJsonString(emptyBackup().toJsonString()))
    }
    private fun emptyBackup() = BackupData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

    @Test
    fun rejectsPartialBackupsAndWrongArrayTypes() {
        assertNull(BackupData.fromJsonString("""{"accounts":[]}"""))
        val keys = listOf("accounts", "transactions", "budgets", "goals", "debts", "investments")
        keys.forEach { key ->
            val missing = JSONObject(emptyBackup().toJsonString()).apply { remove(key) }
            assertNull("Missing $key", BackupData.fromJsonString(missing.toString()))
            val wrongType = JSONObject(emptyBackup().toJsonString()).put(key, JSONObject())
            assertNull("Wrong type for $key", BackupData.fromJsonString(wrongType.toString()))
        }
    }

    @Test
    fun requiresKnownIntegerVersion() {
        val missing = JSONObject(emptyBackup().toJsonString()).apply { remove("version") }
        assertNull(BackupData.fromJsonString(missing.toString()))
        listOf(0, -1, 4, 999, 2.5, "3", JSONObject.NULL).forEach { version ->
            val root = JSONObject(emptyBackup().toJsonString()).put("version", version)
            assertNull("Version $version", BackupData.fromJsonString(root.toString()))
        }
        listOf(1, 2, 3).forEach { version ->
            val root = JSONObject(emptyBackup().toJsonString()).put("version", version)
            assertEquals(version, BackupData.fromJsonString(root.toString())!!.version)
        }
    }

    @Test
    fun summaryReportsCountsVersionAndOriginalExportDate() {
        val data = emptyBackup().copy(
            accounts = listOf(Account(id = 1, name = "Wallet", kind = "Cash", balance = 250.0)),
            investments = listOf(Investment(id = 1, name = "Fund", kind = "Stocks", currentValue = 50.0)),
            exportedAt = 1_700_000_000_000L
        )
        val parsed = BackupData.fromJsonString(data.toJsonString())!!
        assertEquals(data, parsed)
        val summary = parsed.summary
        assertEquals(3, summary.version)
        assertEquals(1_700_000_000_000L, summary.exportedAt)
        assertEquals(listOf(1, 0, 0, 0, 0, 1), listOf(summary.accounts, summary.transactions,
            summary.budgets, summary.goals, summary.debts, summary.investments))
        assertTrue(summary.confirmationText().contains("1 accounts, 0 transactions"))
        assertTrue(summary.confirmationText().contains("replaces all current Piso data"))
        assertTrue(summary.confirmationText().contains(java.text.DateFormat.getDateTimeInstance()
            .format(java.util.Date(1_700_000_000_000L))))
    }

    @Test
    fun olderBackupWithoutDateReportsUnknown() {
        val root = JSONObject(emptyBackup().toJsonString()).apply { remove("exportedAt") }
        val summary = BackupData.fromJsonString(root.toString())!!.summary
        assertNull(summary.exportedAt)
        assertTrue(summary.confirmationText().contains("Export date: Unknown"))
    }

}
