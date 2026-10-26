package com.janreins.piso.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.janreins.piso.data.models.RecurringRule
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringDao {

    @Query("SELECT * FROM recurring_rules ORDER BY nextDueDay ASC, id ASC")
    fun getAllRules(): Flow<List<RecurringRule>>

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun getRuleById(id: Long): RecurringRule?

    @Query("SELECT * FROM recurring_rules WHERE isPaused = 0 AND nextDueDay <= :today ORDER BY nextDueDay ASC, id ASC")
    suspend fun getDueRules(today: Long): List<RecurringRule>

    @Query("SELECT COUNT(*) FROM recurring_rules WHERE accountId = :accountId")
    suspend fun countRulesForAccount(accountId: Long): Int

    @Query("UPDATE recurring_rules SET isPaused = 1 WHERE accountId = :accountId")
    suspend fun pauseRulesForAccount(accountId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: RecurringRule): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<RecurringRule>)

    @Update
    suspend fun updateRule(rule: RecurringRule)

    @Delete
    suspend fun deleteRule(rule: RecurringRule)

    @Query("DELETE FROM recurring_rules")
    suspend fun clearRules()
}
