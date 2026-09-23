package com.example.data.gemini

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ParsedBankSms(
    val bankId: String,
    val bankName: String,
    val type: String, // "DEBIT" or "CREDIT"
    val amount: Double,
    val newBalance: Double?,
    val transactionId: String?,
    val counterparty: String?,
    val confidence: String = "LOCAL_REGEX", // or "GEMINI_AI"
    val rawText: String
)

object GeminiSmsParserService {

    private const val TAG = "GeminiSmsParser"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val SYSTEM_PROMPT = """
        أنت محلل رسائل مصرفية متخصص في البنوك والمحافظ الإلكترونية السودانية (بنكك - بنك الخرطوم، أوكاش - سوداني، فوري - بنك فيصل، بنك الساحل والصحراء، محفظة قريشي).
        مهمتك استخراج البيانات المالية من رسالة الـ SMS الواردة وإرجاع JSON مطابق تماماً للنموذج التالي بدون أي نصوص إضافية:
        {
          "bank_id": "bankak" | "okash" | "faysal" | "sahel" | "other",
          "bank_name": "اسم البنك أو المحفظة",
          "transaction_type": "DEBIT" | "CREDIT",
          "amount": 0.0,
          "currency": "SDG",
          "new_balance": 0.0,
          "transaction_id": "string",
          "counterparty": "رقم هاتف أو حساب الطرف الآخر",
          "confidence_score": 0.95
        }
        قواعد هامة:
        - "خصم" أو "تحويل الى" تعني DEBIT
        - "إيداع" أو "قيد" أو "استلام من" تعني CREDIT
        - حول الأرقام العربية المشرقية (١٢٣) إلى أرقام إنجليزية (123)
        - تخلص من الفواصل في المبالغ مثل 15,000 لتصبح 15000.0
    """.trimIndent()

    suspend fun parseWithGemini(smsText: String, sender: String = ""): ParsedBankSms? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.d(TAG, "No Gemini API key present, returning null for local fallback")
            return@withContext null
        }

        try {
            val rootJson = JSONObject().apply {
                // system_instruction
                put("system_instruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", SYSTEM_PROMPT) })
                    })
                })
                // contents
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "مرسل الرسالة: $sender\nنص الرسالة: $smsText")
                            })
                        })
                    })
                })
                // generationConfig
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.1)
                })
            }

            val requestBody = rootJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL?key=$apiKey")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: return@withContext null

            if (!response.isSuccessful) {
                Log.w(TAG, "Gemini API error ${response.code}: $bodyString")
                return@withContext null
            }

            val respObj = JSONObject(bodyString)
            val candidates = respObj.optJSONArray("candidates") ?: return@withContext null
            if (candidates.length() == 0) return@withContext null

            val firstCand = candidates.getJSONObject(0)
            val content = firstCand.optJSONObject("content") ?: return@withContext null
            val parts = content.optJSONArray("parts") ?: return@withContext null
            if (parts.length() == 0) return@withContext null

            val outputText = parts.getJSONObject(0).optString("text")
            val parsedJson = JSONObject(outputText)

            ParsedBankSms(
                bankId = parsedJson.optString("bank_id", "other"),
                bankName = parsedJson.optString("bank_name", "إشعار بنكي"),
                type = parsedJson.optString("transaction_type", "DEBIT"),
                amount = parsedJson.optDouble("amount", 0.0),
                newBalance = if (parsedJson.has("new_balance") && !parsedJson.isNull("new_balance")) parsedJson.optDouble("new_balance") else null,
                transactionId = parsedJson.optString("transaction_id", null),
                counterparty = parsedJson.optString("counterparty", null),
                confidence = "GEMINI_3.5_FLASH",
                rawText = smsText
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call Gemini API", e)
            null
        }
    }
}
