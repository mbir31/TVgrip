package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented smoke test for the home screen. These tests require a connected
 * Android emulator/device and are compiled in CI via assembleDebugAndroidTest.
 */
@RunWith(AndroidJUnit4::class)
class AppHomeSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreenShowsBrandAndPairEntryPoint() {
        composeRule.onNodeWithText("TVGrip").assertIsDisplayed()
        composeRule.onNodeWithText("No TV Connected").assertIsDisplayed()
        composeRule.onNodeWithText("PAIR A TV").assertIsDisplayed()
    }

    @Test
    fun footerCreditIsAlwaysPresent() {
        composeRule.onNodeWithText("Made with love by ©munabbiRMushran🇧🇩").assertIsDisplayed()
    }
}
