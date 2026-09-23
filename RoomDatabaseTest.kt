package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.BankAccountEntity
import com.example.data.local.entity.OfflineQueueEntity
import com.example.data.local.entity.SmsLogEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.repository.BankRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomDatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: BankRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.getInMemoryDatabase(context)
        repository = BankRepository(db, testDispatcher)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun testUserAccountsStorageAndOperations() = runTest(testDispatcher) {
        val account = BankAccountEntity(
            id = "bankak",
            bankNameAr = "بنك الخرطوم (بنكك)",
            bankNameEn = "Bank of Khartoum",
            accountNumber = "12345678",
            accountHolder = "أحمد محمد",
            balance = 100000.0,
            currency = "SDG",
            primaryColorHex = 0xFF0B5345,
            secondaryColorHex = 0xFF148F77,
            isPrimary = true,
            ussdCode = "*111#",
            smsShortCode = "1111",
            iconName = "bankak"
        )

        // 1. Insert account
        repository.addOrUpdateAccount(account)

        // 2. Fetch by ID
        val fetched = repository.getAccountById("bankak")
        assertNotNull(fetched)
        assertEquals("bankak", fetched?.id)
        assertEquals(100000.0, fetched?.balance ?: 0.0, 0.001)
        assertTrue(fetched?.isPrimary == true)

        // 3. Deduct balance
        repository.deductAccountBalance("bankak", 25000.0)
        val afterDeduct = repository.getAccountById("bankak")
        assertEquals(75000.0, afterDeduct?.balance ?: 0.0, 0.001)

        // 4. Credit balance
        repository.creditAccountBalance("bankak", 10000.0)
        val afterCredit = repository.getAccountById("bankak")
        assertEquals(85000.0, afterCredit?.balance ?: 0.0, 0.001)

        // 5. Total balance flow
        val total = repository.totalBalance.first()
        assertEquals(85000.0, total ?: 0.0, 0.001)
    }

    @Test
    fun testTransactionLedgerAndTransfer() = runTest(testDispatcher) {
        val account = BankAccountEntity(
            id = "okash",
            bankNameAr = "أوكاش",
            bankNameEn = "O-Kash",
            accountNumber = "0912345678",
            accountHolder = "سارة حسن",
            balance = 50000.0,
            currency = "SDG",
            primaryColorHex = 0xFFC0392B,
            secondaryColorHex = 0xFFE67E22,
            isPrimary = false,
            ussdCode = "*123#",
            smsShortCode = "123",
            iconName = "okash"
        )
        repository.addOrUpdateAccount(account)

        // Execute successful transfer
        val success = repository.executeTransfer(
            bankId = "okash",
            amount = 15000.0,
            recipientName = "محمد علي",
            recipientAccount = "0999123456",
            channel = "ONLINE",
            note = "سداد مستحقات"
        )
        assertTrue(success)

        // Account balance reduced
        val updatedAccount = repository.getAccountById("okash")
        assertEquals(35000.0, updatedAccount?.balance ?: 0.0, 0.001)

        // Transaction recorded in history
        val transactions = repository.transactions.first()
        assertEquals(1, transactions.size)
        assertEquals(15000.0, transactions[0].amount, 0.001)
        assertEquals("DEBIT", transactions[0].type)
        assertEquals("SUCCESS", transactions[0].status)
        assertEquals("محمد علي", transactions[0].recipientName)

        // Test overdrawn transfer failure
        val failSuccess = repository.executeTransfer(
            bankId = "okash",
            amount = 999999.0,
            recipientName = "شخص آخر",
            recipientAccount = "000000",
            channel = "ONLINE",
            note = "رصيد غير كافي"
        )
        assertFalse(failSuccess)
    }

    @Test
    fun testSyncedSmsStorageAndAutoBalanceApplication() = runTest(testDispatcher) {
        val account = BankAccountEntity(
            id = "faysal",
            bankNameAr = "بنك فيصل",
            bankNameEn = "FIB",
            accountNumber = "55124091",
            accountHolder = "علي عثمان",
            balance = 40000.0,
            currency = "SDG",
            primaryColorHex = 0xFF1F3A60,
            secondaryColorHex = 0xFF2E86C1,
            isPrimary = false,
            ussdCode = "*333#",
            smsShortCode = "3333",
            iconName = "faysal"
        )
        repository.addOrUpdateAccount(account)

        // Apply incoming parsed SMS credit
        val smsText = "تم إيداع مبلغ 20,000 SDG في حسابك 55124091. الرصيد الجديد: 60,000 SDG"
        repository.applyParsedSms(
            bankId = "faysal",
            amount = 20000.0,
            newBalance = 60000.0,
            type = "CREDIT",
            txId = "FIB-88129",
            sender = "FIB-SMS",
            message = smsText
        )

        // Check account balance updated to new balance
        val updatedAccount = repository.getAccountById("faysal")
        assertEquals(60000.0, updatedAccount?.balance ?: 0.0, 0.001)

        // Check SMS stored in synced logs
        val smsLogs = repository.smsLogs.first()
        assertEquals(1, smsLogs.size)
        assertEquals("FIB-SMS", smsLogs[0].sender)
        assertEquals(20000.0, smsLogs[0].parsedAmount ?: 0.0, 0.001)
        assertEquals("FIB-88129", smsLogs[0].parsedTxId)
        assertTrue(smsLogs[0].isParsed)
        assertTrue(smsLogs[0].isSynced)

        // Check transaction was automatically generated for ledger
        val transactions = repository.transactions.first()
        assertEquals(1, transactions.size)
        assertEquals("CREDIT", transactions[0].type)
        assertEquals(20000.0, transactions[0].amount, 0.001)
        assertEquals("FIB-88129", transactions[0].txId)
    }

    @Test
    fun testOfflineQueueStoreAndForward() = runTest(testDispatcher) {
        val voucher = OfflineQueueEntity(
            voucherCode = "VOUCH-1029",
            fromBankId = "bankak",
            recipient = "0911223344",
            amount = 12000.0,
            signedPayload = "SPAY#TRF#V1#ENCRYPTED",
            channel = "SMS_ENCRYPTED",
            isSynced = false
        )

        val id = repository.saveOfflineVoucher(voucher)
        assertTrue(id > 0)

        val pending = repository.pendingOfflineVouchers.first()
        assertEquals(1, pending.size)
        assertEquals("VOUCH-1029", pending[0].voucherCode)
        assertFalse(pending[0].isSynced)

        // Mark as synced
        repository.syncOfflineItem(pending[0].id)
        val pendingAfterSync = repository.pendingOfflineVouchers.first()
        assertEquals(0, pendingAfterSync.size)
    }
}
