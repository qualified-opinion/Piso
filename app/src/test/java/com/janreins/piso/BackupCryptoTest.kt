package com.janreins.piso

import com.janreins.piso.data.models.Account
import com.janreins.piso.data.models.BackupData
import com.janreins.piso.ui.settings.backupPasswordError
import com.janreins.piso.ui.settings.parseBackupText
import com.janreins.piso.ui.settings.unlockBackupText
import com.janreins.piso.util.BackupCrypto
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupCryptoTest {

    // Fewer rounds keep the tests quick; the format stores the count, so decrypting is the same.
    private val rounds = 20_000

    private val plain = BackupData(
        accounts = listOf(Account(id = 1, name = "Wallet", kind = "Cash", balance = 1234.5)),
        transactions = emptyList(),
        budgets = emptyList(),
        goals = emptyList(),
        debts = emptyList(),
        investments = emptyList()
    ).toJsonString()

    @Test
    fun rightPasswordGivesBackTheSameBackup() {
        val locked = BackupCrypto.encrypt(plain, "correct horse".toCharArray(), rounds)

        assertEquals(plain, BackupCrypto.decrypt(locked, "correct horse".toCharArray()))
    }

    @Test
    fun theProtectedFileHidesTheAmountsAndNames() {
        val locked = BackupCrypto.encrypt(plain, "correct horse".toCharArray(), rounds)

        assertFalse(locked.contains("Wallet"))
        assertFalse(locked.contains("1234.5"))
        assertTrue(BackupCrypto.isEncrypted(locked))
        assertFalse(BackupCrypto.isEncrypted(plain))
        assertFalse(BackupCrypto.isEncrypted("not json"))
        // An older Piso sees it as "not a valid backup" rather than restoring nothing.
        assertNull(BackupData.fromJsonString(locked))
    }

    @Test
    fun wrongPasswordOrChangedFileIsRefused() {
        val locked = BackupCrypto.encrypt(plain, "correct horse".toCharArray(), rounds)
        assertThrows(BackupCrypto.WrongPasswordException::class.java) {
            BackupCrypto.decrypt(locked, "wrong horse".toCharArray())
        }

        val root = JSONObject(locked)
        val data = Base64.getDecoder().decode(root.getString("data"))
        data[0] = (data[0].toInt() xor 1).toByte()
        val tampered = root.put("data", Base64.getEncoder().encodeToString(data)).toString()
        assertThrows(BackupCrypto.WrongPasswordException::class.java) {
            BackupCrypto.decrypt(tampered, "correct horse".toCharArray())
        }
    }

    @Test
    fun eachSaveUsesAFreshSaltAndIv() {
        val a = JSONObject(BackupCrypto.encrypt(plain, "correct horse".toCharArray(), rounds))
        val b = JSONObject(BackupCrypto.encrypt(plain, "correct horse".toCharArray(), rounds))

        assertNotEquals(a.getString("salt"), b.getString("salt"))
        assertNotEquals(a.getString("iv"), b.getString("iv"))
        assertNotEquals(a.getString("data"), b.getString("data"))
    }

    @Test
    fun damagedOrUnsupportedFilesAreNotTreatedAsAWrongPassword() {
        val locked = JSONObject(BackupCrypto.encrypt(plain, "correct horse".toCharArray(), rounds))

        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.decrypt(JSONObject(locked.toString()).put("iterations", 1).toString(), "correct horse".toCharArray())
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.decrypt(JSONObject(locked.toString()).put("salt", "AAAA").toString(), "correct horse".toCharArray())
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.decrypt(plain, "correct horse".toCharArray())
        }
    }

    @Test
    fun shortPasswordsAreRefused() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.encrypt(plain, "short".toCharArray(), rounds)
        }
        assertNotNull(backupPasswordError("short", "short"))
        assertNotNull(backupPasswordError("long enough", "long enougj"))
        assertNull(backupPasswordError("long enough", "long enough"))
    }

    @Test
    fun unlockingKeepsTheFileLockedOnAWrongPasswordAndReadsItOnTheRightOne() {
        val locked = BackupCrypto.encrypt(plain, "correct horse".toCharArray(), rounds)

        val wrong = unlockBackupText(locked, "wrong horse".toCharArray())
        assertTrue(wrong.needsPassword)
        assertNotNull(wrong.error)
        assertNull(wrong.data)

        val right = unlockBackupText(locked, "correct horse".toCharArray())
        assertFalse(right.needsPassword)
        assertNull(right.error)
        assertEquals("Wallet", right.data!!.accounts.single().name)

        assertEquals("Wallet", parseBackupText(plain).data!!.accounts.single().name)
        assertNotNull(parseBackupText("{}").error)
    }
}
