package com.sysadmindoc.nimbus.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sysadmindoc.nimbus.data.model.ReportCondition
import com.sysadmindoc.nimbus.testing.setContentWithAccessibilityChecks
import com.sysadmindoc.nimbus.ui.theme.NimbusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ReportSubmitSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun reportSheet_requiresConditionBeforeSubmit() {
        var submittedCondition: ReportCondition? = null

        composeTestRule.setContentWithAccessibilityChecks {
            NimbusTheme {
                ReportSubmitSheet(
                    isSubmitting = false,
                    submitResult = null,
                    onSubmit = { condition, _ -> submittedCondition = condition },
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Report conditions").assertIsDisplayed()
        composeTestRule.onNodeWithText("Choose a condition").assertIsNotEnabled()
        assertNull(submittedCondition)
    }

    @Test
    fun reportSheet_selectsConditionAndSubmits() {
        var submittedCondition: ReportCondition? = null
        var submittedNote: String? = null

        composeTestRule.setContentWithAccessibilityChecks {
            NimbusTheme {
                ReportSubmitSheet(
                    isSubmitting = false,
                    submitResult = null,
                    onSubmit = { condition, note ->
                        submittedCondition = condition
                        submittedNote = note
                    },
                    onDismiss = {},
                )
            }
        }

        composeTestRule
            .onNode(hasContentDescription("Rain condition"))
            .performClick()

        composeTestRule
            .onNode(hasContentDescription("Rain condition"))
            .assertIsSelected()

        composeTestRule.onNodeWithText("Submit report").assertIsEnabled().performClick()

        assertEquals(ReportCondition.RAIN, submittedCondition)
        assertEquals("", submittedNote)
    }
}
