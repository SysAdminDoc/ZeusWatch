package com.sysadmindoc.nimbus.testing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Proves the accessibility helpers can fail.
 *
 * They replaced an ATF integration that reported nothing under Robolectric,
 * and the way that went unnoticed for so long is that every screen using it
 * passed. A gate nobody has watched fail is not a gate, so each check here is
 * pointed at a tree that violates it and asserted to reject it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class AccessibilityHelperSelfTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun expectFailure(content: @Composable () -> Unit): String {
        val error = runCatching { composeTestRule.setContentWithAccessibilityChecks(content) }
            .exceptionOrNull()
        assertTrue("expected the accessibility checks to reject this tree", error is AssertionError)
        return error!!.message.orEmpty()
    }

    @Test
    fun anUnlabelledClickableIsRejected() {
        val message = expectFailure {
            Surface {
                Box(Modifier.size(48.dp).clickable {})
            }
        }

        assertTrue(message, message.contains("no label for a screen reader"))
    }

    @Test
    fun anIconButtonWithNoContentDescriptionIsRejected() {
        // The realistic version: an icon-only button is invisible to TalkBack
        // without a description, and nothing about the code looks wrong.
        val message = expectFailure {
            Surface {
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                }
            }
        }

        assertTrue(message, message.contains("no label for a screen reader"))
    }

    @Test
    fun textThatBarelyDiffersFromItsBackgroundIsRejected() {
        val message = expectFailure {
            Column(Modifier.fillMaxSize().background(Color.White)) {
                Text("almost invisible", color = Color(0xFFFAFAFA))
            }
        }

        assertTrue(message, message.contains("contrast"))
    }

    @Test
    fun textOnAnUnexpectedSurfaceIsRejected() {
        // The mistake this catches in a real screen: a secondary text token
        // that reads well on the app background, placed on a light card.
        val message = expectFailure {
            Column(Modifier.fillMaxSize().background(Color(0xFFEEEEEE))) {
                Text("secondary on the wrong card", color = Color(0xFFBBBBBB))
            }
        }

        assertTrue(message, message.contains("contrast"))
    }

    @Test
    fun aCorrectlyBuiltTreePasses() {
        // The other half of the proof: the checks must not reject a screen
        // that is actually fine, or they would just be turned off again.
        composeTestRule.setContentWithAccessibilityChecks {
            Column(Modifier.fillMaxSize().background(Color.White)) {
                Text("clearly readable", color = Color.Black)
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
                Box(
                    Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "Described box" }
                        .clickable {},
                )
            }
        }
    }

    @Test
    fun aClickableLabelledOnlyByItsChildTextPasses() {
        // Merged semantics: this is how most Compose buttons are written, and
        // rejecting it would make the check unusable.
        composeTestRule.setContentWithAccessibilityChecks {
            Column(Modifier.fillMaxSize().background(Color.White)) {
                Box(Modifier.size(48.dp).clickable {}) {
                    Text("Retry", color = Color.Black)
                }
            }
        }
    }

    @Composable
    private fun Surface(content: @Composable () -> Unit) {
        Column(Modifier.fillMaxSize().background(Color.White)) { content() }
    }
}
