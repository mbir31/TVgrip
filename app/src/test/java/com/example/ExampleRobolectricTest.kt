package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.PlayerSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("TVGrip", appName)
  }

  @Test
  fun `verify player slots exist`() {
    assertEquals(4, PlayerSlot.entries.size)
    assertEquals("P1", PlayerSlot.PLAYER_1.label)
    assertEquals("P2", PlayerSlot.PLAYER_2.label)
    assertEquals("P3", PlayerSlot.PLAYER_3.label)
    assertEquals("P4", PlayerSlot.PLAYER_4.label)
  }
}
