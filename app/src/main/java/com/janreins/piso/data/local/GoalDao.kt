package com.janreins.piso.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.janreins.piso.data.models.Goal
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {

    @Query("SELECT * FROM goals ORDER BY isCompleted ASC, id DESC")
    fun getAllGoals(): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE isCompleted = 0 ORDER BY id DESC")
    fun getActiveGoals(): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getGoalById(id: Long): Goal?

    @Query("UPDATE goals SET accountId = NULL WHERE accountId = :accountId")
    suspend fun detachAccount(accountId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: Goal): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoals(goals: List<Goal>)

    @Update
    suspend fun updateGoal(goal: Goal)

    @Query("UPDATE goals SET isCompleted = :done WHERE id = :id")
    suspend fun setGoalCompleted(id: Long, done: Boolean)

    @Delete
    suspend fun deleteGoal(goal: Goal)

    @Query("DELETE FROM goals")
    suspend fun clearGoals()
}
