package app.overbay.swiftfox.ui.reader

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import app.overbay.swiftfox.SwiftfoxApp
import app.overbay.swiftfox.data.prefs.AccentPreset
import app.overbay.swiftfox.data.prefs.BrowserSettings
import app.overbay.swiftfox.data.prefs.ReaderFontFamily
import app.overbay.swiftfox.data.prefs.ReaderSurfaceMode
import app.overbay.swiftfox.features.adblock.AdblockEngine
import app.overbay.swiftfox.features.omnibox.OmniboxEngine
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
import app.overbay.swiftfox.ui.theme.ForestDarkPrimary
import app.overbay.swiftfox.ui.theme.ForestDayPrimary
import app.overbay.swiftfox.ui.theme.FoxAmberDarkPrimary
import app.overbay.swiftfox.ui.theme.FoxAmberDayPrimary
import app.overbay.swiftfox.ui.theme.IndigoDarkPrimary
import app.overbay.swiftfox.ui.theme.IndigoDayPrimary
import app.overbay.swiftfox.ui.theme.MonoDarkPrimary
import app.overbay.swiftfox.ui.theme.MonoDayPrimary
import app.overbay.swiftfox.ui.theme.rememberReduceMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.max
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
private val ReaderCardShape = RoundedCornerShape(14.dp)

private val LineSpacingOptions = listOf(
    1.4f to "Compact",
    1.6f to "Balanced",
    1.85f to "Relaxed"
)

private object ReaderImageCache {
    private const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
    private const val MAX_BITMAP_DIM = 1440
    private const val MAX_IMAGE_REDIRECTS = 5

    private val cache = object : LruCache<String, ImageBitmap>(12) {}

    fun peek(
        url: String,
        isPrivate: Boolean,
        privateCache: LruCache<String, ImageBitmap>? = null
    ): ImageBitmap? {
        return if (isPrivate) privateCache?.get(url) else cache.get(url)
    }

    private fun resolveTargetUrl(
        rawUrl: String,
        httpsOnlyMode: Boolean,
        isHostHttpsBypassed: (String) -> Boolean
    ): String? {
        val scheme = runCatching { Uri.parse(rawUrl).scheme?.lowercase(java.util.Locale.ROOT) }.getOrNull()
        if (scheme != "http" && scheme != "https") return null
        return if (scheme == "http" && httpsOnlyMode) {
            val host = OmniboxEngine.extractHost(rawUrl)
            if (host.isNotBlank() && !OmniboxEngine.isLocalHostOrLan(host) && !isHostHttpsBypassed(host)) {
                "https://" + rawUrl.substringAfter("://")
            } else {
                rawUrl
            }
        } else {
            rawUrl
        }
    }

    suspend fun load(
        client: OkHttpClient,
        url: String,
        pageUrl: String,
        isPrivate: Boolean,
        httpsOnlyMode: Boolean,
        adblockEnabled: Boolean,
        siteAdblockDisabled: Boolean,
        adblockEngine: AdblockEngine?,
        isHostHttpsBypassed: (String) -> Boolean,
        privateCache: LruCache<String, ImageBitmap>? = null
    ): ImageBitmap? = withContext(Dispatchers.IO) {
        val initialTarget = resolveTargetUrl(url, httpsOnlyMode, isHostHttpsBypassed) ?: return@withContext null
        val pageHost = OmniboxEngine.extractHost(pageUrl)
        val pageIsLan = pageHost.isNotBlank() && OmniboxEngine.isLocalHostOrLan(pageHost)

        if (isPrivate) {
            privateCache?.get(url)?.let { return@withContext it }
        } else {
            cache.get(url)?.let { return@withContext it }
            cache.get(initialTarget)?.let { return@withContext it }
        }

        try {
            val noRedirectClient = client.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
            var currentUrl = initialTarget
            var hops = 0
            while (hops <= MAX_IMAGE_REDIRECTS) {
                val hopHost = OmniboxEngine.extractHost(currentUrl)
                // Prevent public articles from redirecting image fetches onto private LAN hosts
                if (!pageIsLan && hopHost.isNotBlank() && OmniboxEngine.isLocalHostOrLan(hopHost)) {
                    return@withContext null
                }
                if (adblockEngine?.shouldBlock(
                        requestUrl = currentUrl,
                        pageUrl = pageUrl,
                        resourceType = "image",
                        globalEnabled = adblockEnabled,
                        siteDisabled = siteAdblockDisabled
                    ) == true
                ) {
                    return@withContext null
                }

                val request = Request.Builder()
                    .url(currentUrl)
                    .get()
                    .header("Accept", "image/webp,image/png,image/jpeg,image/*;q=0.8")
                    .build()
                noRedirectClient.newCall(request).execute().use { response ->
                    if (response.isRedirect) {
                        val location = response.header("Location") ?: return@withContext null
                        val resolved = response.request.url.resolve(location)?.toString() ?: return@withContext null
                        val nextUrl = resolveTargetUrl(resolved, httpsOnlyMode, isHostHttpsBypassed)
                            ?: return@withContext null
                        currentUrl = nextUrl
                        hops++
                        return@use
                    }
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body
                    if (body.contentLength() > MAX_IMAGE_BYTES) return@withContext null
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    var total = 0
                    body.byteStream().use { input ->
                        while (true) {
                            val read = input.read(buf)
                            if (read == -1) break
                            total += read
                            if (total > MAX_IMAGE_BYTES) return@withContext null
                            out.write(buf, 0, read)
                        }
                    }
                    val bytes = out.toByteArray()
                    if (bytes.isEmpty()) return@withContext null

                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

                    var sampleSize = 1
                    while (bounds.outWidth / sampleSize > MAX_BITMAP_DIM ||
                        bounds.outHeight / sampleSize > MAX_BITMAP_DIM
                    ) {
                        sampleSize *= 2
                    }

                    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
                        ?: return@withContext null
                    val imageBitmap = decoded.asImageBitmap()
                    if (isPrivate) {
                        privateCache?.put(url, imageBitmap)
                    } else {
                        cache.put(url, imageBitmap)
                        cache.put(currentUrl, imageBitmap)
                    }
                    return@withContext imageBitmap
                }
            }
            null
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            null
        }
    }
}

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
    onLineHeightChange: (Float) -> Unit,
    pageUrl: String = article.url,
    isPrivate: Boolean = false,
    siteAdblockDisabled: Boolean = false,
    isHostHttpsBypassed: (String) -> Boolean = { false }
) {
    var showAppearanceSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val adblockEngine = remember(context) {
        (context.applicationContext as? SwiftfoxApp)?.adblockEngine
    }
    val reduceMotion = rememberReduceMotion()

    val targetPalette = resolveReaderPalette(settings.readerSurfaceMode, settings)
    val readerIsLight = when (settings.readerSurfaceMode) {
        ReaderSurfaceMode.DAY, ReaderSurfaceMode.SEPIA -> true
        ReaderSurfaceMode.DARK, ReaderSurfaceMode.AMOLED -> false
        ReaderSurfaceMode.FOLLOW_APP -> targetPalette.background.luminance() > 0.5f
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null) {
            val controller = remember(window, view) {
                WindowCompat.getInsetsController(window, view)
            }
            val prevBars = remember(controller) {
                controller.isAppearanceLightStatusBars to controller.isAppearanceLightNavigationBars
            }
            DisposableEffect(controller) {
                onDispose {
                    controller.isAppearanceLightStatusBars = prevBars.first
                    controller.isAppearanceLightNavigationBars = prevBars.second
                }
            }
            androidx.compose.runtime.SideEffect {
                controller.isAppearanceLightStatusBars = readerIsLight
                controller.isAppearanceLightNavigationBars = readerIsLight
            }
        }
    }

    val colorSpec = remember(reduceMotion) {
        if (reduceMotion) snap<Color>() else tween<Color>(durationMillis = 180)
    }
    // Defer animated background reads to the Draw phase so theme transitions never
    // recompose the entire article LazyColumn on every animation frame.
    val animatedBackgroundState = animateColorAsState(
        targetValue = targetPalette.background,
        animationSpec = colorSpec,
        label = "reader_background"
    )
    val palette = targetPalette

    val fontFamily = remember(settings.readerFontFamily) {
        when (settings.readerFontFamily) {
            ReaderFontFamily.SANS -> FontFamily.SansSerif
            ReaderFontFamily.SERIF -> FontFamily.Serif
            ReaderFontFamily.MONO -> FontFamily.Monospace
        }
    }

    val fontSizeSp = settings.readerFontSizeSp.coerceIn(14, 28)
    val lineHeightMultiplier = settings.readerLineHeight.coerceIn(1.3f, 2.0f)
    val currentOnOpenLink by rememberUpdatedState(onOpenLink)
    val stableOpenLink = remember { { linkUrl: String -> currentOnOpenLink(linkUrl) } }
    val privateSessionCache = remember(article.url, isPrivate) {
        if (isPrivate) LruCache<String, ImageBitmap>(12) else null
    }
    val listState = rememberLazyListState()
    LaunchedEffect(article.url) {
        listState.scrollToItem(0)
    }
    val sourceLabel = remember(article.siteName, article.url) {
        article.siteName?.trim()?.takeIf { it.isNotEmpty() }
            ?: extractHostLabel(article.url)
            ?: "Reader"
    }

    Scaffold(
        modifier = Modifier.drawBehind {
            drawRect(animatedBackgroundState.value)
        },
        containerColor = Color.Transparent,
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
                    containerColor = Color.Transparent,
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
                .padding(top = innerPadding.calculateTopPadding()),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 16.dp,
                    bottom = 24.dp + innerPadding.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(key = "reader_article_header", contentType = "article_header") {
                    SelectionContainer {
                        ReaderArticleHeader(
                            article = article,
                            sourceLabel = sourceLabel,
                            fontFamily = fontFamily,
                            baseFontSizeSp = fontSizeSp,
                            palette = palette
                        )
                    }
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
                        onOpenLink = stableOpenLink,
                        pageUrl = pageUrl,
                        isPrivate = isPrivate,
                        httpsOnlyMode = settings.httpsOnlyMode,
                        adblockEnabled = settings.adblockEnabled,
                        siteAdblockDisabled = siteAdblockDisabled,
                        adblockEngine = adblockEngine,
                        isHostHttpsBypassed = isHostHttpsBypassed,
                        privateImageCache = privateSessionCache
                    )
                }

                item(key = "reader_footer_spacer", contentType = "spacer") {
                    Spacer(modifier = Modifier.height(32.dp))
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
    onOpenLink: (String) -> Unit,
    pageUrl: String,
    isPrivate: Boolean,
    httpsOnlyMode: Boolean,
    adblockEnabled: Boolean,
    siteAdblockDisabled: Boolean,
    adblockEngine: AdblockEngine?,
    isHostHttpsBypassed: (String) -> Boolean,
    privateImageCache: LruCache<String, ImageBitmap>?
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

            SelectionContainer {
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
            SelectionContainer {
                Text(
                    text = annotated,
                    fontFamily = fontFamily,
                    fontSize = fontSizeSp.sp,
                    lineHeight = bodyLineHeightSp,
                    color = palette.onBackground
                )
            }
        }

        is ReaderBlock.Quote -> {
            SelectionContainer {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ReaderCardShape)
                        .background(palette.surfaceContainerLow)
                        .drawWithCache {
                            val barWidth = 3.dp.toPx()
                            val barInset = 10.dp.toPx()
                            val twoBarInset = barInset * 2f
                            val barColor = palette.accent
                            val corner = CornerRadius(barWidth / 2f, barWidth / 2f)
                            onDrawBehind {
                                drawRoundRect(
                                    color = barColor,
                                    topLeft = Offset(0f, barInset),
                                    size = Size(barWidth, (size.height - twoBarInset).coerceAtLeast(0f)),
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
        }

        is ReaderBlock.CodeBlock -> {
            val codeSizeSp = (fontSizeSp - 2).coerceAtLeast(12)
            SelectionContainer {
                Surface(
                    shape = ReaderCardShape,
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
            val startPad = (4 + block.indentLevel.coerceIn(0, 4) * 18).dp
            val marker = block.orderedIndex?.let { "$it." } ?: when (block.indentLevel % 3) {
                0 -> "•"
                1 -> "◦"
                else -> "▪"
            }
            SelectionContainer {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = startPad),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = marker,
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
        }

        is ReaderBlock.Image -> {
            ReaderImageBlock(
                block = block,
                fontFamily = fontFamily,
                fontSizeSp = fontSizeSp,
                palette = palette,
                pageUrl = pageUrl,
                isPrivate = isPrivate,
                httpsOnlyMode = httpsOnlyMode,
                adblockEnabled = adblockEnabled,
                siteAdblockDisabled = siteAdblockDisabled,
                adblockEngine = adblockEngine,
                isHostHttpsBypassed = isHostHttpsBypassed,
                privateImageCache = privateImageCache
            )
        }

        is ReaderBlock.Table -> {
            ReaderTableBlock(
                block = block,
                fontFamily = fontFamily,
                fontSizeSp = fontSizeSp,
                lineHeightMultiplier = lineHeightMultiplier,
                palette = palette,
                onOpenLink = onOpenLink
            )
        }

        is ReaderBlock.DefinitionItem -> {
            ReaderDefinitionBlock(
                block = block,
                fontFamily = fontFamily,
                fontSizeSp = fontSizeSp,
                lineHeightMultiplier = lineHeightMultiplier,
                palette = palette,
                onOpenLink = onOpenLink
            )
        }

        is ReaderBlock.Divider -> {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = palette.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun ReaderImageBlock(
    block: ReaderBlock.Image,
    fontFamily: FontFamily,
    fontSizeSp: Int,
    palette: ReaderPalette,
    pageUrl: String,
    isPrivate: Boolean,
    httpsOnlyMode: Boolean,
    adblockEnabled: Boolean,
    siteAdblockDisabled: Boolean,
    adblockEngine: AdblockEngine?,
    isHostHttpsBypassed: (String) -> Boolean,
    privateImageCache: LruCache<String, ImageBitmap>?
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val httpClient = remember(context) {
        (context.applicationContext as? SwiftfoxApp)?.httpClient ?: OkHttpClient()
    }
    val initialCached = remember(block.url, isPrivate, privateImageCache) {
        ReaderImageCache.peek(block.url, isPrivate, privateImageCache)
    }
    var loadedBitmap by remember(block.url) { mutableStateOf(initialCached) }
    var loadFinished by remember(block.url) { mutableStateOf(initialCached != null) }

    LaunchedEffect(block.url, pageUrl, isPrivate, httpsOnlyMode, adblockEnabled, siteAdblockDisabled) {
        val cached = ReaderImageCache.peek(block.url, isPrivate, privateImageCache)
        if (cached != null) {
            loadedBitmap = cached
            loadFinished = true
            return@LaunchedEffect
        }
        loadFinished = false
        loadedBitmap = ReaderImageCache.load(
            client = httpClient,
            url = block.url,
            pageUrl = pageUrl,
            isPrivate = isPrivate,
            httpsOnlyMode = httpsOnlyMode,
            adblockEnabled = adblockEnabled,
            siteAdblockDisabled = siteAdblockDisabled,
            adblockEngine = adblockEngine,
            isHostHttpsBypassed = isHostHttpsBypassed,
            privateCache = privateImageCache
        )
        loadFinished = true
    }

    val captionText = block.caption ?: block.alt

    if (loadFinished && loadedBitmap == null) {
        if (!captionText.isNullOrBlank()) {
            SelectionContainer {
                Text(
                    text = captionText,
                    fontFamily = fontFamily,
                    fontSize = (fontSizeSp - 2).coerceAtLeast(12).sp,
                    lineHeight = ((fontSizeSp - 2).coerceAtLeast(12) * 1.4f).sp,
                    fontStyle = FontStyle.Italic,
                    color = palette.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = ReaderCardShape,
            color = palette.surfaceContainerLow,
            border = BorderStroke(1.dp, palette.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            val bmp = loadedBitmap
            if (bmp != null) {
                val naturalWidthDp = with(density) { bmp.width.toDp() }
                val isSmallInlineImage = bmp.width < 240 && bmp.height < 240
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bmp,
                        contentDescription = block.alt ?: block.caption ?: "Article image",
                        contentScale = ContentScale.Fit,
                        modifier = if (isSmallInlineImage) {
                            Modifier
                                .widthIn(max = naturalWidthDp.coerceAtLeast(48.dp))
                                .padding(12.dp)
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 560.dp)
                        }
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = palette.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        if (!block.caption.isNullOrBlank()) {
            SelectionContainer {
                Text(
                    text = block.caption,
                    fontFamily = fontFamily,
                    fontSize = (fontSizeSp - 2).coerceAtLeast(12).sp,
                    lineHeight = ((fontSizeSp - 2).coerceAtLeast(12) * 1.4f).sp,
                    fontStyle = FontStyle.Italic,
                    color = palette.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ReaderTableBlock(
    block: ReaderBlock.Table,
    fontFamily: FontFamily,
    fontSizeSp: Int,
    lineHeightMultiplier: Float,
    palette: ReaderPalette,
    onOpenLink: (String) -> Unit
) {
    val colCount = remember(block.headers, block.rows) {
        max(
            block.headers.size,
            block.rows.maxOfOrNull { it.size } ?: 0
        )
    }
    if (colCount == 0) return

    val cellFontSizeSp = (fontSizeSp - 2).coerceAtLeast(13)
    val cellLineHeightSp = (cellFontSizeSp * (lineHeightMultiplier - 0.1f).coerceAtLeast(1.3f)).sp
    val scaleFactor = fontSizeSp / 16f

    val columnWidths: List<Dp> = remember(block.headers, block.rows, colCount, scaleFactor) {
        (0 until colCount).map { col ->
            val headerLen = block.headers.getOrNull(col)?.sumOf { it.text.length } ?: 0
            val maxDataLen = block.rows.maxOfOrNull { row ->
                row.getOrNull(col)?.sumOf { it.text.length } ?: 0
            } ?: 0
            val maxChars = max(headerLen, maxDataLen)
            val estimatedDp = (maxChars * 8.2f * scaleFactor + 28f).coerceIn(88f, 240f)
            estimatedDp.dp
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!block.caption.isNullOrBlank()) {
            SelectionContainer {
                Text(
                    text = block.caption,
                    fontFamily = fontFamily,
                    fontSize = cellFontSizeSp.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }

        Surface(
            shape = ReaderCardShape,
            color = palette.surfaceContainerLow,
            border = BorderStroke(1.dp, palette.outlineVariant.copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
            SelectionContainer {
                Column {
                    if (block.headers.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .height(IntrinsicSize.Min)
                                .background(palette.surfaceContainer)
                        ) {
                            block.headers.forEachIndexed { colIndex, cellRuns ->
                                if (colIndex > 0) {
                                    VerticalDivider(
                                        color = palette.outlineVariant.copy(alpha = 0.35f),
                                        thickness = 0.5.dp
                                    )
                                }
                                val annotated = remember(cellRuns, palette.accent, palette.background, cellFontSizeSp) {
                                    buildRunsAnnotatedString(
                                        runs = cellRuns,
                                        linkColor = palette.accent,
                                        inlineCodeBackground = palette.background.copy(alpha = 0.55f),
                                        inlineCodeFontSizeSp = (cellFontSizeSp * 0.9f),
                                        onOpenLink = onOpenLink
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .width(columnWidths[colIndex])
                                        .fillMaxHeight()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = annotated,
                                        fontFamily = fontFamily,
                                        fontSize = cellFontSizeSp.sp,
                                        lineHeight = cellLineHeightSp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = palette.onBackground
                                    )
                                }
                            }
                        }
                        HorizontalDivider(
                            color = palette.outlineVariant.copy(alpha = 0.7f),
                            thickness = 1.dp
                        )
                    }

                    block.rows.forEachIndexed { rowIndex, rowCells ->
                        if (rowIndex > 0) {
                            HorizontalDivider(
                                color = palette.outlineVariant.copy(alpha = 0.3f),
                                thickness = 0.5.dp
                            )
                        }
                        val rowBg = if (rowIndex % 2 == 1) {
                            palette.background.copy(alpha = 0.35f)
                        } else {
                            Color.Transparent
                        }
                        Row(
                            modifier = Modifier
                                .height(IntrinsicSize.Min)
                                .background(rowBg)
                        ) {
                            rowCells.forEachIndexed { colIndex, cellRuns ->
                                if (colIndex > 0) {
                                    VerticalDivider(
                                        color = palette.outlineVariant.copy(alpha = 0.3f),
                                        thickness = 0.5.dp
                                    )
                                }
                                val annotated = remember(cellRuns, palette.accent, palette.surfaceContainer, cellFontSizeSp) {
                                    buildRunsAnnotatedString(
                                        runs = cellRuns,
                                        linkColor = palette.accent,
                                        inlineCodeBackground = palette.surfaceContainer,
                                        inlineCodeFontSizeSp = (cellFontSizeSp * 0.9f),
                                        onOpenLink = onOpenLink
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .width(columnWidths[colIndex])
                                        .fillMaxHeight()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.TopStart
                                ) {
                                    Text(
                                        text = annotated,
                                        fontFamily = fontFamily,
                                        fontSize = cellFontSizeSp.sp,
                                        lineHeight = cellLineHeightSp,
                                        color = palette.onBackground
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun ReaderDefinitionBlock(
    block: ReaderBlock.DefinitionItem,
    fontFamily: FontFamily,
    fontSizeSp: Int,
    lineHeightMultiplier: Float,
    palette: ReaderPalette,
    onOpenLink: (String) -> Unit
) {
    val bodyLineHeightSp = (fontSizeSp * lineHeightMultiplier).sp
    val termAnnotated = remember(block.term, palette.accent, palette.surfaceContainer, fontSizeSp) {
        buildRunsAnnotatedString(
            runs = block.term,
            linkColor = palette.accent,
            inlineCodeBackground = palette.surfaceContainer,
            inlineCodeFontSizeSp = (fontSizeSp * 0.88f),
            onOpenLink = onOpenLink
        )
    }

    SelectionContainer {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = termAnnotated,
                fontFamily = fontFamily,
                fontSize = fontSizeSp.sp,
                lineHeight = bodyLineHeightSp,
                fontWeight = FontWeight.SemiBold,
                color = palette.onBackground
            )
            block.definitions.forEach { defRuns ->
                val defAnnotated = remember(defRuns, palette.accent, palette.surfaceContainer, fontSizeSp) {
                    buildRunsAnnotatedString(
                        runs = defRuns,
                        linkColor = palette.accent,
                        inlineCodeBackground = palette.surfaceContainer,
                        inlineCodeFontSizeSp = (fontSizeSp * 0.88f),
                        onOpenLink = onOpenLink
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp)
                        .drawWithCache {
                            val barWidth = 2.dp.toPx()
                            val vInset = 4.dp.toPx()
                            val twoVInset = 8.dp.toPx()
                            val barColor = palette.outlineVariant.copy(alpha = 0.65f)
                            onDrawBehind {
                                drawRect(
                                    color = barColor,
                                    topLeft = Offset(0f, vInset),
                                    size = Size(barWidth, (size.height - twoVInset).coerceAtLeast(0f))
                                )
                            }
                        }
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = defAnnotated,
                        fontFamily = fontFamily,
                        fontSize = fontSizeSp.sp,
                        lineHeight = bodyLineHeightSp,
                        color = palette.onSurfaceVariant
                    )
                }
            }
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
                                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
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
            var sliderFontSize by remember(currentFontSize) { mutableFloatStateOf(currentFontSize.toFloat()) }
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
                        text = "${sliderFontSize.roundToInt().coerceIn(14, 28)} sp",
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
                        value = sliderFontSize,
                        onValueChange = { value ->
                            sliderFontSize = value.coerceIn(14f, 28f)
                        },
                        onValueChangeFinished = {
                            onFontSizeChange(sliderFontSize.roundToInt().coerceIn(14, 28))
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
private fun resolveReaderPalette(
    mode: ReaderSurfaceMode,
    settings: BrowserSettings
): ReaderPalette {
    val scheme = MaterialTheme.colorScheme
    val lightAccent = when {
        settings.useDynamicColor && scheme.background.luminance() > 0.5f -> scheme.primary
        else -> when (settings.accentPreset) {
            AccentPreset.FOX_AMBER -> FoxAmberDayPrimary
            AccentPreset.MIDNIGHT_INDIGO -> IndigoDayPrimary
            AccentPreset.FOREST -> ForestDayPrimary
            AccentPreset.MONOCHROME -> MonoDayPrimary
        }
    }
    val darkAccent = when {
        settings.useDynamicColor && scheme.background.luminance() <= 0.5f -> scheme.primary
        else -> when (settings.accentPreset) {
            AccentPreset.FOX_AMBER -> FoxAmberDarkPrimary
            AccentPreset.MIDNIGHT_INDIGO -> IndigoDarkPrimary
            AccentPreset.FOREST -> ForestDarkPrimary
            AccentPreset.MONOCHROME -> MonoDarkPrimary
        }
    }
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
            accent = lightAccent
        )
        ReaderSurfaceMode.SEPIA -> ReaderPalette(
            background = SepiaSurface,
            onBackground = SepiaOnSurface,
            onSurfaceVariant = SepiaOnSurfaceVariant,
            surfaceContainerLow = SepiaSurfaceLow,
            surfaceContainer = SepiaSurfaceContainer,
            outlineVariant = SepiaOutlineVariant,
            accent = lightAccent
        )
        ReaderSurfaceMode.DARK -> ReaderPalette(
            background = DarkSurface,
            onBackground = DarkOnSurface,
            onSurfaceVariant = DarkOnSurfaceVariant,
            surfaceContainerLow = DarkSurfaceLow,
            surfaceContainer = DarkSurfaceContainer,
            outlineVariant = DarkOutlineVariant,
            accent = darkAccent
        )
        ReaderSurfaceMode.AMOLED -> ReaderPalette(
            background = AmoledSurface,
            onBackground = AmoledOnSurface,
            onSurfaceVariant = AmoledOnSurfaceVariant,
            surfaceContainerLow = AmoledSurfaceLow,
            surfaceContainer = AmoledSurfaceContainer,
            outlineVariant = AmoledOutlineVariant,
            accent = darkAccent
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
        val decoration = when {
            run.linkUrl != null && run.strikethrough -> TextDecoration.combine(
                listOf(TextDecoration.Underline, TextDecoration.LineThrough)
            )
            run.strikethrough -> TextDecoration.LineThrough
            else -> null
        }

        val style = SpanStyle(
            fontWeight = if (run.bold) FontWeight.Bold else null,
            fontStyle = if (run.italic) FontStyle.Italic else null,
            fontFamily = if (run.code) FontFamily.Monospace else null,
            fontSize = if (run.code) inlineCodeFontSizeSp.sp else TextStyleDefaults.UnsetFontSize,
            background = if (run.code) inlineCodeBackground else Color.Unspecified,
            textDecoration = decoration
        )

        val linkUrl = run.linkUrl
        if (linkUrl != null) {
            withLink(
                LinkAnnotation.Url(
                    url = linkUrl,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = decoration ?: TextDecoration.Underline
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
    is ReaderBlock.Image -> "image"
    is ReaderBlock.Table -> "table"
    is ReaderBlock.DefinitionItem -> "definition_item"
    is ReaderBlock.Divider -> "divider"
}

private fun extractHostLabel(url: String): String? {
    if (url.isBlank()) return null
    return runCatching {
        Uri.parse(url).host?.removePrefix("www.")?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
