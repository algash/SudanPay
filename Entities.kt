package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Entity representing a user's linked bank or wallet account
 * (e.g. Bankak, O-Kash, Sahel, Fawry, Unified Pool).
 */
@Entity(
    tableName = "bank_accounts",
    indices = [
        Index(value = ["accountNumber"], unique = false),
        Index(value = ["isPrimary"])
    ]
)
data class BankAccountEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "bankNameAr")
    val bankNameAr: String,

    @ColumnInfo(name = "bankNameEn")
    val bankNameEn: String,

    @ColumnInfo(name = "accountNumber")
    val accountNumber: String,

    @ColumnInfo(name = "accountHolder")
    val accountHolder: String,

    @ColumnInfo(name = "balance")
    val balance: Double,

    @ColumnInfo(name = "currency")
    val currency: String = "SDG",

    @ColumnInfo(name = "primaryColorHex")
    val primaryColorHex: Long,

    @ColumnInfo(name = "secondaryColorHex")
    val secondaryColorHex: Long,

    @ColumnInfo(name = "isPrimary")
    val isPrimary: Boolean = false,

    @ColumnInfo(name = "ussdCode")
    val ussdCode: String,

    @ColumnInfo(name = "smsShortCode")
    val smsShortCode: String,

    @ColumnInfo(name = "iconName")
    val iconName: String,

    @ColumnInfo(name = "lastSyncedAt")
    val lastSyncedAt: Long = System.currentTimeMillis()
)

/**
 * Room Entity representing the local financial ledger and transaction history.
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["bankAccountId"]),
        Index(value = ["timestamp"]),
        Index(value = ["type"]),
        Index(value = ["status"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "txId")
    val txId: String,

    @ColumnInfo(name = "bankAccountId")
    val bankAccountId: String,

    @ColumnInfo(name = "bankNameAr")
    val bankNameAr: String,

    @ColumnInfo(name = "amount")
    val amount: Double,

    @ColumnInfo(name = "recipientName")
    val recipientName: String,

    @ColumnInfo(name = "recipientAccount")
    val recipientAccount: String,

    @ColumnInfo(name = "type")
    val type: String, // "DEBIT" or "CREDIT"

    @ColumnInfo(name = "status")
    val status: String, // "SUCCESS", "OFFLINE_QUEUED", "PENDING_SMS", "FAILED"

    @ColumnInfo(name = "channel")
    val channel: String, // "ONLINE", "SMS_ENCRYPTED", "QR_FORWARD", "SMS_PARSED"

    @ColumnInfo(name = "note")
    val note: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Room Entity representing synced and parsed SMS messages from Sudanese financial institutions.
 */
@Entity(
    tableName = "sms_logs",
    indices = [
        Index(value = ["sender"]),
        Index(value = ["receivedAt"]),
        Index(value = ["isParsed"]),
        Index(value = ["isSynced"])
    ]
)
data class SmsLogEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "sender")
    val sender: String,

    @ColumnInfo(name = "messageBody")
    val messageBody: String,

    @ColumnInfo(name = "receivedAt")
    val receivedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "parsedAmount")
    val parsedAmount: Double? = null,

    @ColumnInfo(name = "parsedBalance")
    val parsedBalance: Double? = null,

    @ColumnInfo(name = "parsedTxId")
    val parsedTxId: String? = null,

    @ColumnInfo(name = "bankId")
    val bankId: String? = null,

    @ColumnInfo(name = "isParsed")
    val isParsed: Boolean = false,

    @ColumnInfo(name = "isSynced")
    val isSynced: Boolean = false,

    @ColumnInfo(name = "syncedAt")
    val syncedAt: Long? = null
)

/**
 * Alias for Synced SMS records.
 */
typealias SyncedSmsEntity = SmsLogEntity

/**
 * Room Entity representing offline-generated cryptographic vouchers and store-and-forward queue items.
 */
@Entity(
    tableName = "offline_queue",
    indices = [
        Index(value = ["isSynced"]),
        Index(value = ["createdAt"])
    ]
)
data class OfflineQueueEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "voucherCode")
    val voucherCode: String,

    @ColumnInfo(name = "fromBankId")
    val fromBankId: String,

    @ColumnInfo(name = "recipient")
    val recipient: String,

    @ColumnInfo(name = "amount")
    val amount: Double,

    @ColumnInfo(name = "signedPayload")
    val signedPayload: String,

    @ColumnInfo(name = "channel")
    val channel: String, // "SMS_ENCRYPTED", "QR_CODE"

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "isSynced")
    val isSynced: Boolean = false
)
