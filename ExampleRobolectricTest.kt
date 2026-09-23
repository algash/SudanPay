package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.security.OfflineSecurityUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SudanPay", appName)
  }

  @Test
  fun `verify offline payload generation`() {
    val payload = OfflineSecurityUtil.generateOfflinePayload(
      fromBankId = "bankak",
      fromAccount = "249810294812",
      recipient = "0912345678",
      amount = 25000.0
    )
    assertNotNull(payload)
    assertTrue(payload.encryptedSmsText.startsWith("SPAY#TRF#V1#"))
    assertTrue(payload.voucherId.startsWith("SPAY-"))
    assertEquals(12, payload.hmacSignature.length)
  }
}
