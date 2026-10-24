package com.janreins.piso.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents an Income, Expense, or Transfer transaction.
 */
@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dateMillis: Long = System.currentTimeMillis(),
    val type: String, // INCOME, EXPENSE, TRANSFER, ADJUSTMENT (one side only: in to transferToId, out of accountId)
    val category: String = "",
    val subcategory: String = "",
    val amount: Double, // Always positive
    val note: String = "",
    val accountId: Long? = null,
    val transferToId: Long? = null, // Only for TRANSFER
    val goalId: Long? = null, // Optional link to a Goal
    val goalFlow: String? = null, // "IN" or "OUT"
    val debtId: Long? = null // Optional link to a Debt payment
)
