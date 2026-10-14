package com.janreins.piso.data.models

import org.json.JSONArray
import org.json.JSONObject

/**
 * Helper to export and import all Piso room data to/from JSON for private backup and restore.
 */
data class BackupData(
    val accounts: List<Account>,
    val transactions: List<Transaction>,
    val budgets: List<Budget>,
    val goals: List<Goal>,
    val debts: List<Debt>,
    val investments: List<Investment>,
    val categories: List<UserCategory> = emptyList(),
    val subcategories: List<UserSubcategory> = emptyList(),
    val version: Int = CURRENT_VERSION,
    val exportedAt: Long? = System.currentTimeMillis(),
    // Older backups may omit category data; preserve local categories in that case.
    val hasCategoryData: Boolean = true
) {
    fun toJsonString(): String {
        val root = JSONObject()
        root.put("version", CURRENT_VERSION)
        root.put("appName", "Piso")
        root.put("exportedAt", exportedAt)

        val accArr = JSONArray()
        accounts.forEach { acc ->
            val obj = JSONObject()
            obj.put("id", acc.id)
            obj.put("name", acc.name)
            obj.put("kind", acc.kind)
            obj.put("balance", acc.balance)
            obj.put("notes", acc.notes)
            accArr.put(obj)
        }
        root.put("accounts", accArr)

        val txArr = JSONArray()
        transactions.forEach { tx ->
            val obj = JSONObject()
            obj.put("id", tx.id)
            obj.put("dateMillis", tx.dateMillis)
            obj.put("type", tx.type)
            obj.put("category", tx.category)
            obj.put("subcategory", tx.subcategory)
            obj.put("amount", tx.amount)
            obj.put("note", tx.note)
            if (tx.accountId != null) obj.put("accountId", tx.accountId)
            if (tx.transferToId != null) obj.put("transferToId", tx.transferToId)
            if (tx.goalId != null) obj.put("goalId", tx.goalId)
            if (tx.goalFlow != null) obj.put("goalFlow", tx.goalFlow)
            if (tx.debtId != null) obj.put("debtId", tx.debtId)
            txArr.put(obj)
        }
        root.put("transactions", txArr)

        val bgtArr = JSONArray()
        budgets.forEach { bgt ->
            val obj = JSONObject()
            obj.put("id", bgt.id)
            obj.put("category", bgt.category)
            obj.put("limitAmount", bgt.limitAmount)
            obj.put("monthKey", bgt.monthKey)
            bgtArr.put(obj)
        }
        root.put("budgets", bgtArr)

        val goalArr = JSONArray()
        goals.forEach { g ->
            val obj = JSONObject()
            obj.put("id", g.id)
            obj.put("name", g.name)
            obj.put("targetAmount", g.targetAmount)
            obj.put("currentAmount", g.currentAmount)
            obj.put("isCompleted", g.isCompleted)
            if (g.deadlineMillis != null) obj.put("deadlineMillis", g.deadlineMillis)
            if (g.accountId != null) obj.put("accountId", g.accountId)
            goalArr.put(obj)
        }
        root.put("goals", goalArr)

        val debtArr = JSONArray()
        debts.forEach { d ->
            val obj = JSONObject()
            obj.put("id", d.id)
            obj.put("name", d.name)
            obj.put("kind", d.kind)
            obj.put("originalAmount", d.originalAmount)
            obj.put("remainingAmount", d.remainingAmount)
            obj.put("notes", d.notes)
            if (d.dueMillis != null) obj.put("dueMillis", d.dueMillis)
            debtArr.put(obj)
        }
        root.put("debts", debtArr)

        val invArr = JSONArray()
        investments.forEach { inv ->
            val obj = JSONObject()
            obj.put("id", inv.id)
            obj.put("name", inv.name)
            obj.put("kind", inv.kind)
            obj.put("currentValue", inv.currentValue)
            obj.put("notes", inv.notes)
            if (inv.quantity != null) obj.put("quantity", inv.quantity)
            obj.put("lastUpdatedMillis", inv.lastUpdatedMillis)
            invArr.put(obj)
        }
        root.put("investments", invArr)

        val catArr = JSONArray()
        categories.forEach { cat ->
            val obj = JSONObject()
            obj.put("id", cat.id)
            obj.put("name", cat.name)
            obj.put("kind", cat.kind)
            obj.put("isArchived", cat.isArchived)
            catArr.put(obj)
        }
        root.put("categories", catArr)

        val subcatArr = JSONArray()
        subcategories.forEach { sub ->
            val obj = JSONObject()
            obj.put("id", sub.id)
            obj.put("parentCategoryName", sub.parentCategoryName)
            obj.put("name", sub.name)
            obj.put("isArchived", sub.isArchived)
            obj.put("parentKind", sub.parentKind)
            subcatArr.put(obj)
        }
        root.put("subcategories", subcatArr)

        return root.toString(2)
    }

    val summary: BackupSummary
        get() = BackupSummary(version, exportedAt, accounts.size, transactions.size, budgets.size,
            goals.size, debts.size, investments.size)

    companion object {
        const val CURRENT_VERSION = 3
        private val SUPPORTED_VERSIONS = setOf(1, 2, CURRENT_VERSION)
        private val CORE_ARRAY_KEYS = listOf(
            "accounts", "transactions", "budgets", "goals", "debts", "investments"
        )

        fun fromJsonString(jsonStr: String): BackupData? {
            return try {
                val root = JSONObject(jsonStr)

                val versionValue = root.opt("version")
                if (versionValue !is Number || versionValue.toDouble() != versionValue.toInt().toDouble() ||
                    versionValue.toInt() !in SUPPORTED_VERSIONS) return null
                if (root.has("appName") && root.optString("appName") != "Piso") return null
                if (CORE_ARRAY_KEYS.any { root.optJSONArray(it) == null }) return null
                if (listOf("categories", "subcategories").any {
                        root.has(it) && root.optJSONArray(it) == null
                    }) return null
                val exportedAt = if (root.has("exportedAt") && !root.isNull("exportedAt")) {
                    root.getLong("exportedAt")
                } else null

                val accounts = mutableListOf<Account>()
                val accArr = root.getJSONArray("accounts")
                for (i in 0 until accArr.length()) {
                    val obj = accArr.getJSONObject(i)
                    accounts.add(
                        Account(
                            id = obj.optLong("id", 0L),
                            name = obj.getString("name"),
                            kind = obj.optString("kind", "Cash"),
                            balance = obj.optDouble("balance", 0.0),
                            notes = obj.optString("notes", "")
                        )
                    )
                }

                val transactions = mutableListOf<Transaction>()
                val txArr = root.getJSONArray("transactions")
                for (i in 0 until txArr.length()) {
                    val obj = txArr.getJSONObject(i)
                    transactions.add(
                        Transaction(
                            id = obj.optLong("id", 0L),
                            dateMillis = obj.optLong("dateMillis", System.currentTimeMillis()),
                            type = obj.getString("type"),
                            category = obj.optString("category", ""),
                            subcategory = obj.optString("subcategory", ""),
                            amount = obj.getDouble("amount"),
                            note = obj.optString("note", ""),
                            accountId = if (obj.has("accountId")) obj.getLong("accountId") else null,
                            transferToId = if (obj.has("transferToId")) obj.getLong("transferToId") else null,
                            goalId = if (obj.has("goalId")) obj.getLong("goalId") else null,
                            goalFlow = if (obj.has("goalFlow")) obj.getString("goalFlow") else null,
                            debtId = if (obj.has("debtId")) obj.getLong("debtId") else null
                        )
                    )
                }

                val budgets = mutableListOf<Budget>()
                val bgtArr = root.getJSONArray("budgets")
                for (i in 0 until bgtArr.length()) {
                    val obj = bgtArr.getJSONObject(i)
                    budgets.add(
                        Budget(
                            id = obj.optLong("id", 0L),
                            category = obj.getString("category"),
                            limitAmount = obj.getDouble("limitAmount"),
                            monthKey = obj.getString("monthKey")
                        )
                    )
                }

                val goals = mutableListOf<Goal>()
                val goalArr = root.getJSONArray("goals")
                for (i in 0 until goalArr.length()) {
                    val obj = goalArr.getJSONObject(i)
                    goals.add(
                        Goal(
                            id = obj.optLong("id", 0L),
                            name = obj.getString("name"),
                            targetAmount = obj.getDouble("targetAmount"),
                            currentAmount = obj.optDouble("currentAmount", 0.0),
                            isCompleted = obj.optBoolean("isCompleted", false),
                            deadlineMillis = if (obj.has("deadlineMillis")) obj.getLong("deadlineMillis") else null,
                            accountId = if (obj.has("accountId")) obj.getLong("accountId") else null
                        )
                    )
                }

                val debts = mutableListOf<Debt>()
                val debtArr = root.getJSONArray("debts")
                for (i in 0 until debtArr.length()) {
                    val obj = debtArr.getJSONObject(i)
                    debts.add(
                        Debt(
                            id = obj.optLong("id", 0L),
                            name = obj.getString("name"),
                            kind = obj.optString("kind", "Other"),
                            originalAmount = obj.getDouble("originalAmount"),
                            remainingAmount = obj.optDouble("remainingAmount", 0.0),
                            notes = obj.optString("notes", ""),
                            dueMillis = if (obj.has("dueMillis")) obj.getLong("dueMillis") else null
                        )
                    )
                }

                val investments = mutableListOf<Investment>()
                val invArr = root.getJSONArray("investments")
                for (i in 0 until invArr.length()) {
                    val obj = invArr.getJSONObject(i)
                    investments.add(
                        Investment(
                            id = obj.optLong("id", 0L),
                            name = obj.getString("name"),
                            kind = obj.optString("kind", "Other"),
                            currentValue = obj.getDouble("currentValue"),
                            notes = obj.optString("notes", ""),
                            quantity = if (obj.has("quantity")) obj.getString("quantity") else null,
                            lastUpdatedMillis = obj.optLong("lastUpdatedMillis", System.currentTimeMillis())
                        )
                    )
                }

                val categories = mutableListOf<UserCategory>()
                val catArr = root.optJSONArray("categories") ?: JSONArray()
                for (i in 0 until catArr.length()) {
                    val obj = catArr.getJSONObject(i)
                    categories.add(
                        UserCategory(
                            id = obj.optLong("id", 0L),
                            name = obj.getString("name"),
                            kind = obj.optString("kind", "EXPENSE"),
                            isArchived = obj.optBoolean("isArchived", false)
                        )
                    )
                }

                val subcategories = mutableListOf<UserSubcategory>()
                val subcatArr = root.optJSONArray("subcategories") ?: JSONArray()
                for (i in 0 until subcatArr.length()) {
                    val obj = subcatArr.getJSONObject(i)
                    val parentName = obj.getString("parentCategoryName")
                    val sub = UserSubcategory(
                        id = obj.optLong("id", 0L),
                        parentCategoryName = parentName,
                        name = obj.getString("name"),
                        isArchived = obj.optBoolean("isArchived", false)
                    )
                    val storedKind = obj.optString("parentKind", "").uppercase()
                    if (storedKind == "INCOME" || storedKind == "EXPENSE") {
                        subcategories.add(sub.copy(parentKind = storedKind))
                    } else {
                        // Older backups have no kind: attach to whichever kind(s) use the parent name
                        val kinds = categories.filter { it.name == parentName }.map { it.kind.uppercase() }.distinct()
                        if (kinds.isEmpty()) {
                            subcategories.add(sub.copy(parentKind = "EXPENSE"))
                        } else {
                            kinds.forEachIndexed { index, kind ->
                                // Only the first copy keeps the original id so ids stay unique
                                subcategories.add(sub.copy(id = if (index == 0) sub.id else 0L, parentKind = kind))
                            }
                        }
                    }
                }

                BackupData(accounts, transactions, budgets, goals, debts, investments, categories, subcategories,
                    version = versionValue.toInt(), exportedAt = exportedAt,
                    hasCategoryData = root.has("categories"))
            } catch (_: Exception) {
                null
            }
        }
    }
}
