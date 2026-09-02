package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ExpenseCategory
import com.example.data.model.MemberSplit
import com.example.data.model.SplitJsonConverter
import com.example.data.model.SplitType
import com.example.data.repository.ExportHelper
import com.example.voice.ExpenseVoiceParser
import org.junit.Assert.assertEquals
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
    assertEquals("GroupSpend", appName)
  }

  @Test
  fun `voice parser detects amount and category`() {
    val members = listOf("You", "Alex", "Sam")
    val parsed = ExpenseVoiceParser.parse("Dinner at Mario's 64 dollars split equally", members)
    assertEquals(64.0, parsed.amount, 0.01)
    assertEquals(ExpenseCategory.FOOD, parsed.category)
    assertEquals(SplitType.EQUAL, parsed.splitType)
  }

  @Test
  fun `voice parser detects percentages and payer`() {
    val members = listOf("You", "Alex", "Sam")
    val parsed = ExpenseVoiceParser.parse("Groceries 95 paid by Alex split 60% Alex 40% You", members)
    assertEquals(95.0, parsed.amount, 0.01)
    assertEquals(ExpenseCategory.GROCERIES, parsed.category)
    assertEquals(SplitType.PERCENTAGE, parsed.splitType)
    assertEquals("Alex", parsed.payerName)
  }

  @Test
  fun `split json converter serializes and deserializes correctly`() {
    val splits = listOf(
      MemberSplit(memberId = 1L, memberName = "Alex", shareValue = 50.0, computedAmount = 25.0),
      MemberSplit(memberId = 2L, memberName = "Sam", shareValue = 50.0, computedAmount = 25.0)
    )
    val json = SplitJsonConverter.toJson(splits)
    val reconstructed = SplitJsonConverter.fromJson(json)

    assertEquals(2, reconstructed.size)
    assertEquals("Alex", reconstructed[0].memberName)
    assertEquals(25.0, reconstructed[0].computedAmount, 0.01)
    assertEquals("Sam", reconstructed[1].memberName)
    assertEquals(25.0, reconstructed[1].computedAmount, 0.01)
  }
}
