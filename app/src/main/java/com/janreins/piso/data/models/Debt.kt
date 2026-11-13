package com.janreins.piso.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.math.max

/**
 * Represents a debt (Credit Card, Personal, Family, Housing, Vehicle, etc.).
 */
@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val kind: String, // Credit Card, Personal, Family, Housing, Vehicle, Other
    val originalAmount: Double,
    val remainingAmount: Double,
    val notes: String = "",
    val dueMillis: Long? = null,
    // A credit card whose purchases Jan records: each purchase is spending when made and adds
    // to what's owed, so a payment to it only moves money and isn't counted as spending again.
    val tracksPurchases: Boolean = false
) {
    val isPaidOff: Boolean
        get() = amountOwed() <= 0.0001
}

/**
 * What's owed, never below zero. The stored [Debt.remainingAmount] can sit below zero when a
 * purchase is deleted before the payment that cleared it, so deleting that payment later still
 * lands on the right balance. Screens show this, not the raw balance.
 */
fun Debt.amountOwed(): Double = if (remainingAmount.isFinite()) max(0.0, remainingAmount) else remainingAmount
