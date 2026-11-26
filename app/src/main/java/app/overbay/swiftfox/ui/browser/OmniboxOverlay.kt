package app.overbay.swiftfox.ui.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import app.overbay.swiftfox.ui.icons.rounded.Bolt
import app.overbay.swiftfox.ui.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import app.overbay.swiftfox.ui.icons.rounded.History
import app.overbay.swiftfox.ui.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import app.overbay.swiftfox.ui.icons.rounded.Tab
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.overbay.swiftfox.data.prefs.SearchEngineId
import app.overbay.swiftfox.features.omnibox.InlineUrlAutocomplete
import app.overbay.swiftfox.features.omnibox.OmniboxAutocompleteGate
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import app.overbay.swiftfox.features.omnibox.OmniboxSearchScope
import app.overbay.swiftfox.features.omnibox.OmniboxSuggestion
import app.overbay.swiftfox.features.omnibox.SuggestionKind
import app.overbay.swiftfox.ui.components.SiteIconBadge
import app.overbay.swiftfox.ui.theme.LocalSwiftfoxColors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.yield
import kotlin.math.roundToInt

enum class OmniboxAnchor {
    NONE,
    START_PAGE,
    TOP_BAR,
    BOTTOM_BAR,
    SIDE_RAIL_BADGE,
    SIDE_RAIL_BUTTON
}

/**
 * Holds the latest attached [LayoutCoordinates] for a search-bar anchor so callers can query
 * exact, up-to-date root bounds on demand at tap time without calculating rects on every scroll frame.
 */
internal class BoundsHolder(
    var coordinates: LayoutCoordinates? = null
) {
    fun currentBoundsInRoot(): Rect? {
        val coords = coordinates?.takeIf { it.isAttached } ?: return null
        val rect = coords.boundsInRoot()
        return rect.takeIf { it.width > 0f && it.height > 0f }
    }
}

private class RootOriginHolder {
    var origin by mutableStateOf(Offset.Zero)
}

@Composable
fun OmniboxOverlay(
    isVisible: Boolean = true,
    expandProgressProvider: () -> Float = { 1f },
    sourceRect: Rect? = null,
    isFadeOnlyExit: Boolean = false,
    query: String,
    defaultSearchEngine: SearchEngineId,
    customSearchUrlTemplate: String = "",
    activeSearchScope: OmniboxSearchScope? = null,
    isPrivate: Boolean = false,
    suggestions: List<OmniboxSuggestion>,
    inlineAutocomplete: InlineUrlAutocomplete? = null,
    onQueryChange: (String) -> Unit,
    onSelectSearchScope: (OmniboxSearchScope?) -> Unit = {},
    onOpenSearchSettings: () -> Unit = {},
    onSubmit: (String) -> Unit,
    onSelectSuggestion: (OmniboxSuggestion) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val foxColors = LocalSwiftfoxColors.current
    val quickBangs = remember { OmniboxEngine.bangs.take(7) }
    val quickTlds = remember { OmniboxEngine.quickTldShortcuts }
    val overlayOrigin = remember { RootOriginHolder() }

    // TextFieldState owns the IME buffer synchronously so fast typing bursts never race with
    // parent recompositions or drop characters; select-all on initial open or rapid reopen.
    val textFieldState = rememberTextFieldState(
        initialText = query,
        initialSelection = if (query.isNotEmpty()) TextRange(0, query.length) else TextRange.Zero
    )
    val currentText = textFieldState.text.toString()
    val activeBang = remember(currentText, activeSearchScope) {
        if (activeSearchScope == null || activeSearchScope is OmniboxSearchScope.Engine) {
            OmniboxEngine.detectBang(currentText)
        } else {
            null
        }
    }
    val isAllSelected = currentText.isNotEmpty() &&
        textFieldState.selection.min == 0 &&
        textFieldState.selection.max == currentText.length
    val showQuickTldChips = activeSearchScope == null && !isAllSelected && remember(currentText) {
        OmniboxEngine.shouldShowQuickTldChips(currentText)
    }
    var lastSyncedExternalQuery by remember { mutableStateOf(query) }
    var clearPrefilledUrlOnScopeSelect by remember { mutableStateOf(false) }
    var focusRequestNonce by remember { mutableIntStateOf(0) }
    val autocompleteGate = remember { OmniboxAutocompleteGate(initialSuppressed = query.isNotEmpty()) }
    var gateVersion by remember { mutableIntStateOf(0) }

    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .distinctUntilChanged()
            .collect { typed ->
                if (typed != lastSyncedExternalQuery) {
                    val prev = lastSyncedExternalQuery
                    autocompleteGate.onUserTextCommitted(prev, typed)
                    gateVersion++
                    lastSyncedExternalQuery = typed
                    onQueryChange(typed)
                }
            }
    }

    LaunchedEffect(query) {
        if (query != lastSyncedExternalQuery) {
            lastSyncedExternalQuery = query
            autocompleteGate.onExternalQuerySynced(query)
            gateVersion++
            if (textFieldState.text.toString() != query) {
                textFieldState.setTextAndPlaceCursorAtEnd(query)
            }
        }
    }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            autocompleteGate.onOverlayOpened(query)
            gateVersion++
            if (query.isNotEmpty()) {
                textFieldState.edit {
                    replace(0, length, query)
                    selection = TextRange(0, query.length)
                }
                lastSyncedExternalQuery = query
            } else if (textFieldState.text.isNotEmpty()) {
                textFieldState.setTextAndPlaceCursorAtEnd("")
                lastSyncedExternalQuery = ""
            }
            // Let frame 0 of the bounds morph commit before triggering IME binder IPC so the opening
            // motion stays 120Hz silky-smooth while the keyboard still rises instantaneously.
            withFrameNanos { }
            focusRequester.requestFocus()
        } else {
            clearPrefilledUrlOnScopeSelect = false
            focusManager.clearFocus(force = true)
        }
    }

    LaunchedEffect(focusRequestNonce) {
        if (focusRequestNonce > 0 && isVisible) {
            yield()
            focusRequester.requestFocus()
        }
    }

    val isCursorAtEnd = textFieldState.selection.collapsed &&
        textFieldState.selection.end == currentText.length
    val isAutocompleteSuppressed = remember(gateVersion) { autocompleteGate.isSuppressed }
    val activeAutocomplete = inlineAutocomplete?.takeIf { candidate ->
        !isAutocompleteSuppressed &&
            isCursorAtEnd &&
            currentText.isNotEmpty() &&
            candidate.typedPrefix.equals(currentText, ignoreCase = true) &&
            candidate.completionSuffix.isNotEmpty()
    }
    val latestHasActiveAutocomplete = rememberUpdatedState(activeAutocomplete != null)
    val backspaceDismissTransformation = remember(autocompleteGate) {
        InputTransformation {
            val collapsedAtEnd = selection.collapsed && selection.end == length
            if (autocompleteGate.interceptBackspaceToDismiss(
                    originalText = originalText,
                    proposedText = asCharSequence(),
                    proposedSelectionCollapsedAtEnd = collapsedAtEnd,
                    hasActiveAutocomplete = latestHasActiveAutocomplete.value
                )
            ) {
                revertAllChanges()
                gateVersion++
            }
        }
    }
    val autocompleteHighlightBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.26f)
    val autocompleteHighlightFg = MaterialTheme.colorScheme.onSurface
    val autocompleteTransformation = remember(
        activeAutocomplete,
        autocompleteHighlightBg,
        autocompleteHighlightFg
    ) {
        val suffix = activeAutocomplete?.completionSuffix
        if (suffix.isNullOrEmpty()) {
            null
        } else {
            OutputTransformation {
                val start = length
                append(suffix)
                addStyle(
                    SpanStyle(
                        background = autocompleteHighlightBg,
                        color = autocompleteHighlightFg
                    ),
                    start = start,
                    end = length
                )
            }
        }
    }

    val focusRingColor = if (isPrivate) foxColors.privateAccent else MaterialTheme.colorScheme.primary
    val pillContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val bodySlideMaxPx = with(density) { 22.dp.toPx() }
    val fallbackSlideMaxPx = with(density) { 16.dp.toPx() }
    val pillCornerPx = with(density) { 28.dp.toPx() }
    val oneDpPx = with(density) { 1.dp.toPx() }

    val statusBarTopPx = WindowInsets.statusBars.getTop(density)
    val leftInsetPx = maxOf(
        WindowInsets.displayCutout.getLeft(density, layoutDirection),
        WindowInsets.navigationBars.getLeft(density, layoutDirection)
    )
    val rightInsetPx = maxOf(
        WindowInsets.displayCutout.getRight(density, layoutDirection),
        WindowInsets.navigationBars.getRight(density, layoutDirection)
    )
    val horizontalMarginPx = with(density) { 16.dp.roundToPx() }
    val topMarginPx = with(density) { 10.dp.roundToPx() }
    val targetPillHeightPx = with(density) { 52.dp.roundToPx() }
    val minPillWidthPx = with(density) { 48.dp.roundToPx() }
    val minPillHeightPx = with(density) { 44.dp.roundToPx() }
    val narrowSourceThresholdPx = with(density) { 160.dp.toPx() }
    val hasValidMorphSource = sourceRect != null && sourceRect.width > 0f && sourceRect.height > 0f && !isFadeOnlyExit
    val isNarrowMorphSource = hasValidMorphSource && sourceRect.width < narrowSourceThresholdPx

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPlaced { coords ->
                if (coords.isAttached) {
                    overlayOrigin.origin = coords.positionInRoot()
                }
            }
            .graphicsLayer {
                if (isFadeOnlyExit) {
                    alpha = expandProgressProvider().coerceIn(0f, 1f)
                }
            }
    ) {
        // Animated full-screen backdrop surface (Draw phase only — zero recomposition)
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (!isFadeOnlyExit) {
                        val p = expandProgressProvider().coerceIn(0f, 1f)
                        alpha = (p * 1.45f).coerceIn(0f, 1f)
                    }
                },
            color = MaterialTheme.colorScheme.background
        ) {}

        // Quick Bangs + Suggestions glide cleanly below the resting pill slot without being re-measured during morph
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(top = 72.dp)
                .graphicsLayer {
                    if (!isFadeOnlyExit) {
                        val p = expandProgressProvider().coerceIn(0f, 1f)
                        val bodyProgress = ((p - 0.14f) / 0.86f).coerceIn(0f, 1f)
                        alpha = bodyProgress
                        translationY = bodySlideMaxPx * (1f - bodyProgress)
                    }
                }
        ) {
            // Active one-time scope reset chip, Quick TLD shortcuts, or Quick Bangs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (activeSearchScope != null) {
                    Surface(
                        onClick = {
                            onSelectSearchScope(null)
                            focusRequestNonce++
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isPrivate) foxColors.privateContainer else MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(
                            1.dp,
                            if (isPrivate) {
                                foxColors.privateAccent.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            }
                        ),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Searching in ${activeSearchScope.displayLabel(customSearchUrlTemplate)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isPrivate) foxColors.onPrivateContainer else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear search engine filter",
                                tint = if (isPrivate) foxColors.privateAccent else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else if (showQuickTldChips) {
                    quickTlds.forEach { shortcut ->
                        val isPrimaryCom = shortcut == ".com"
                        Surface(
                            onClick = {
                                val applied = OmniboxEngine.applyTldShortcut(currentText, shortcut)
                                    ?: return@Surface
                                onSubmit(applied)
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isPrimaryCom) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isPrimaryCom) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                }
                            ),
                            modifier = Modifier
                                .widthIn(min = 52.dp)
                                .heightIn(min = 48.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = shortcut,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isPrimaryCom) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                } else {
                    quickBangs.forEach { bang ->
                        Surface(
                            onClick = {
                                val bangPrefix = "${bang.triggers.first()} "
                                autocompleteGate.onExternalQuerySynced(bangPrefix)
                                gateVersion++
                                textFieldState.setTextAndPlaceCursorAtEnd(bangPrefix)
                                lastSyncedExternalQuery = bangPrefix
                                onQueryChange(bangPrefix)
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = bang.triggers.first(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = bang.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Ranked suggestions with web favicons (or helpful empty state for local scope filters)
            if (activeSearchScope != null && activeSearchScope.isLocalFilter && suggestions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        text = if (currentText.isBlank()) {
                            "No ${activeSearchScope.shortLabel.lowercase()} to show"
                        } else {
                            "No matching ${activeSearchScope.shortLabel.lowercase()} found"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(
                        items = suggestions,
                        key = { it.id },
                        contentType = { it.kind }
                    ) { suggestion ->
                        SuggestionRow(
                            suggestion = suggestion,
                            onClick = { onSelectSuggestion(suggestion) }
                        )
                    }
                }
            }
        }

        // Focused omnibox pill morphing via true Layout-phase bounds interpolation (zero scaleX/scaleY distortion,
        // crisp constant 28dp rounded corners, exact frame-0 alignment, and zero recomposition per frame)
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = pillContainerColor,
            modifier = Modifier
                .layout { measurable, constraints ->
                    val p = if (isFadeOnlyExit) 1f else expandProgressProvider().coerceIn(0f, 1f)
                    val dstLeft = (leftInsetPx + horizontalMarginPx).toFloat()
                    val dstTop = (statusBarTopPx + topMarginPx).toFloat()
                    val dstWidth = (constraints.maxWidth - leftInsetPx - rightInsetPx - horizontalMarginPx * 2)
                        .coerceAtLeast(minPillWidthPx)
                        .toFloat()
                    val dstHeight = targetPillHeightPx.toFloat()

                    val src = sourceRect
                    val curLeft: Float
                    val curTop: Float
                    val curWidth: Int
                    val curHeight: Int
                    if (src != null && src.width > 0f && src.height > 0f && !isFadeOnlyExit) {
                        val origin = overlayOrigin.origin
                        val localLeft = src.left - origin.x
                        val localTop = src.top - origin.y
                        curLeft = localLeft + (dstLeft - localLeft) * p
                        curTop = localTop + (dstTop - localTop) * p
                        curWidth = (src.width + (dstWidth - src.width) * p)
                            .roundToInt()
                            .coerceIn(minPillWidthPx, constraints.maxWidth.coerceAtLeast(minPillWidthPx))
                        curHeight = (src.height + (dstHeight - src.height) * p)
                            .roundToInt()
                            .coerceIn(minPillHeightPx, constraints.maxHeight.coerceAtLeast(minPillHeightPx))
                    } else {
                        curLeft = dstLeft
                        curTop = dstTop - if (isFadeOnlyExit) 0f else fallbackSlideMaxPx * (1f - p)
                        curWidth = dstWidth.roundToInt().coerceIn(minPillWidthPx, constraints.maxWidth.coerceAtLeast(minPillWidthPx))
                        curHeight = dstHeight.roundToInt().coerceIn(minPillHeightPx, constraints.maxHeight.coerceAtLeast(minPillHeightPx))
                    }

                    val placeable = measurable.measure(Constraints.fixed(curWidth, curHeight))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        // Use physical screen coordinates (place, not placeRelative) to match boundsInRoot() in LTR & RTL
                        placeable.place(curLeft.roundToInt(), curTop.roundToInt())
                    }
                }
                .graphicsLayer {
                    if (!hasValidMorphSource && !isFadeOnlyExit) {
                        val p = expandProgressProvider().coerceIn(0f, 1f)
                        alpha = (p * 1.45f).coerceIn(0f, 1f)
                    }
                }
                .drawWithContent {
                    drawContent()
                    val p = if (isFadeOnlyExit) 1f else expandProgressProvider().coerceIn(0f, 1f)
                    val strokePx = oneDpPx * (1f + p)
                    val half = strokePx / 2f
                    val maxRadius = (minOf(size.width, size.height) * 0.5f).coerceAtLeast(0f)
                    val radius = pillCornerPx.coerceAtMost(maxRadius)
                    drawRoundRect(
                        color = focusRingColor.copy(alpha = 0.42f + 0.58f * p),
                        topLeft = Offset(half, half),
                        size = Size(
                            width = (size.width - strokePx).coerceAtLeast(0f),
                            height = (size.height - strokePx).coerceAtLeast(0f)
                        ),
                        cornerRadius = CornerRadius(radius, radius),
                        style = Stroke(width = strokePx)
                    )
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                    .graphicsLayer {
                        if (hasValidMorphSource && !isFadeOnlyExit) {
                            val p = expandProgressProvider().coerceIn(0f, 1f)
                            alpha = if (isNarrowMorphSource) {
                                ((p - 0.18f) / 0.52f).coerceIn(0f, 1f)
                            } else {
                                (0.45f + 0.55f * p).coerceIn(0f, 1f)
                            }
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (activeBang != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        Text(
                            text = activeBang.bang.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    SearchScopeSelectorAnchor(
                        defaultEngine = defaultSearchEngine,
                        customSearchUrlTemplate = customSearchUrlTemplate,
                        activeScope = activeSearchScope,
                        isPrivate = isPrivate,
                        compact = true,
                        onMenuOpened = {
                            clearPrefilledUrlOnScopeSelect =
                                isAllSelected && OmniboxEngine.looksLikeUrl(currentText)
                        },
                        onSelectScope = { scope ->
                            val shouldClear = clearPrefilledUrlOnScopeSelect ||
                                (isAllSelected && OmniboxEngine.looksLikeUrl(currentText))
                            clearPrefilledUrlOnScopeSelect = false
                            if (shouldClear) {
                                autocompleteGate.onCleared()
                                gateVersion++
                                textFieldState.setTextAndPlaceCursorAtEnd("")
                                lastSyncedExternalQuery = ""
                                onQueryChange("")
                            }
                            onSelectSearchScope(scope)
                            focusRequestNonce++
                        },
                        onOpenSearchSettings = onOpenSearchSettings
                    )
                }

                BasicTextField(
                    state = textFieldState,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    inputTransformation = backspaceDismissTransformation,
                    outputTransformation = autocompleteTransformation,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(if (isPrivate) foxColors.privateAccent else MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (activeSearchScope != null) KeyboardType.Text else KeyboardType.Uri,
                        imeAction = if (activeSearchScope != null) ImeAction.Search else ImeAction.Go
                    ),
                    onKeyboardAction = {
                        val completion = activeAutocomplete
                        val text = textFieldState.text.toString()
                        if (activeSearchScope?.isLocalFilter == true) {
                            onSubmit(text)
                        } else if (completion != null) {
                            onSubmit(completion.targetUrl)
                        } else if (text.isNotBlank()) {
                            onSubmit(text)
                        }
                    },
                    decorator = { inner ->
                        if (currentText.isEmpty()) {
                            val placeholderText = activeSearchScope?.placeholder(customSearchUrlTemplate)
                                ?: "Search ${defaultSearchEngine.displayName(customSearchUrlTemplate)} or enter address"
                            Text(
                                text = placeholderText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        inner()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                )

                if (currentText.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            autocompleteGate.onCleared()
                            gateVersion++
                            textFieldState.setTextAndPlaceCursorAtEnd("")
                            lastSyncedExternalQuery = ""
                            onQueryChange("")
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear text",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: OmniboxSuggestion,
    onClick: () -> Unit
) {
    val fallbackIcon = when (suggestion.kind) {
        SuggestionKind.OPEN_TAB -> Icons.Rounded.Tab
        SuggestionKind.BOOKMARK -> Icons.Rounded.Bookmark
        SuggestionKind.HISTORY -> Icons.Rounded.History
        SuggestionKind.BANG -> Icons.Rounded.Bolt
        SuggestionKind.DIRECT_URL -> Icons.Rounded.Public
        SuggestionKind.SEARCH -> Icons.Rounded.Search
    }
    val useSiteFavicon = suggestion.kind == SuggestionKind.OPEN_TAB ||
        suggestion.kind == SuggestionKind.BOOKMARK ||
        suggestion.kind == SuggestionKind.HISTORY

    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (useSiteFavicon) {
                SiteIconBadge(
                    url = suggestion.targetUrl,
                    fallbackLabel = suggestion.title,
                    size = 36.dp,
                    iconSize = 20.dp,
                    shape = RoundedCornerShape(12.dp),
                    containerColor = if (suggestion.kind == SuggestionKind.OPEN_TAB) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    tint = if (suggestion.kind == SuggestionKind.OPEN_TAB) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fallbackVector = fallbackIcon,
                    fillImageToContainer = true,
                    probeDistinctSubpath = suggestion.kind != SuggestionKind.HISTORY
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (suggestion.kind == SuggestionKind.BANG) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = fallbackIcon,
                        contentDescription = null,
                        tint = if (suggestion.kind == SuggestionKind.BANG) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = suggestion.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = suggestion.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (suggestion.kind == SuggestionKind.OPEN_TAB) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "Switch",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}
