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
    val debtId: Long? = null, // Optional link to a Debt payment
    val recurringId: Long? = null, // Set when a recurring schedule recorded this entry
    // With debtId: DEBT_FLOW_CHARGE is a purchase on a credit card (adds to what's owed).
    // Null is a payment (lowers what's owed), as every debt-linked entry was before.
    val debtFlow: String? = null
)

/** [Transaction.debtFlow] of a purchase made on a credit card. */
const val DEBT_FLOW_CHARGE = "CHARGE"

/** Category given to a payment on a card that tracks its purchases (it isn't spending). */
const val CARD_PAYMENT_CATEGORY = "Card payment"

val Transaction.isCardPurchase: Boolean get() = debtId != null && debtFlow == DEBT_FLOW_CHARGE
