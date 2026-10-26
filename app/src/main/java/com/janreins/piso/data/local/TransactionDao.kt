package com.janreins.piso.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.janreins.piso.data.models.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY dateMillis DESC, id DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): Transaction?

    @Query("SELECT * FROM transactions ORDER BY dateMillis DESC, id DESC LIMIT :limit")
    fun getRecentTransactions(limit: Int): Flow<List<Transaction>>

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId OR transferToId = :accountId")
    suspend fun getTransactionCountForAccount(accountId: Long): Int

    // Detach history from a deleted goal/debt so a later record reusing the id isn't affected
    @Query("UPDATE transactions SET goalId = NULL WHERE goalId = :goalId")
    suspend fun detachGoal(goalId: Long)

    @Query("UPDATE transactions SET debtId = NULL WHERE debtId = :debtId")
    suspend fun detachDebt(debtId: Long)

    @Query("UPDATE transactions SET recurringId = NULL WHERE recurringId = :ruleId")
    suspend fun detachRecurring(ruleId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<Transaction>)

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions")
    suspend fun clearTransactions()
}
