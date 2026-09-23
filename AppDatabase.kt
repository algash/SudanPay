package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.BankAccountDao
import com.example.data.local.dao.OfflineQueueDao
import com.example.data.local.dao.SmsLogDao
import com.example.data.local.dao.SyncedSmsDao
import com.example.data.local.dao.TransactionDao
import com.example.data.local.entity.BankAccountEntity
import com.example.data.local.entity.OfflineQueueEntity
import com.example.data.local.entity.SmsLogEntity
import com.example.data.local.entity.TransactionEntity

/**
 * Main Room Database for SudanPay local storage.
 * Stores user bank accounts, transaction ledger, offline queue vouchers, and synced SMS logs.
 */
@Database(
    entities = [
        BankAccountEntity::class,
        TransactionEntity::class,
        OfflineQueueEntity::class,
        SmsLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bankAccountDao(): BankAccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun offlineQueueDao(): OfflineQueueDao
    abstract fun smsLogDao(): SmsLogDao
    
    fun syncedSmsDao(): SyncedSmsDao = smsLogDao()

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sudanpay_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Creates an in-memory database instance for testing.
         */
        fun getInMemoryDatabase(context: Context): AppDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                AppDatabase::class.java
            )
            .allowMainThreadQueries()
            .build()
        }
    }
}
