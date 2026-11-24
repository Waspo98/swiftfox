package app.overbay.swiftfox.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.swiftfox.features.icons.CustomIconSpec
import app.overbay.swiftfox.features.icons.FaviconRepository
import app.overbay.swiftfox.ui.theme.firstDisplaySymbol
import app.overbay.swiftfox.ui.theme.rememberReduceMotion
import app.overbay.swiftfox.ui.theme.spaceIconFor

val LocalFaviconRepository = staticCompositionLocalOf<FaviconRepository?> { null }

/**
 * Renders any [SpaceEntity.iconKey] — Material Rounded vector, Emoji/text badge, or local custom image.
 */
@Composable
fun SpaceIconGlyph(
    iconKey: String,
    tint: Color,
    contentDescription: String? = null,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    val repo = LocalFaviconRepository.current
    val spec = remember(iconKey) { CustomIconSpec.parse(iconKey) }

    when (spec) {
        is CustomIconSpec.LocalImage -> {
            var bitmap by remember(spec.fileName, repo) {
                mutableStateOf(repo?.getCachedCustomImage(spec.fileName))
            }
            LaunchedEffect(spec.fileName, repo) {
                if (repo == null) return@LaunchedEffect
                if (bitmap == null) {
                    bitmap = repo.loadCustomImage(spec.fileName)
                }
                repo.revision.collect {
                    val cached = repo.getCachedCustomImage(spec.fileName)
                    if (cached != null) {
                        bitmap = cached
                    } else if (bitmap == null) {
                        bitmap = repo.loadCustomImage(spec.fileName)
                    }
                }
            }
            val img = remember(bitmap) { bitmap?.asImageBitmap() }
            if (img != null) {
                Image(
                    bitmap = img,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                    modifier = modifier
                        .size(size)
                        .clip(RoundedCornerShape(size * 0.28f))
                )
            } else {
                Icon(
                    imageVector = spaceIconFor("layers"),
                    contentDescription = contentDescription,
                    tint = tint,
                    modifier = modifier.size(size)
                )
            }
        }
        is CustomIconSpec.Emoji -> {
            Box(
                modifier = modifier.size(size),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = spec.text,
                    fontSize = (size.value * 0.78f).coerceIn(12f, 26f).sp,
                    lineHeight = (size.value * 0.92f).coerceIn(14f, 30f).sp,
                    textAlign = TextAlign.Center,
                    color = tint,
                    maxLines = 1
                )
            }
        }
        is CustomIconSpec.MaterialIcon -> {
            Icon(
                imageVector = spaceIconFor(spec.key),
                contentDescription = contentDescription,
                tint = tint,
                modifier = modifier.size(size)
            )
        }
        CustomIconSpec.None -> {
            Icon(
                imageVector = spaceIconFor("layers"),
                contentDescription = contentDescription,
                tint = tint,
                modifier = modifier.size(size)
            )
        }
    }
}

/**
 * Unified site icon renderer used across Start Page Essentials, Sidebar Essentials/Pinned/Tabs,
 * Tab Switcher, Omnibox suggestions, and Bookmarks/History overlays.
 *
 * Resolution order:
 * 1. User-chosen [customIcon] (LocalImage / Emoji / MaterialIcon)
 * 2. Cached or fetched website favicon for [url] (or in-memory [privateTabId] icon)
 * 3. Optional [fallbackVector] or crisp monogram letter from [fallbackLabel]
 */
@Composable
fun SiteIconBadge(
    url: String,
    fallbackLabel: String,
    modifier: Modifier = Modifier,
    customIcon: String? = null,
    privateTabId: String? = null,
    size: Dp = 28.dp,
    iconSize: Dp = size * 0.68f,
    shape: Shape = RoundedCornerShape(8.dp),
    containerColor: Color = Color.Transparent,
    tint: Color = MaterialTheme.colorScheme.primary,
    monogramFontSize: TextUnit = (size.value * 0.44f).coerceIn(11f, 22f).sp,
    fallbackVector: ImageVector? = null,
    fillImageToContainer: Boolean = false
) {
    val repo = LocalFaviconRepository.current
    val reduceMotion = rememberReduceMotion()
    val spec = remember(customIcon) { CustomIconSpec.parse(customIcon) }

    val hostKey = remember(url) { FaviconRepository.cacheKeyForUrl(url) }
    var customBitmap by remember(spec, repo) {
        mutableStateOf(
            if (spec is CustomIconSpec.LocalImage) repo?.getCachedCustomImage(spec.fileName) else null
        )
    }
    var faviconBitmap by remember(hostKey, privateTabId, repo) {
        mutableStateOf<Bitmap?>(
            if (spec == CustomIconSpec.None && repo != null) {
                repo.getCachedFavicon(url, privateTabId)
            } else {
                null
            }
        )
    }

    LaunchedEffect(spec, repo) {
        if (spec is CustomIconSpec.LocalImage && repo != null) {
            if (customBitmap == null) {
                customBitmap = repo.loadCustomImage(spec.fileName)
            }
            repo.revision.collect {
                val cached = repo.getCachedCustomImage(spec.fileName)
                if (cached != null) {
                    customBitmap = cached
                } else if (customBitmap == null) {
                    customBitmap = repo.loadCustomImage(spec.fileName)
                }
            }
        } else {
            customBitmap = null
        }
    }

    // Fetch website favicon once per target without tying cancellation to global cache revisions
    LaunchedEffect(spec, hostKey, url, privateTabId, repo) {
        if (spec != CustomIconSpec.None || repo == null) return@LaunchedEffect
        val cached = repo.getCachedFavicon(url, privateTabId)
        if (cached != null) {
            faviconBitmap = cached
        } else if (privateTabId == null && hostKey != null) {
            val fetched = repo.getOrFetchFavicon(url, privateTabId = null)
            if (fetched != null) {
                faviconBitmap = fetched
            }
        }
    }

    // Observe cache updates (WebView onReceivedIcon, private tab icon updates, or cache clears)
    // without cancelling in-flight fetches or recomposing unrelated badges.
    LaunchedEffect(spec, hostKey, privateTabId, repo) {
        if (spec != CustomIconSpec.None || repo == null) return@LaunchedEffect
        repo.revision.collect {
            val cached = repo.getCachedFavicon(url, privateTabId)
            if (cached !== faviconBitmap) {
                faviconBitmap = cached
            }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .then(if (containerColor != Color.Transparent) Modifier.background(containerColor) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        when (spec) {
            is CustomIconSpec.LocalImage -> {
                val img = remember(customBitmap) { customBitmap?.asImageBitmap() }
                if (img != null) {
                    Image(
                        bitmap = img,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.High,
                        modifier = if (fillImageToContainer) {
                            Modifier.fillMaxSize().clip(shape)
                        } else {
                            Modifier.size(iconSize).clip(RoundedCornerShape(iconSize * 0.24f))
                        }
                    )
                } else {
                    MonogramOrVectorFallback(fallbackLabel, fallbackVector, tint, iconSize, monogramFontSize)
                }
            }
            is CustomIconSpec.Emoji -> {
                Text(
                    text = spec.text,
                    fontSize = (iconSize.value * 0.86f).coerceIn(13f, 28f).sp,
                    lineHeight = (iconSize.value * 0.98f).coerceIn(15f, 32f).sp,
                    textAlign = TextAlign.Center,
                    color = tint,
                    maxLines = 1
                )
            }
            is CustomIconSpec.MaterialIcon -> {
                Icon(
                    imageVector = spaceIconFor(spec.key),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(iconSize)
                )
            }
            CustomIconSpec.None -> {
                val img = remember(faviconBitmap) { faviconBitmap?.asImageBitmap() }
                Crossfade(
                    targetState = img,
                    animationSpec = if (reduceMotion) snap() else tween(160),
                    label = "favicon_crossfade"
                ) { loadedImg ->
                    if (loadedImg != null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = loadedImg,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                filterQuality = FilterQuality.High,
                                modifier = Modifier
                                    .size(iconSize)
                                    .clip(RoundedCornerShape(iconSize * 0.22f))
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            MonogramOrVectorFallback(
                                fallbackLabel = fallbackLabel,
                                fallbackVector = fallbackVector,
                                tint = tint,
                                iconSize = iconSize,
                                monogramFontSize = monogramFontSize
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonogramOrVectorFallback(
    fallbackLabel: String,
    fallbackVector: ImageVector?,
    tint: Color,
    iconSize: Dp,
    monogramFontSize: TextUnit
) {
    if (fallbackVector != null) {
        Icon(
            imageVector = fallbackVector,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size((iconSize * 0.88f).coerceAtLeast(16.dp))
        )
    } else {
        Text(
            text = firstDisplaySymbol(fallbackLabel, fallback = "S"),
            fontSize = monogramFontSize,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}
