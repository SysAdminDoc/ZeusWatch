package com.sysadmindoc.nimbus.testing

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import org.junit.Assert.fail
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Renders [content] and runs the accessibility checks that work on the JVM.
 *
 * The Accessibility Test Framework is not one of them. `enableAccessibilityChecks`
 * logs "Accessibility checks are currently not supported by Robolectric" and
 * installs a validator that walks an AccessibilityNodeInfo tree Robolectric
 * never populates, so it reported nothing: a tree with an unlabelled 10dp
 * clickable and 1.02:1 text contrast passed it. The three checks below are
 * measured from the semantics tree and the rendered pixels instead, both of
 * which Robolectric does produce, and each one is proven to fail on a planted
 * violation in AccessibilityHelperSelfTest.
 */
fun ComposeContentTestRule.setContentWithAccessibilityChecks(
    content: @Composable () -> Unit,
) {
    setContent(content)
    waitForIdle()

    assertClickablesAreLabelled()
    assertTextContrastMeetsMinimum()
}

/**
 * Fails when a clickable node carries no name a screen reader can announce.
 *
 * Read from the merged tree, so a button whose only label is its child Text
 * counts as labelled, which is how Compose is normally written.
 */
fun ComposeContentTestRule.assertClickablesAreLabelled() {
    waitForIdle()
    val failures = onAllNodes(hasClickAction(), useUnmergedTree = false)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .filter { node -> node.isVisible() && node.announcedLabel().isNullOrBlank() }
        .map { node -> "node#${node.id} at ${node.boundsInRoot}" }

    if (failures.isNotEmpty()) {
        fail("Clickable nodes with no label for a screen reader: ${failures.joinToString()}")
    }
}

/**
 * Fails when rendered text does not stand out from what is behind it.
 *
 * Measured from the captured pixels of each text node's own bounds: the most
 * common colour in the region is the background, and the colour furthest from
 * it in luminance is the glyph. Anti-aliasing puts intermediate shades in
 * between, so the extreme is the honest reading of the text colour rather than
 * a frequency-weighted average, which would understate the contrast of thin
 * glyphs and fail correct screens.
 *
 * [minimumRatio] defaults to WCAG's large-text threshold rather than 4.5:1
 * because the semantics tree does not carry glyph size, so the strict ratio
 * cannot be applied only where it belongs. Token pairs are held to the full
 * 4.5:1 in AccessibilityContrastTest; this catches the composition-level
 * mistakes that test cannot see, such as text landing on a surface nobody
 * paired it with.
 */
fun ComposeContentTestRule.assertTextContrastMeetsMinimum(
    minimumRatio: Double = 3.0,
): ContrastCoverage {
    waitForIdle()
    val matcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)
    val textNodes = onAllNodes(matcher, useUnmergedTree = true)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
    if (textNodes.isEmpty()) return ContrastCoverage()

    val failures = mutableListOf<String>()
    // Counts only what is actually on screen: a text node laid out at zero
    // size is not text anybody could read, so letting it dilute the coverage
    // figure would make a complete audit look incomplete.
    var considered = 0
    var measured = 0
    val skippedScrolling = mutableListOf<String>()
    val skippedNotCaptured = mutableListOf<String>()
    val skippedNothingDrawn = mutableListOf<String>()

    textNodes.forEachIndexed { index, node ->
        if (!node.isVisible()) return@forEachIndexed
        considered++
        // Inside a horizontally scrolling container the glyphs are painted at
        // a translation the semantics bounds do not carry: on the hourly card
        // the pixels for "70" sit about 24px below the node's own bounds, so
        // the sample reads the card background against itself. Verified with
        // an independent read of the captured frame. Those nodes cannot be
        // measured this way, and guessing at them would fail correct screens.
        if (node.hasHorizontallyScrollableAncestor()) {
            skippedScrolling += node.describe()
            return@forEachIndexed
        }
        // Each node is captured on its own rather than cropped out of a
        // capture of the root. The root bitmap and the semantics coordinate
        // space do not share an origin once window insets are involved: on the
        // main screen every crop landed about 22px above the glyphs, which
        // measured the card background against itself and reported readable
        // white text as a 1.4:1 failure.
        // A node below the fold of a scrolling list is laid out but never
        // rasterised, and capturing it throws. That is not a pass: it is a
        // node nobody looked at, so it is counted and reported rather than
        // dropped. The whole Daily Forecast card went through here unseen.
        val pixels = runCatching {
            onAllNodes(matcher, useUnmergedTree = true)[index].captureToImage().toPixelMap()
        }.getOrNull()
        if (pixels == null) {
            skippedNotCaptured += node.describe()
            return@forEachIndexed
        }

        val counts = mutableMapOf<Long, Int>()
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val pixel = pixels[x, y]
                // Undrawn area captures as fully transparent, and reading it
                // as black invents contrast against a colour nobody painted.
                if (pixel.alpha < 1f) continue
                val key = pixel.value.toLong()
                counts[key] = (counts[key] ?: 0) + 1
            }
        }
        // A single colour means nothing was drawn in the region: the node is
        // clipped, fully transparent, or its glyphs landed elsewhere. There is
        // no contrast to measure, and inventing a 1:1 reading would fail
        // correct screens. It also does not count against coverage: text at
        // alpha 0 mid fade-in is not text anyone can read, and letting it
        // reduce the fraction made an ordinary animation fail the floor.
        if (counts.size < 2) {
            skippedNothingDrawn += node.describe()
            considered--
            return@forEachIndexed
        }

        val background = Color(counts.maxByOrNull { it.value }!!.key.toULong())
        val backgroundLuminance = background.relativeLuminance()
        // Low, and capped. Too high and the real glyph colour is discarded:
        // at a flat two pixels a small label reported its antialias fringe as
        // the text and failed at 1.74:1, and at one in four hundred a counter
        // inside a large padded box lost its glyphs entirely and measured the
        // background against itself. The floor only exists to reject a single
        // stray pixel bleeding in from a neighbour, and an intermediate fringe
        // never wins anyway, because the furthest colour from the background
        // is the glyph itself.
        val sampled = counts.values.sum()
        val minimumPixels = minOf(3, maxOf(1, sampled / 400))
        val foreground = counts.entries
            .filter { it.value >= minimumPixels }
            .map { Color(it.key.toULong()) }
            .maxByOrNull { kotlin.math.abs(it.relativeLuminance() - backgroundLuminance) }
            ?: run {
                skippedNothingDrawn += node.describe()
                return@forEachIndexed
            }

        measured++

        val ratio = contrastRatio(foreground.relativeLuminance(), backgroundLuminance)
        if (ratio < minimumRatio) {
            // The histogram goes in the message because the first question on
            // any failure is whether the reading is real or the region caught
            // something other than the glyphs.
            val histogram = counts.entries.sortedByDescending { it.value }.take(4)
                .joinToString { "${Color(it.key.toULong()).hex()} x${it.value}" }
            failures.add(
                "${node.announcedLabel() ?: "node#${node.id}"} " +
                    "${"%.2f".format(ratio)}:1 (text ${foreground.hex()} on ${background.hex()}) " +
                    "[$histogram]",
            )
        }
    }

    if (failures.isNotEmpty()) {
        fail(
            "Text below ${"%.1f".format(minimumRatio)}:1 contrast against its own background: " +
                failures.joinToString(),
        )
    }

    return ContrastCoverage(
        total = considered,
        measured = measured,
        skippedScrolling = skippedScrolling,
        skippedNotCaptured = skippedNotCaptured,
        skippedNothingDrawn = skippedNothingDrawn,
    )
}

/**
 * How much of a screen's text the contrast check actually looked at.
 *
 * Returned rather than swallowed because the first version dropped every
 * unmeasurable node in silence, and a screen where 37 of 100 nodes could not be
 * captured passed exactly like a screen with no problems. A caller that wants
 * the gate to mean something asserts on this.
 */
data class ContrastCoverage(
    val total: Int = 0,
    val measured: Int = 0,
    val skippedScrolling: List<String> = emptyList(),
    val skippedNotCaptured: List<String> = emptyList(),
    val skippedNothingDrawn: List<String> = emptyList(),
) {
    val skipped: Int get() = total - measured

    override fun toString(): String =
        "measured $measured of $total" +
            " (scrolling ${skippedScrolling.size}," +
            " not captured ${skippedNotCaptured.size}," +
            " nothing drawn ${skippedNothingDrawn.size})"
}

/**
 * Fails when the contrast check could not look at [minimumFraction] of the
 * text on screen.
 *
 * The point is that a fixture cannot quietly stop covering anything: if a card
 * moves below the fold and its text stops being rasterised, this says so
 * instead of continuing to pass.
 */
fun ContrastCoverage.assertMeasuredAtLeast(minimumFraction: Double) {
    if (total == 0) {
        // An empty tree used to satisfy every floor, so an audit of a card
        // that had silently stopped rendering passed all of its assertions.
        if (minimumFraction > 0.0) {
            fail("Contrast check found no visible text at all; there is nothing to audit.")
        }
        return
    }
    val fraction = measured.toDouble() / total
    if (fraction < minimumFraction) {
        fail(
            "Contrast check only $this. Unmeasured: " +
                (skippedNotCaptured + skippedNothingDrawn + skippedScrolling)
                    .take(12).joinToString(),
        )
    }
}

/**
 * Fails when a control is drawn smaller than a finger.
 *
 * Measured from the node's drawn size, deliberately. `touchBoundsInRoot` looks
 * like the more correct property and is useless here: Compose expands the
 * touch bounds of every clickable to the minimum, so it reports 48dp for a
 * 10dp button and the check passes everything. That expansion is also only a
 * best effort, since neighbouring controls cannot all claim the same space, so
 * a control that draws at 10dp is still a control nobody can hit reliably.
 */
/**
 * Fails when the tree exposes no merged description a screen reader can read
 * out as a whole.
 *
 * A card is not clickable, so the labelling check above says nothing about it:
 * removing the merged contentDescription from all three of the cards audited
 * by NX-71 left every test green. A card whose parts are readable one node at
 * a time but which announces nothing as a unit is the usual failure.
 */
fun ComposeContentTestRule.assertHasMergedDescription() {
    waitForIdle()
    val described = onAllNodes(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription),
        useUnmergedTree = false,
    ).fetchSemanticsNodes(atLeastOneRootRequired = false)
        .any { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)
                ?.any { it.isNotBlank() } == true
        }

    if (!described) {
        fail("Nothing in this tree carries a content description for a screen reader.")
    }
}

fun ComposeContentTestRule.assertVisibleTouchTargetsMeetMinimum(
    minSize: Dp = 48.dp,
) {
    waitForIdle()
    val minPx = with(density) { minSize.toPx() }
    val failures = onAllNodes(hasClickAction(), useUnmergedTree = false)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .filter { node ->
            val size = node.size
            size.width > 0 && size.height > 0 &&
                (size.width < minPx || size.height < minPx)
        }
        .map { node ->
            val size = node.size
            "${node.accessibilityLabel()} ${size.width}x${size.height}px"
        }

    if (failures.isNotEmpty()) {
        fail("Clickable nodes below ${minSize.value.toInt()}dp touch target: ${failures.joinToString()}")
    }
}

private fun SemanticsNode.isVisible(): Boolean = size.width > 0 && size.height > 0

private fun SemanticsNode.describe(): String = announcedLabel()?.let { "'$it'" } ?: "node#$id"

private fun SemanticsNode.hasHorizontallyScrollableAncestor(): Boolean {
    var current: SemanticsNode? = this
    while (current != null) {
        if (current.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null) {
            return true
        }
        current = current.parent
    }
    return false
}

/**
 * What TalkBack would read out for this node, or null when it would say
 * nothing. Editable fields announce their own content, so a text field with no
 * other label is still reachable.
 */
private fun SemanticsNode.announcedLabel(): String? {
    config.getOrNull(SemanticsProperties.ContentDescription)
        ?.firstOrNull { it.isNotBlank() }
        ?.let { return it }
    config.getOrNull(SemanticsProperties.Text)
        ?.firstOrNull { it.text.isNotBlank() }
        ?.let { return it.text }
    config.getOrNull(SemanticsProperties.EditableText)
        ?.text?.takeIf { it.isNotBlank() }
        ?.let { return it }
    config.getOrNull(SemanticsProperties.StateDescription)
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }
    // A custom onClick label is what TalkBack announces for the action itself.
    config.getOrNull(SemanticsActions.OnClick)?.label
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }
    return null
}

private fun SemanticsNode.accessibilityLabel(): String {
    val text = config.getOrNull(SemanticsProperties.Text)
        ?.joinToString(" ") { it.text }
    if (!text.isNullOrBlank()) return "'$text'"

    val contentDescription = config.getOrNull(SemanticsProperties.ContentDescription)
        ?.joinToString(" ")
    if (!contentDescription.isNullOrBlank()) return "'$contentDescription'"

    return "node#${id}"
}

private fun Color.hex(): String =
    "#%02X%02X%02X".format((red * 255).roundToInt(), (green * 255).roundToInt(), (blue * 255).roundToInt())

private fun Color.relativeLuminance(): Double {
    fun channel(value: Float): Double {
        val v = value.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
}

private fun contrastRatio(first: Double, second: Double): Double {
    val lighter = max(first, second)
    val darker = min(first, second)
    return (lighter + 0.05) / (darker + 0.05)
}
