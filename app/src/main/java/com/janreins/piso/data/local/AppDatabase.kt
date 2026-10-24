package com.janreins.piso.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.janreins.piso.data.models.Account
import com.janreins.piso.data.models.Budget
import com.janreins.piso.data.models.Debt
import com.janreins.piso.data.models.Goal
import com.janreins.piso.data.models.Investment
import com.janreins.piso.data.models.Transaction
import com.janreins.piso.data.models.UserCategory
import com.janreins.piso.data.models.UserSubcategory

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `subcategory` TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `user_categories` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `isArchived` INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `user_subcategories` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `parentCategoryName` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `isArchived` INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }
}

/**
 * Adds the parent kind to subcategories. Existing subcategories are assigned to whichever
 * category kind(s) use their parent name; a name used by both kinds gets a copy for each.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `user_subcategories` ADD COLUMN `parentKind` TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            """
            UPDATE `user_subcategories` SET `parentKind` = 'EXPENSE'
            WHERE `parentCategoryName` IN (SELECT `name` FROM `user_categories` WHERE `kind` = 'EXPENSE')
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `user_subcategories` (`parentCategoryName`, `name`, `isArchived`, `parentKind`)
            SELECT `parentCategoryName`, `name`, `isArchived`, 'INCOME' FROM `user_subcategories`
            WHERE `parentKind` = 'EXPENSE'
              AND `parentCategoryName` IN (SELECT `name` FROM `user_categories` WHERE `kind` = 'INCOME')
            """.trimIndent()
        )
        db.execSQL(
            """
            UPDATE `user_subcategories` SET `parentKind` = 'INCOME'
            WHERE `parentKind` = ''
              AND `parentCategoryName` IN (SELECT `name` FROM `user_categories` WHERE `kind` = 'INCOME')
            """.trimIndent()
        )
        // Orphans (parent category no longer exists) default to expense
        db.execSQL("UPDATE `user_subcategories` SET `parentKind` = 'EXPENSE' WHERE `parentKind` = ''")
    }
}

/**
 * Lets an old account be archived. Existing accounts start unarchived and keep their balances.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `isArchived` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Main Room Database for Piso personal money book.
 */
@Database(
    entities = [
        Account::class,
        Transaction::class,
        Budget::class,
        Goal::class,
        Debt::class,
        Investment::class,
        UserCategory::class,
        UserSubcategory::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun goalDao(): GoalDao
    abstract fun debtDao(): DebtDao
    abstract fun investmentDao(): InvestmentDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "piso_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
