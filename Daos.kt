package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entity.BankAccountEntity
import com.example.data.local.entity.OfflineQueueEntity
import com.example.data.local.entity.SmsLogEntity
import com.example.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for local User Bank & Wallet Accounts.
 */
@Dao
interface BankAccountDao {
    @Query("SELECT * FROM bank_accounts ORDER BY isPrimary DESC, balance DESC")
    fun getAllAccounts(): Flow<List<BankAccountEntity>>

    @Query("SELECT * FROM bank_accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountById(id: String): BankAccountEntity?

    @Query("SELECT * FROM bank_accounts WHERE id = :id LIMIT 1")
    fun getAccountByIdFlow(id: String): Flow<BankAccountEntity?>

    @Query("SELECT SUM(balance) FROM bank_accounts")
    fun getTotalBalance(): Flow<Double?>

    @Query("SELECT COUNT(*) FROM bank_accounts")
    fun getAccountCount(): Flow<Int>

    @Query("SELECT * FROM bank_accounts WHERE isPrimary = 1 LIMIT 1")
    suspend fun getPrimaryAccount(): BankAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(account: BankAccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<BankAccountEntity>)

    @Update
    suspend fun update(account: BankAccountEntity)

    @Delete
    suspend fun deleteAccount(account: BankAccountEntity)

    @Query("DELETE FROM bank_accounts WHERE id = :id")
    suspend fun deleteAccountById(id: String)

    @Query("UPDATE bank_accounts SET balance = balance - :amount, lastSyncedAt = :timestamp WHERE id = :id")
    suspend fun deductBalance(id: String, amount: Double, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE bank_accounts SET balance = balance + :amount, lastSyncedAt = :timestamp WHERE id = :id")
    suspend fun creditBalance(id: String, amount: Double, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE bank_accounts SET balance = :newBalance, lastSyncedAt = :timestamp WHERE id = :id")
    suspend fun setBalance(id: String, newBalance: Double, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE bank_accounts SET isPrimary = 0")
    suspend fun clearPrimaryFlags()

    @Query("UPDATE bank_accounts SET isPrimary = 1 WHERE id = :id")
    suspend fun markAsPrimary(id: String)

    @Transaction
    suspend fun setPrimaryAccount(id: String) {
        clearPrimaryFlags()
        markAsPrimary(id)
    }
}

/**
 * Data Access Object for local financial transactions ledger.
 */
@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE bankAccountId = :bankId ORDER BY timestamp DESC LIMIT 30")
    fun getTransactionsByBank(bankId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY timestamp DESC")
    fun getTransactionsByType(type: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentTransactions(limit: Int = 20): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE txId = :txId LIMIT 1")
    suspend fun getTransactionByTxId(txId: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>): List<Long>

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: Long)

    @Query("DELETE FROM transactions WHERE bankAccountId = :bankId")
    suspend fun deleteTransactionsByBank(bankId: String)

    @Query("DELETE FROM transactions")
    suspend fun clearAllTransactions()

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'DEBIT' AND status = 'SUCCESS'")
    fun getTotalSpent(): Flow<Double?>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'CREDIT' AND status = 'SUCCESS'")
    fun getTotalReceived(): Flow<Double?>

    @Query("SELECT COUNT(*) FROM transactions")
    fun getTransactionCount(): Flow<Int>
}

/**
 * Data Access Object for synced and parsed SMS data.
 */
@Dao
interface SmsLogDao {
    @Query("SELECT * FROM sms_logs ORDER BY receivedAt DESC")
    fun getAllSmsLogs(): Flow<List<SmsLogEntity>>

    @Query("SELECT * FROM sms_logs WHERE isSynced = 0 ORDER BY receivedAt DESC")
    fun getUnsyncedSms(): Flow<List<SmsLogEntity>>

    @Query("SELECT * FROM sms_logs WHERE isParsed = 1 ORDER BY receivedAt DESC")
    fun getParsedSms(): Flow<List<SmsLogEntity>>

    @Query("SELECT * FROM sms_logs WHERE sender LIKE '%' || :sender || '%' ORDER BY receivedAt DESC")
    fun getSmsBySender(sender: String): Flow<List<SmsLogEntity>>

    @Query("SELECT * FROM sms_logs WHERE id = :id LIMIT 1")
    suspend fun getSmsById(id: Long): SmsLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SmsLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllLogs(logs: List<SmsLogEntity>): List<Long>

    @Query("UPDATE sms_logs SET isParsed = 1, parsedAmount = :parsedAmount, parsedBalance = :parsedBalance, parsedTxId = :parsedTxId, bankId = :bankId WHERE id = :id")
    suspend fun markSmsParsed(
        id: Long,
        parsedAmount: Double?,
        parsedBalance: Double?,
        parsedTxId: String?,
        bankId: String? = null
    )

    @Query("UPDATE sms_logs SET isSynced = 1, syncedAt = :syncedAt WHERE id = :id")
    suspend fun markSmsSynced(id: Long, syncedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM sms_logs WHERE id = :id")
    suspend fun deleteSms(id: Long)

    @Query("DELETE FROM sms_logs WHERE receivedAt < :beforeTimestamp")
    suspend fun clearOldSms(beforeTimestamp: Long)

    @Query("SELECT COUNT(*) FROM sms_logs")
    fun getSmsCount(): Flow<Int>
}

/**
 * Alias for Synced SMS DAO.
 */
typealias SyncedSmsDao = SmsLogDao

/**
 * Data Access Object for offline queued transactions and vouchers.
 */
@Dao
interface OfflineQueueDao {
    @Query("SELECT * FROM offline_queue ORDER BY createdAt DESC")
    fun getAllOfflineQueued(): Flow<List<OfflineQueueEntity>>

    @Query("SELECT * FROM offline_queue WHERE isSynced = 0 ORDER BY createdAt ASC")
    fun getPendingVouchers(): Flow<List<OfflineQueueEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: OfflineQueueEntity): Long

    @Query("UPDATE offline_queue SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)

    @Query("DELETE FROM offline_queue WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM offline_queue WHERE isSynced = 1")
    suspend fun clearSynced()
}
