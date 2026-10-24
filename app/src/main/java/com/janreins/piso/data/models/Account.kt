package com.janreins.piso.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a financial account (e.g. Cash, Bank, Savings, E-wallet).
 */
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val kind: String, // Cash, Bank, Savings, E-Wallet, Other
    val balance: Double,
    val notes: String = "",
    // Archived accounts are hidden from pickers and the main list, but still count in net worth.
    val isArchived: Boolean = false
)

/**
 * The accounts to offer in an account picker: the ones not archived, plus any of [keepIds]
 * (an account the record being edited already uses), so editing never silently changes it.
 */
fun List<Account>.forPicker(vararg keepIds: Long?): List<Account> =
    filter { !it.isArchived || it.id in keepIds }
