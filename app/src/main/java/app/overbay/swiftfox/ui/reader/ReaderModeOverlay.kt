package app.overbay.swiftfox.ui.reader

import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.swiftfox.data.prefs.BrowserSettings
import app.overbay.swiftfox.data.prefs.ReaderFontFamily
import app.overbay.swiftfox.data.prefs.ReaderSurfaceMode
import app.overbay.swiftfox.features.reader.ReaderArticle
import app.overbay.swiftfox.features.reader.ReaderBlock
import app.overbay.swiftfox.features.reader.TextRun
import app.overbay.swiftfox.ui.theme.AmoledOnSurface
import app.overbay.swiftfox.ui.theme.AmoledOnSurfaceVariant
import app.overbay.swiftfox.ui.theme.AmoledOutlineVariant
import app.overbay.swiftfox.ui.theme.AmoledSurface
import app.overbay.swiftfox.ui.theme.AmoledSurfaceContainer
import app.overbay.swiftfox.ui.theme.AmoledSurfaceLow
import app.overbay.swiftfox.ui.theme.DarkOnSurface
import app.overbay.swiftfox.ui.theme.DarkOnSurfaceVariant
import app.overbay.swiftfox.ui.theme.DarkOutlineVariant
import app.overbay.swiftfox.ui.theme.DarkSurface
import app.overbay.swiftfox.ui.theme.DarkSurfaceContainer
import app.overbay.swiftfox.ui.theme.DarkSurfaceLow
import app.overbay.swiftfox.ui.theme.DayOnSurface
import app.overbay.swiftfox.ui.theme.DayOnSurfaceVariant
import app.overbay.swiftfox.ui.theme.DayOutlineVariant
import app.overbay.swiftfox.ui.theme.DaySurface
import app.overbay.swiftfox.ui.theme.DaySurfaceContainer
import app.overbay.swiftfox.ui.theme.DaySurfaceLow
import app.overbay.swiftfox.ui.theme.FoxAmberDarkPrimary
import app.overbay.swiftfox.ui.theme.FoxAmberDayPrimary
import kotlin.math.abs
import kotlin.math.roundToInt

@Immutable
private data class ReaderPalette(
    val background: Color,
    val onBackground: Color,
    val onSurfaceVariant: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val outlineVariant: Color,
    val accent: Color
)

private val SepiaSurface = Color(0xFFF4ECD8)
private val SepiaSurfaceLow = Color(0xFFEDE3CC)
private val SepiaSurfaceContainer = Color(0xFFE6DAC0)
private val SepiaOnSurface = Color(0xFF3B2F20)
private val SepiaOnSurfaceVariant = Color(0xFF6B5942)
private val SepiaOutlineVariant = Color(0xFFCBBBA0)
private val SepiaAccent = Color(0xFF8C4A15)

private val LineSpacingOptions = listOf(
    1.4f to "Compact",
    1.6f to "Balanced",
    1.85f to "Relaxed"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderModeOverlay(
    article: ReaderArticle,
    settings: BrowserSettings,
    onClose: () -> Unit,
    onOpenLink: (String) -> Unit,
    onSurfaceModeChange: (ReaderSurfaceMode) -> Unit,
    onFontFamilyChange: (ReaderFontFamily) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onLineHeightChange: (Float) -> Unit
) {
    var showAppearanceSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val reduceMotion = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }

    val targetPalette = resolveReaderPalette(settings.readerSurfaceMode)
    val animatedBackground by animateColorAsState(
        targetValue = targetPalette.background,
        label = "reader_background"
    )
    val animatedInk by animateColorAsState(
        targetValue = targetPalette.onBackground,
        label = "reader_ink"
    )
    val palette = if (reduceMotion) {
        targetPalette
    } else {
        targetPalette.copy(background = animatedBackground, onBackground = animatedInk)
    }

    val fontFamily = remember(settings.readerFontFamily) {
        when (settings.readerFontFamily) {
            ReaderFontFamily.SANS -> FontFamily.SansSerif
            ReaderFontFamily.SERIF -> FontFamily.Serif
            ReaderFontFamily.MONO -> FontFamily.Monospace
        }
    }

    val fontSizeSp = settings.readerFontSizeSp.coerceIn(14, 28)
    val lineHeightMultiplier = settings.readerLineHeight.coerceIn(1.3f, 2.0f)
    val sourceLabel = remember(article.siteName, article.url) {
        article.siteName?.trim()?.takeIf { it.isNotEmpty() }
            ?: extractHostLabel(article.url)
            ?: "Reader"
    }

    Scaffold(
        containerColor = palette.background,
        contentColor = palette.onBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "$sourceLabel · ${article.readingTimeMinutes} min read",
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Exit reader mode"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAppearanceSheet = true },
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = "Typography and appearance settings" }
                    ) {
                        Text(
                            text = "Aa",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.background,
                    navigationIconContentColor = palette.onBackground,
                    titleContentColor = palette.onSurfaceVariant,
                    actionIconContentColor = palette.onBackground
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            SelectionContainer(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .fillMaxHeight()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item(key = "reader_article_header", contentType = "article_header") {
                        ReaderArticleHeader(
                            article = article,
                            sourceLabel = sourceLabel,
                            fontFamily = fontFamily,
                            baseFontSizeSp = fontSizeSp,
                            palette = palette
                        )
                    }

                    items(
                        items = article.blocks,
                        key = { it.id },
                        contentType = { blockContentType(it) }
                    ) { block ->
                        ReaderBlockItem(
                            block = block,
                            fontFamily = fontFamily,
                            fontSizeSp = fontSizeSp,
                            lineHeightMultiplier = lineHeightMultiplier,
                            palette = palette,
                            onOpenLink = onOpenLink
                        )
                    }

                    item(key = "reader_footer_spacer", contentType = "spacer") {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    if (showAppearanceSheet) {
        ReaderAppearanceSheet(
            settings = settings,
            onDismiss = { showAppearanceSheet = false },
            onSurfaceModeChange = onSurfaceModeChange,
            onFontFamilyChange = onFontFamilyChange,
            onFontSizeChange = onFontSizeChange,
            onLineHeightChange = onLineHeightChange
        )
    }
}

@Composable
private fun ReaderArticleHeader(
    article: ReaderArticle,
    sourceLabel: String,
    fontFamily: FontFamily,
    baseFontSizeSp: Int,
    palette: ReaderPalette
) {
    val titleSizeSp = (baseFontSizeSp + 9).coerceIn(23, 36)
    val titleLineHeightSp = (titleSizeSp * 1.24f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = sourceLabel,
            style = MaterialTheme.typography.labelLarge,
            color = palette.accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = article.title,
            fontFamily = fontFamily,
            fontSize = titleSizeSp.sp,
            lineHeight = titleLineHeightSp.sp,
            fontWeight = FontWeight.Bold,
            color = palette.onBackground
        )

        val metaText = remember(article.byline, article.readingTimeMinutes, article.wordCount) {
            buildList {
                article.byline?.takeIf { it.isNotBlank() }?.let { add(it) }
                add("${article.readingTimeMinutes} min read")
                if (article.wordCount > 0) add("${article.wordCount} words")
            }.joinToString(" · ")
        }

        Text(
            text = metaText,
            style = MaterialTheme.typography.bodySmall,
            color = palette.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        HorizontalDivider(
            modifier = Modifier.padding(top = 6.dp),
            color = palette.outlineVariant.copy(alpha = 0.45f)
        )
    }
}

@Composable
private fun ReaderBlockItem(
    block: ReaderBlock,
    fontFamily: FontFamily,
    fontSizeSp: Int,
    lineHeightMultiplier: Float,
    palette: ReaderPalette,
    onOpenLink: (String) -> Unit
) {
    val bodyLineHeightSp = (fontSizeSp * lineHeightMultiplier).sp

    when (block) {
        is ReaderBlock.Heading -> {
            val headingSizeSp = when (block.level) {
                1 -> (fontSizeSp + 6)
                2 -> (fontSizeSp + 4)
                3 -> (fontSizeSp + 2)
                else -> (fontSizeSp + 1)
            }.coerceAtMost(34)
            val headingWeight = if (block.level <= 2) FontWeight.Bold else FontWeight.SemiBold

            Text(
                text = block.text,
                fontFamily = fontFamily,
                fontSize = headingSizeSp.sp,
                lineHeight = (headingSizeSp * 1.28f).sp,
                fontWeight = headingWeight,
                color = palette.onBackground,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        is ReaderBlock.Paragraph -> {
            val annotated = remember(block.runs, palette.accent, palette.surfaceContainer, fontSizeSp) {
                buildRunsAnnotatedString(
                    runs = block.runs,
                    linkColor = palette.accent,
                    inlineCodeBackground = palette.surfaceContainer,
                    inlineCodeFontSizeSp = (fontSizeSp * 0.88f),
                    onOpenLink = onOpenLink
                )
            }
            Text(
                text = annotated,
                fontFamily = fontFamily,
                fontSize = fontSizeSp.sp,
                lineHeight = bodyLineHeightSp,
                color = palette.onBackground
            )
        }

        is ReaderBlock.Quote -> {
            val quoteShape = RoundedCornerShape(14.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(quoteShape)
                    .background(palette.surfaceContainerLow)
                    .drawWithCache {
                        val barWidth = 3.dp.toPx()
                        val barInset = 10.dp.toPx()
                        val corner = CornerRadius(barWidth / 2f, barWidth / 2f)
                        onDrawBehind {
                            drawRoundRect(
                                color = palette.accent,
                                topLeft = Offset(0f, barInset),
                                size = Size(barWidth, (size.height - barInset * 2f).coerceAtLeast(0f)),
                                cornerRadius = corner
                            )
                        }
                    }
                    .padding(start = 16.dp, top = 12.dp, end = 14.dp, bottom = 12.dp)
            ) {
                Text(
                    text = block.text,
                    fontFamily = fontFamily,
                    fontSize = fontSizeSp.sp,
                    lineHeight = bodyLineHeightSp,
                    fontStyle = FontStyle.Italic,
                    color = palette.onSurfaceVariant
                )
            }
        }

        is ReaderBlock.CodeBlock -> {
            val codeSizeSp = (fontSizeSp - 2).coerceAtLeast(12)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = palette.surfaceContainer,
                border = BorderStroke(1.dp, palette.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = block.code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = codeSizeSp.sp,
                    lineHeight = (codeSizeSp * 1.45f).sp,
                    color = palette.onBackground,
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(14.dp)
                )
            }
        }

        is ReaderBlock.BulletItem -> {
            val annotated = remember(block.runs, palette.accent, palette.surfaceContainer, fontSizeSp) {
                buildRunsAnnotatedString(
                    runs = block.runs,
                    linkColor = palette.accent,
                    inlineCodeBackground = palette.surfaceContainer,
                    inlineCodeFontSizeSp = (fontSizeSp * 0.88f),
                    onOpenLink = onOpenLink
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = block.orderedIndex?.let { "$it." } ?: "•",
                    fontFamily = fontFamily,
                    fontSize = fontSizeSp.sp,
                    lineHeight = bodyLineHeightSp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.accent,
                    modifier = Modifier.widthIn(min = 20.dp)
                )
                Text(
                    text = annotated,
                    fontFamily = fontFamily,
                    fontSize = fontSizeSp.sp,
                    lineHeight = bodyLineHeightSp,
                    color = palette.onBackground,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        is ReaderBlock.Divider -> {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = palette.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReaderAppearanceSheet(
    settings: BrowserSettings,
    onDismiss: () -> Unit,
    onSurfaceModeChange: (ReaderSurfaceMode) -> Unit,
    onFontFamilyChange: (ReaderFontFamily) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onLineHeightChange: (Float) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Surface selector
            SheetSection(label = "Surface") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReaderSurfaceMode.entries.forEach { mode ->
                        val selected = settings.readerSurfaceMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = { onSurfaceModeChange(mode) },
                            leadingIcon = {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(surfaceSwatchColor(mode))
                                    )
                                }
                            },
                            label = {
                                Text(
                                    text = mode.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            shape = RoundedCornerShape(18.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            // Font family selector
            SheetSection(label = "Typeface") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReaderFontFamily.entries.forEach { family ->
                        val selected = settings.readerFontFamily == family
                        val chipFont = when (family) {
                            ReaderFontFamily.SANS -> FontFamily.SansSerif
                            ReaderFontFamily.SERIF -> FontFamily.Serif
                            ReaderFontFamily.MONO -> FontFamily.Monospace
                        }
                        FilterChip(
                            selected = selected,
                            onClick = { onFontFamilyChange(family) },
                            label = {
                                Text(
                                    text = family.label,
                                    fontFamily = chipFont,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            shape = RoundedCornerShape(18.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            // Font size slider + 48dp stepper buttons
            val currentFontSize = settings.readerFontSizeSp.coerceIn(14, 28)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Text size",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$currentFontSize sp",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = { onFontSizeChange((currentFontSize - 1).coerceIn(14, 28)) },
                        enabled = currentFontSize > 14,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = "Decrease font size"
                        )
                    }

                    Slider(
                        value = currentFontSize.toFloat(),
                        onValueChange = { value ->
                            onFontSizeChange(value.roundToInt().coerceIn(14, 28))
                        },
                        valueRange = 14f..28f,
                        steps = 13,
                        modifier = Modifier.weight(1f)
                    )

                    FilledTonalIconButton(
                        onClick = { onFontSizeChange((currentFontSize + 1).coerceIn(14, 28)) },
                        enabled = currentFontSize < 28,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Increase font size"
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            // Line spacing selector
            val closestSpacing = LineSpacingOptions.minByOrNull { (spacing, _) ->
                abs(settings.readerLineHeight - spacing)
            }?.first ?: 1.6f

            SheetSection(label = "Line spacing") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LineSpacingOptions.forEach { (spacing, label) ->
                        val selected = spacing == closestSpacing
                        FilterChip(
                            selected = selected,
                            onClick = { onLineHeightChange(spacing) },
                            label = {
                                Text(
                                    text = "$label · ${spacing}×",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            shape = RoundedCornerShape(18.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetSection(
    label: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        content()
    }
}

@Composable
private fun resolveReaderPalette(mode: ReaderSurfaceMode): ReaderPalette {
    val scheme = MaterialTheme.colorScheme
    return when (mode) {
        ReaderSurfaceMode.FOLLOW_APP -> ReaderPalette(
            background = scheme.background,
            onBackground = scheme.onBackground,
            onSurfaceVariant = scheme.onSurfaceVariant,
            surfaceContainerLow = scheme.surfaceContainerLow,
            surfaceContainer = scheme.surfaceContainer,
            outlineVariant = scheme.outlineVariant,
            accent = scheme.primary
        )
        ReaderSurfaceMode.DAY -> ReaderPalette(
            background = DaySurface,
            onBackground = DayOnSurface,
            onSurfaceVariant = DayOnSurfaceVariant,
            surfaceContainerLow = DaySurfaceLow,
            surfaceContainer = DaySurfaceContainer,
            outlineVariant = DayOutlineVariant,
            accent = FoxAmberDayPrimary
        )
        ReaderSurfaceMode.SEPIA -> ReaderPalette(
            background = SepiaSurface,
            onBackground = SepiaOnSurface,
            onSurfaceVariant = SepiaOnSurfaceVariant,
            surfaceContainerLow = SepiaSurfaceLow,
            surfaceContainer = SepiaSurfaceContainer,
            outlineVariant = SepiaOutlineVariant,
            accent = SepiaAccent
        )
        ReaderSurfaceMode.DARK -> ReaderPalette(
            background = DarkSurface,
            onBackground = DarkOnSurface,
            onSurfaceVariant = DarkOnSurfaceVariant,
            surfaceContainerLow = DarkSurfaceLow,
            surfaceContainer = DarkSurfaceContainer,
            outlineVariant = DarkOutlineVariant,
            accent = FoxAmberDarkPrimary
        )
        ReaderSurfaceMode.AMOLED -> ReaderPalette(
            background = AmoledSurface,
            onBackground = AmoledOnSurface,
            onSurfaceVariant = AmoledOnSurfaceVariant,
            surfaceContainerLow = AmoledSurfaceLow,
            surfaceContainer = AmoledSurfaceContainer,
            outlineVariant = AmoledOutlineVariant,
            accent = FoxAmberDarkPrimary
        )
    }
}

@Composable
private fun surfaceSwatchColor(mode: ReaderSurfaceMode): Color = when (mode) {
    ReaderSurfaceMode.FOLLOW_APP -> MaterialTheme.colorScheme.primary
    ReaderSurfaceMode.DAY -> DaySurface
    ReaderSurfaceMode.SEPIA -> SepiaSurface
    ReaderSurfaceMode.DARK -> DarkSurface
    ReaderSurfaceMode.AMOLED -> AmoledSurface
}

private fun buildRunsAnnotatedString(
    runs: List<TextRun>,
    linkColor: Color,
    inlineCodeBackground: Color,
    inlineCodeFontSizeSp: Float,
    onOpenLink: (String) -> Unit
): AnnotatedString = buildAnnotatedString {
    for (run in runs) {
        val style = SpanStyle(
            fontWeight = if (run.bold) FontWeight.Bold else null,
            fontStyle = if (run.italic) FontStyle.Italic else null,
            fontFamily = if (run.code) FontFamily.Monospace else null,
            fontSize = if (run.code) inlineCodeFontSizeSp.sp else TextStyleDefaults.UnsetFontSize,
            background = if (run.code) inlineCodeBackground else Color.Unspecified
        )

        val linkUrl = run.linkUrl
        if (linkUrl != null) {
            withLink(
                LinkAnnotation.Url(
                    url = linkUrl,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline
                        )
                    ),
                    linkInteractionListener = LinkInteractionListener {
                        onOpenLink(linkUrl)
                    }
                )
            ) {
                withStyle(style) {
                    append(run.text)
                }
            }
        } else {
            withStyle(style) {
                append(run.text)
            }
        }
    }
}

private object TextStyleDefaults {
    val UnsetFontSize = androidx.compose.ui.unit.TextUnit.Unspecified
}

private fun blockContentType(block: ReaderBlock): String = when (block) {
    is ReaderBlock.Heading -> "heading"
    is ReaderBlock.Paragraph -> "paragraph"
    is ReaderBlock.Quote -> "quote"
    is ReaderBlock.CodeBlock -> "code_block"
    is ReaderBlock.BulletItem -> "bullet_item"
    is ReaderBlock.Divider -> "divider"
}

private fun extractHostLabel(url: String): String? {
    if (url.isBlank()) return null
    return runCatching {
        Uri.parse(url).host?.removePrefix("www.")?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
