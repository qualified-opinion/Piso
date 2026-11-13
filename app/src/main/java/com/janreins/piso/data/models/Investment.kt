package com.janreins.piso.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.math.max

/**
 * Represents an investment asset (Stocks, Mutual Funds, Gold, Crypto, etc.).
 */
@Entity(tableName = "investments")
data class Investment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val kind: String, // Stocks, Mutual Funds / ETFs, Gold, Silver, Bitcoin, Other Crypto, Other
    val currentValue: Double,
    val notes: String = "",
    val quantity: String? = null,
    val lastUpdatedMillis: Long = System.currentTimeMillis()
)

/**
 * Value shown on screen, never below zero. The stored [Investment.currentValue] can sit below
 * zero when a buy is deleted before the sale that emptied it, so deleting that sale later still
 * restores the right value and the cash that moved with it.
 */
fun Investment.shownValue(): Double = if (currentValue.isFinite()) max(0.0, currentValue) else currentValue
