package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.domain.sms.BankShortcodes
import com.example.receiver.BankSmsReceiver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BankSmsReceiverTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.getDatabase(context)
    }

    @After
    fun tearDown() {
        // cleanup if needed
    }

    @Test
    fun testBankShortcodeRecognition() {
        // Bank of Khartoum shortcodes
        assertTrue("2000 should be recognized as BOK shortcode", BankShortcodes.isBankShortcode("2000"))
        assertTrue("2002 should be recognized as BOK shortcode", BankShortcodes.isBankShortcode("2002"))
        assertTrue("BOK alphanumeric should be recognized", BankShortcodes.isBankShortcode("BOK"))
        assertTrue("Bankak alphanumeric should be recognized", BankShortcodes.isBankShortcode("Bankak"))
        assertTrue("BOK-Alert should be recognized", BankShortcodes.isBankShortcode("BOK-Alert"))

        // O-Kash / ONB shortcodes
        assertTrue("3030 should be recognized as O-Kash shortcode", BankShortcodes.isBankShortcode("3030"))
        assertTrue("OKASH should be recognized", BankShortcodes.isBankShortcode("OKASH"))
        assertTrue("Sudani should be recognized", BankShortcodes.isBankShortcode("Sudani"))

        // Faisal Islamic Bank shortcodes
        assertTrue("1212 should be recognized as FIB shortcode", BankShortcodes.isBankShortcode("1212"))
        assertTrue("FIB should be recognized", BankShortcodes.isBankShortcode("FIB"))
        assertTrue("FAWRY should be recognized", BankShortcodes.isBankShortcode("FAWRY"))

        // Sahel and Sahara shortcodes
        assertTrue("4040 should be recognized as Sahel shortcode", BankShortcodes.isBankShortcode("4040"))
        assertTrue("Sahel should be recognized", BankShortcodes.isBankShortcode("Sahel"))

        // Non-bank numbers
        assertFalse("Personal number without keywords should not match", BankShortcodes.isBankShortcode("0912345678"))
        assertFalse("Random number should not match", BankShortcodes.isBankShortcode("778899"))
    }

    @Test
    fun testShouldInterceptAndParse() {
        // Direct shortcode
        assertTrue(
            "Should intercept SMS from 2000",
            BankShortcodes.shouldInterceptAndParse("2000", "تم إيداع مبلغ 50000 ج.س")
        )

        // Alphanumeric sender
        assertTrue(
            "Should intercept SMS from OKASH",
            BankShortcodes.shouldInterceptAndParse("OKash", "تم استلام 15000 ج.س")
        )

        // Non-bank sender with no financial content
        assertFalse(
            "Should ignore personal chat message",
            BankShortcodes.shouldInterceptAndParse("0912345678", "السلام عليكم كيف حالك يا صديقي")
        )
    }

    @Test
    fun testAutomatedParsingLogicExecution() = runBlocking {
        val testSender = "2000"
        val testMessage = "تم إيداع مبلغ 75,000.00 SDG في حسابك 2498****4812 من 0912345678. الرصيد المتاح 450,000.00 SDG. الرقم المرجعي BOK772211"

        // Execute automated processing logic
        val parsedResult = BankSmsReceiver.processIncomingBankSms(context, testSender, testMessage)

        assertNotNull("Parsed result should not be null", parsedResult)
        assertEquals("bankak", parsedResult?.bankId)
        assertEquals(75000.0, parsedResult?.amount ?: 0.0, 0.01)
        assertEquals(450000.0, parsedResult?.newBalance ?: 0.0, 0.01)
        assertEquals("BOK772211", parsedResult?.transactionId)
        assertEquals("CREDIT", parsedResult?.type)

        // Verify Room Database update
        val updatedAccount = db.bankAccountDao().getAccountById("bankak")
        assertNotNull(updatedAccount)
        assertEquals(450000.0, updatedAccount?.balance ?: 0.0, 0.01)

        // Verify SMS log saved
        val logs = db.smsLogDao().getAllSmsLogs().first()
        assertTrue("SMS log should be stored", logs.any { it.sender == "2000" && it.parsedTxId == "BOK772211" })

        // Verify Transaction saved
        val txs = db.transactionDao().getAllTransactions().first()
        assertTrue("Transaction should be recorded in ledger", txs.any { it.txId == "BOK772211" })
    }

    @Test
    fun testSimulatedBroadcastIntentHandling() {
        val receiver = BankSmsReceiver()
        val intent = Intent(BankSmsReceiver.ACTION_SIMULATE_SMS).apply {
            putExtra(BankSmsReceiver.EXTRA_SENDER, "3030")
            putExtra(BankSmsReceiver.EXTRA_BODY, "تم استلام 25000 ج.س من 0918765432. رصيدك الجديد 175000 ج.س. معرف العملية: 9944112")
        }

        // Broadcast simulation intent
        receiver.onReceive(context, intent)
        // Should execute cleanly without throwing
    }
}
