package app.overbay.swiftfox.ui.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tab
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.swiftfox.features.omnibox.InlineUrlAutocomplete
import app.overbay.swiftfox.features.omnibox.OmniboxAutocompleteGate
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
import app.overbay.swiftfox.features.omnibox.OmniboxSuggestion
import app.overbay.swiftfox.features.omnibox.SuggestionKind
import app.overbay.swiftfox.ui.components.SiteIconBadge
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun OmniboxOverlay(
    isVisible: Boolean = true,
    expandProgressProvider: () -> Float = { 1f },
    sourceRect: Rect? = null,
    query: String,
    searchEngineLabel: String,
    suggestions: List<OmniboxSuggestion>,
    inlineAutocomplete: InlineUrlAutocomplete? = null,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onSelectSuggestion: (OmniboxSuggestion) -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val quickBangs = remember { OmniboxEngine.bangs.take(7) }
    val quickTlds = remember { OmniboxEngine.quickTldShortcuts }
    var targetPillRect by remember { mutableStateOf<Rect?>(null) }

    // TextFieldState owns the IME buffer synchronously so fast typing bursts never race with
    // parent recompositions or drop characters; select-all on initial open or rapid reopen.
    val textFieldState = rememberTextFieldState(
        initialText = query,
        initialSelection = if (query.isNotEmpty()) TextRange(0, query.length) else TextRange.Zero
    )
    val currentText = textFieldState.text.toString()
    val activeBang = remember(currentText) { OmniboxEngine.detectBang(currentText) }
    val isAllSelected = currentText.isNotEmpty() &&
        textFieldState.selection.min == 0 &&
        textFieldState.selection.max == currentText.length
    val showQuickTldChips = !isAllSelected && remember(currentText) {
        OmniboxEngine.shouldShowQuickTldChips(currentText)
    }
    var lastSyncedExternalQuery by remember { mutableStateOf(query) }
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
            focusRequester.requestFocus()
        } else {
            focusManager.clearFocus(force = true)
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

    val primaryColor = MaterialTheme.colorScheme.primary
    val bodySlideMaxPx = with(density) { 26.dp.toPx() }
    val fallbackSlideMaxPx = with(density) { 18.dp.toPx() }
    val pillCornerPx = with(density) { 28.dp.toPx() }
    val oneDpPx = with(density) { 1.dp.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        // Animated full-screen backdrop surface (read progress in graphicsLayer only for 120Hz zero-recomposition)
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = expandProgressProvider().coerceIn(0f, 1f)
                    alpha = (p * 1.35f).coerceIn(0f, 1f)
                },
            color = MaterialTheme.colorScheme.background
        ) {}

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Focused omnibox pill morphing directly from tapped search bar bounds into Fox Amber focus ring
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer {
                            val p = expandProgressProvider().coerceIn(0f, 1f)
                            val backAlpha = ((p - 0.18f) / 0.82f).coerceIn(0f, 1f)
                            alpha = backAlpha
                            translationX = -14f * (1f - backAlpha)
                        }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Close search"
                    )
                }

                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .onGloballyPositioned { coords ->
                            if (coords.isAttached) {
                                val r = coords.boundsInRoot()
                                if (r.width > 0f && r.height > 0f) {
                                    targetPillRect = r
                                }
                            }
                        }
                        .graphicsLayer {
                            val p = expandProgressProvider().coerceIn(0f, 1f)
                            val src = sourceRect
                            val dst = targetPillRect
                            if (src != null && dst != null && dst.width > 0f && dst.height > 0f) {
                                val inv = 1f - p
                                translationX = (src.center.x - dst.center.x) * inv
                                translationY = (src.center.y - dst.center.y) * inv
                                val sx = (src.width / dst.width).coerceIn(0.45f, 1.25f)
                                val sy = (src.height / dst.height).coerceIn(0.75f, 1.35f)
                                scaleX = sx + (1f - sx) * p
                                scaleY = sy + (1f - sy) * p
                            } else {
                                translationY = -fallbackSlideMaxPx * (1f - p)
                                val s = 0.94f + 0.06f * p
                                scaleX = s
                                scaleY = s
                                alpha = (p * 1.4f).coerceIn(0f, 1f)
                            }
                        }
                        .drawWithContent {
                            drawContent()
                            val p = expandProgressProvider().coerceIn(0f, 1f)
                            val strokePx = oneDpPx * (1f + p)
                            val half = strokePx / 2f
                            drawRoundRect(
                                color = primaryColor.copy(alpha = 0.45f + 0.55f * p),
                                topLeft = Offset(half, half),
                                size = Size(
                                    width = (size.width - strokePx).coerceAtLeast(0f),
                                    height = (size.height - strokePx).coerceAtLeast(0f)
                                ),
                                cornerRadius = CornerRadius(pillCornerPx, pillCornerPx),
                                style = Stroke(width = strokePx)
                            )
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (activeBang != null) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = activeBang.bang.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
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
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go
                            ),
                            onKeyboardAction = {
                                val completion = activeAutocomplete
                                val text = textFieldState.text.toString()
                                if (completion != null) {
                                    onSubmit(completion.targetUrl)
                                } else if (text.isNotBlank()) {
                                    onSubmit(text)
                                }
                            },
                            decorator = { inner ->
                                if (currentText.isEmpty()) {
                                    Text(
                                        text = "Search $searchEngineLabel or enter address",
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

            // Quick Bangs + Suggestions glide cleanly into place underneath the expanding search bar
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val p = expandProgressProvider().coerceIn(0f, 1f)
                        val bodyProgress = ((p - 0.14f) / 0.86f).coerceIn(0f, 1f)
                        alpha = bodyProgress
                        translationY = bodySlideMaxPx * (1f - bodyProgress)
                    }
            ) {
                // Quick TLD shortcuts (while typing a single domain-like token) or Quick Bangs
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showQuickTldChips) {
                        items(
                            items = quickTlds,
                            key = { "tld_$it" },
                            contentType = { "tld_shortcut" }
                        ) { shortcut ->
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
                        items(
                            items = quickBangs,
                            key = { it.name },
                            contentType = { "bang_shortcut" }
                        ) { bang ->
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

                // Ranked suggestions with web favicons
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
                    fillImageToContainer = true
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
