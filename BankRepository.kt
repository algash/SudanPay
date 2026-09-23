package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.entity.BankAccountEntity
import com.example.data.local.entity.OfflineQueueEntity
import com.example.data.local.entity.SmsLogEntity
import com.example.data.local.entity.TransactionEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Interface defining the repository contract for SudanPay data access.
 * Abstracted across User Accounts, Financial Transactions, Synced SMS, and Offline Queues.
 */
interface ISudanPayRepository {
    // User Accounts
    val accounts: Flow<List<BankAccountEntity>>
    val totalBalance: Flow<Double?>
    val accountCount: Flow<Int>
    fun getAccountByIdFlow(id: String): Flow<BankAccountEntity?>
    suspend fun getAccountById(id: String): BankAccountEntity?
    suspend fun getPrimaryAccount(): BankAccountEntity?
    suspend fun addOrUpdateAccount(account: BankAccountEntity)
    suspend fun updateAccountBalance(id: String, newBalance: Double)
    suspend fun creditAccountBalance(id: String, amount: Double)
    suspend fun deductAccountBalance(id: String, amount: Double)
    suspend fun setPrimaryAccount(id: String)
    suspend fun deleteAccount(id: String)
    suspend fun initializeDefaultAccountsIfEmpty()

    // Transaction History
    val transactions: Flow<List<TransactionEntity>>
    val totalSpent: Flow<Double?>
    val totalReceived: Flow<Double?>
    fun getTransactionsForBank(bankId: String): Flow<List<TransactionEntity>>
    fun getTransactionsByType(type: String): Flow<List<TransactionEntity>>
    fun getRecentTransactions(limit: Int = 20): Flow<List<TransactionEntity>>
    suspend fun recordTransaction(transaction: TransactionEntity): Long
    suspend fun executeTransfer(
        bankId: String,
        amount: Double,
        recipientName: String,
        recipientAccount: String,
        channel: String,
        note: String
    ): Boolean
    suspend fun deleteTransaction(id: Long)
    suspend fun clearAllTransactions()

    // Synced SMS Data
    val smsLogs: Flow<List<SmsLogEntity>>
    val unsyncedSms: Flow<List<SmsLogEntity>>
    val parsedSms: Flow<List<SmsLogEntity>>
    fun getSmsBySender(sender: String): Flow<List<SmsLogEntity>>
    suspend fun recordSmsLog(sms: SmsLogEntity): Long
    suspend fun syncIncomingSms(sender: String, messageBody: String): Long
    suspend fun markSmsSynced(id: Long)
    suspend fun applyParsedSms(
        bankId: String,
        amount: Double,
        newBalance: Double?,
        type: String,
        txId: String?,
        sender: String,
        message: String
    )
    suspend fun deleteSmsLog(id: Long)
    suspend fun clearOldSmsLogs(beforeTimestamp: Long)

    // Offline Queue
    val offlineQueue: Flow<List<OfflineQueueEntity>>
    val pendingOfflineVouchers: Flow<List<OfflineQueueEntity>>
    suspend fun saveOfflineVoucher(item: OfflineQueueEntity): Long
    suspend fun syncOfflineItem(id: Long)
    suspend fun deleteOfflineItem(id: Long)
    suspend fun clearSyncedOfflineItems()
}

/**
 * Concrete Repository implementation backed by Room Database using Kotlin Coroutines and Flows.
 */
class BankRepository(
    private val db: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ISudanPayRepository {

    private val bankAccountDao = db.bankAccountDao()
    private val transactionDao = db.transactionDao()
    private val smsLogDao = db.smsLogDao()
    private val offlineQueueDao = db.offlineQueueDao()

    // ==========================================
    // 1. User Accounts Layer
    // ==========================================

    override val accounts: Flow<List<BankAccountEntity>> = bankAccountDao.getAllAccounts()
    override val totalBalance: Flow<Double?> = bankAccountDao.getTotalBalance()
    override val accountCount: Flow<Int> = bankAccountDao.getAccountCount()

    override fun getAccountByIdFlow(id: String): Flow<BankAccountEntity?> {
        return bankAccountDao.getAccountByIdFlow(id)
    }

    override suspend fun getAccountById(id: String): BankAccountEntity? = withContext(ioDispatcher) {
        bankAccountDao.getAccountById(id)
    }

    override suspend fun getPrimaryAccount(): BankAccountEntity? = withContext(ioDispatcher) {
        bankAccountDao.getPrimaryAccount()
    }

    override suspend fun addOrUpdateAccount(account: BankAccountEntity) {
        withContext(ioDispatcher) {
            bankAccountDao.insertOrUpdate(account)
        }
    }

    override suspend fun updateAccountBalance(id: String, newBalance: Double) {
        withContext(ioDispatcher) {
            bankAccountDao.setBalance(id, newBalance)
        }
    }

    override suspend fun creditAccountBalance(id: String, amount: Double) {
        withContext(ioDispatcher) {
            bankAccountDao.creditBalance(id, amount)
        }
    }

    override suspend fun deductAccountBalance(id: String, amount: Double) {
        withContext(ioDispatcher) {
            bankAccountDao.deductBalance(id, amount)
        }
    }

    override suspend fun setPrimaryAccount(id: String) {
        withContext(ioDispatcher) {
            bankAccountDao.setPrimaryAccount(id)
        }
    }

    override suspend fun deleteAccount(id: String) {
        withContext(ioDispatcher) {
            bankAccountDao.deleteAccountById(id)
        }
    }

    override suspend fun initializeDefaultAccountsIfEmpty() {
        withContext(ioDispatcher) {
            val existing = bankAccountDao.getAllAccounts().first()
            if (existing.isEmpty()) {
                val defaultAccounts = listOf(
                    BankAccountEntity(
                        id = "bankak",
                        bankNameAr = "بنك الخرطوم (بنكك)",
                        bankNameEn = "Bankak - Bank of Khartoum",
                        accountNumber = "2498 1029 4812",
                        accountHolder = "محمد أحمد عبد الله",
                        balance = 345000.0,
                        currency = "SDG",
                        primaryColorHex = 0xFF0B5345, // Bankak Emerald
                        secondaryColorHex = 0xFF148F77,
                        isPrimary = true,
                        ussdCode = "*111#",
                        smsShortCode = "1111",
                        iconName = "bankak"
                    ),
                    BankAccountEntity(
                        id = "okash",
                        bankNameAr = "أوكاش (سوداني)",
                        bankNameEn = "O-Kash - Sudani Wallet",
                        accountNumber = "0912 345 678",
                        accountHolder = "محمد أحمد عبد الله",
                        balance = 128500.0,
                        currency = "SDG",
                        primaryColorHex = 0xFFC0392B, // O-Kash Crimson
                        secondaryColorHex = 0xFFE67E22,
                        isPrimary = false,
                        ussdCode = "*123#",
                        smsShortCode = "123",
                        iconName = "okash"
                    ),
                    BankAccountEntity(
                        id = "sahel",
                        bankNameAr = "بنك الساحل والصحراء (ساهل)",
                        bankNameEn = "BSIC - Sahel Pay",
                        accountNumber = "7820 9941 0021",
                        accountHolder = "محمد أحمد عبد الله",
                        balance = 92000.0,
                        currency = "SDG",
                        primaryColorHex = 0xFF0E6655, // Sahel Teal
                        secondaryColorHex = 0xFF16A085,
                        isPrimary = false,
                        ussdCode = "*777#",
                        smsShortCode = "7777",
                        iconName = "sahel"
                    ),
                    BankAccountEntity(
                        id = "faysal",
                        bankNameAr = "بنك فيصل الإسلامي (فوري)",
                        bankNameEn = "FIB - Fawry Pay",
                        accountNumber = "5512 4091 3328",
                        accountHolder = "محمد أحمد عبد الله",
                        balance = 215000.0,
                        currency = "SDG",
                        primaryColorHex = 0xFF1F3A60, // FIB Royal Blue
                        secondaryColorHex = 0xFF2E86C1,
                        isPrimary = false,
                        ussdCode = "*333#",
                        smsShortCode = "3333",
                        iconName = "faysal"
                    ),
                    BankAccountEntity(
                        id = "wallet",
                        bankNameAr = "المحفظة الموحدة (SudanPay)",
                        bankNameEn = "SudanPay Unified Pool",
                        accountNumber = "SP-992-817",
                        accountHolder = "محمد أحمد عبد الله",
                        balance = 45000.0,
                        currency = "SDG",
                        primaryColorHex = 0xFF044343, // Unified Dark Cyan
                        secondaryColorHex = 0xFF00B4D8,
                        isPrimary = false,
                        ussdCode = "*249#",
                        smsShortCode = "2490",
                        iconName = "wallet"
                    )
                )
                bankAccountDao.insertAll(defaultAccounts)

                // Seed initial transactions
                val initialTransactions = listOf(
                    TransactionEntity(
                        txId = "TX-99210",
                        bankAccountId = "bankak",
                        bankNameAr = "بنك الخرطوم (بنكك)",
                        amount = 50000.0,
                        recipientName = "عمر الشيخ",
                        recipientAccount = "1928301",
                        type = "DEBIT",
                        status = "SUCCESS",
                        channel = "ONLINE",
                        note = "شراء وقود ومشتريات",
                        timestamp = System.currentTimeMillis() - 3600000 * 4
                    ),
                    TransactionEntity(
                        txId = "TX-99209",
                        bankAccountId = "okash",
                        bankNameAr = "أوكاش (سوداني)",
                        amount = 15000.0,
                        recipientName = "فاطمة إبراهيم",
                        recipientAccount = "0918765432",
                        type = "CREDIT",
                        status = "SUCCESS",
                        channel = "SMS_PARSED",
                        note = "استلام عبر أوكاش",
                        timestamp = System.currentTimeMillis() - 3600000 * 18
                    ),
                    TransactionEntity(
                        txId = "TX-99208",
                        bankAccountId = "faysal",
                        bankNameAr = "بنك فيصل الإسلامي",
                        amount = 80000.0,
                        recipientName = "شركة الاتصالات زين",
                        recipientAccount = "ZAIN-PAY",
                        type = "DEBIT",
                        status = "SUCCESS",
                        channel = "ONLINE",
                        note = "سداد فاتورة باقة إنترنت",
                        timestamp = System.currentTimeMillis() - 3600000 * 36
                    ),
                    TransactionEntity(
                        txId = "TX-99207",
                        bankAccountId = "bankak",
                        bankNameAr = "بنك الخرطوم (بنكك)",
                        amount = 25000.0,
                        recipientName = "صيدلية النيل",
                        recipientAccount = "2091823",
                        type = "DEBIT",
                        status = "OFFLINE_QUEUED",
                        channel = "SMS_ENCRYPTED",
                        note = "تحويل أوفلاين أثناء انقطاع الشبكة",
                        timestamp = System.currentTimeMillis() - 3600000 * 52
                    )
                )
                transactionDao.insertTransactions(initialTransactions)
            }
        }
    }

    // ==========================================
    // 2. Financial Transaction History Layer
    // ==========================================

    override val transactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()
    override val totalSpent: Flow<Double?> = transactionDao.getTotalSpent()
    override val totalReceived: Flow<Double?> = transactionDao.getTotalReceived()

    override fun getTransactionsForBank(bankId: String): Flow<List<TransactionEntity>> {
        return transactionDao.getTransactionsByBank(bankId)
    }

    override fun getTransactionsByType(type: String): Flow<List<TransactionEntity>> {
        return transactionDao.getTransactionsByType(type)
    }

    override fun getRecentTransactions(limit: Int): Flow<List<TransactionEntity>> {
        return transactionDao.getRecentTransactions(limit)
    }

    override suspend fun recordTransaction(transaction: TransactionEntity): Long = withContext(ioDispatcher) {
        transactionDao.insertTransaction(transaction)
    }

    override suspend fun executeTransfer(
        bankId: String,
        amount: Double,
        recipientName: String,
        recipientAccount: String,
        channel: String,
        note: String
    ): Boolean = withContext(ioDispatcher) {
        val account = bankAccountDao.getAccountById(bankId) ?: return@withContext false
        if (account.balance < amount) return@withContext false

        bankAccountDao.deductBalance(bankId, amount)

        val tx = TransactionEntity(
            txId = "TX-" + System.currentTimeMillis().toString().takeLast(6),
            bankAccountId = bankId,
            bankNameAr = account.bankNameAr,
            amount = amount,
            recipientName = recipientName.ifBlank { "مستفيد" },
            recipientAccount = recipientAccount,
            type = "DEBIT",
            status = if (channel == "ONLINE") "SUCCESS" else "OFFLINE_QUEUED",
            channel = channel,
            note = note.ifBlank { "تحويل عبر $channel" },
            timestamp = System.currentTimeMillis()
        )
        transactionDao.insertTransaction(tx)
        true
    }

    override suspend fun deleteTransaction(id: Long) {
        withContext(ioDispatcher) {
            transactionDao.deleteTransaction(id)
        }
    }

    override suspend fun clearAllTransactions() {
        withContext(ioDispatcher) {
            transactionDao.clearAllTransactions()
        }
    }

    // ==========================================
    // 3. Synced SMS Data Layer
    // ==========================================

    override val smsLogs: Flow<List<SmsLogEntity>> = smsLogDao.getAllSmsLogs()
    override val unsyncedSms: Flow<List<SmsLogEntity>> = smsLogDao.getUnsyncedSms()
    override val parsedSms: Flow<List<SmsLogEntity>> = smsLogDao.getParsedSms()

    override fun getSmsBySender(sender: String): Flow<List<SmsLogEntity>> {
        return smsLogDao.getSmsBySender(sender)
    }

    override suspend fun recordSmsLog(sms: SmsLogEntity): Long = withContext(ioDispatcher) {
        smsLogDao.insertLog(sms)
    }

    override suspend fun syncIncomingSms(sender: String, messageBody: String): Long = withContext(ioDispatcher) {
        smsLogDao.insertLog(
            SmsLogEntity(
                sender = sender,
                messageBody = messageBody,
                receivedAt = System.currentTimeMillis(),
                isParsed = false,
                isSynced = true,
                syncedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun markSmsSynced(id: Long) {
        withContext(ioDispatcher) {
            smsLogDao.markSmsSynced(id)
        }
    }

    override suspend fun applyParsedSms(
        bankId: String,
        amount: Double,
        newBalance: Double?,
        type: String,
        txId: String?,
        sender: String,
        message: String
    ) {
        withContext(ioDispatcher) {
            val account = bankAccountDao.getAccountById(bankId)
            val bankTitle = account?.bankNameAr ?: "إشعار بنكي"

            smsLogDao.insertLog(
                SmsLogEntity(
                    sender = sender,
                    messageBody = message,
                    receivedAt = System.currentTimeMillis(),
                    parsedAmount = amount,
                    parsedBalance = newBalance,
                    parsedTxId = txId,
                    bankId = bankId,
                    isParsed = true,
                    isSynced = true,
                    syncedAt = System.currentTimeMillis()
                )
            )

            if (newBalance != null && newBalance > 0.0) {
                bankAccountDao.setBalance(bankId, newBalance)
            } else if (amount > 0.0) {
                if (type == "CREDIT") {
                    bankAccountDao.creditBalance(bankId, amount)
                } else {
                    bankAccountDao.deductBalance(bankId, amount)
                }
            }

            transactionDao.insertTransaction(
                TransactionEntity(
                    txId = txId ?: ("SMS-" + System.currentTimeMillis().toString().takeLast(6)),
                    bankAccountId = bankId,
                    bankNameAr = bankTitle,
                    amount = amount,
                    recipientName = "إشعار مصرفي وارد ($sender)",
                    recipientAccount = sender,
                    type = type,
                    status = "SUCCESS",
                    channel = "SMS_PARSED",
                    note = "تحديث فوري للرصيد عبر معالج الرسائل الذكي",
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    override suspend fun deleteSmsLog(id: Long) {
        withContext(ioDispatcher) {
            smsLogDao.deleteSms(id)
        }
    }

    override suspend fun clearOldSmsLogs(beforeTimestamp: Long) {
        withContext(ioDispatcher) {
            smsLogDao.clearOldSms(beforeTimestamp)
        }
    }

    // ==========================================
    // 4. Offline Queue & Vouchers Layer
    // ==========================================

    override val offlineQueue: Flow<List<OfflineQueueEntity>> = offlineQueueDao.getAllOfflineQueued()
    override val pendingOfflineVouchers: Flow<List<OfflineQueueEntity>> = offlineQueueDao.getPendingVouchers()

    override suspend fun saveOfflineVoucher(item: OfflineQueueEntity): Long = withContext(ioDispatcher) {
        offlineQueueDao.enqueue(item)
    }

    override suspend fun syncOfflineItem(id: Long) {
        withContext(ioDispatcher) {
            offlineQueueDao.markSynced(id)
        }
    }

    override suspend fun deleteOfflineItem(id: Long) {
        withContext(ioDispatcher) {
            offlineQueueDao.delete(id)
        }
    }

    override suspend fun clearSyncedOfflineItems() {
        withContext(ioDispatcher) {
            offlineQueueDao.clearSynced()
        }
    }
}
