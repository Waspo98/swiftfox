package app.overbay.swiftfox.ui.browser

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.overbay.swiftfox.data.prefs.SearchEngineId
import app.overbay.swiftfox.features.omnibox.OmniboxSearchScope
import app.overbay.swiftfox.ui.components.SiteIconBadge
import app.overbay.swiftfox.ui.theme.LocalSwiftfoxColors
import app.overbay.swiftfox.ui.theme.rememberReduceMotion

/**
 * Firefox-inspired search-engine selector pill and anchored "This time search in:" dropdown menu,
 * shared by the New Tab Page search capsule and the focused Omnibox overlay.
 */
@Composable
internal fun SearchScopeSelectorAnchor(
    defaultEngine: SearchEngineId,
    customSearchUrlTemplate: String,
    activeScope: OmniboxSearchScope?,
    isPrivate: Boolean,
    compact: Boolean,
    onSelectScope: (OmniboxSearchScope) -> Unit,
    onOpenSearchSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onMenuOpened: () -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val foxColors = LocalSwiftfoxColors.current
    val reduceMotion = rememberReduceMotion()
    val isLightSurface = MaterialTheme.colorScheme.surface.luminance() > 0.5f

    val effectiveScope = activeScope ?: OmniboxSearchScope.Engine(defaultEngine)
    val hasExplicitOverride = activeScope != null
    val accentTint = if (isPrivate) foxColors.privateAccent else MaterialTheme.colorScheme.primary
    val accentContainer = if (isPrivate) foxColors.privateContainer else MaterialTheme.colorScheme.primaryContainer
    val onAccentContainer = if (isPrivate) foxColors.onPrivateContainer else MaterialTheme.colorScheme.onPrimaryContainer

    val pillColor = when {
        hasExplicitOverride -> accentContainer
        isPrivate -> foxColors.privateContainer
        isLightSurface -> MaterialTheme.colorScheme.surfaceContainerLowest
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val pillBorder = when {
        hasExplicitOverride -> BorderStroke(1.dp, accentTint.copy(alpha = 0.55f))
        isPrivate -> BorderStroke(1.dp, foxColors.privateAccent.copy(alpha = 0.38f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    }

    val chevronRotation by animateFloatAsState(
        targetValue = if (menuExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 160),
        label = "search_engine_chevron_rotation"
    )

    val selectorA11yLabel = remember(effectiveScope, customSearchUrlTemplate) {
        "Search in ${effectiveScope.displayLabel(customSearchUrlTemplate)}. Choose search engine"
    }

    Box(modifier = modifier) {
        Surface(
            onClick = {
                onMenuOpened()
                menuExpanded = true
            },
            shape = RoundedCornerShape(22.dp),
            color = pillColor,
            border = pillBorder,
            modifier = Modifier
                .heightIn(min = if (compact) 40.dp else 44.dp)
                .semantics {
                    role = Role.DropdownList
                    contentDescription = selectorA11yLabel
                }
        ) {
            Row(
                modifier = Modifier.padding(
                    start = if (compact) 8.dp else 11.dp,
                    end = if (compact) 5.dp else 7.dp,
                    top = 6.dp,
                    bottom = 6.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when (effectiveScope) {
                    is OmniboxSearchScope.Engine -> {
                        SearchEngineBrandIcon(
                            engine = effectiveScope.engine,
                            customSearchUrlTemplate = customSearchUrlTemplate,
                            size = if (compact) 20.dp else 22.dp,
                            fallbackTint = accentTint
                        )
                    }
                    OmniboxSearchScope.Bookmarks -> {
                        Icon(
                            imageVector = Icons.Rounded.Bookmark,
                            contentDescription = null,
                            tint = accentTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    OmniboxSearchScope.Tabs -> {
                        Icon(
                            imageVector = Icons.Rounded.Tab,
                            contentDescription = null,
                            tint = accentTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    OmniboxSearchScope.History -> {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            tint = accentTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (compact && effectiveScope.isLocalFilter) {
                    Text(
                        text = effectiveScope.shortLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = onAccentContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = if (hasExplicitOverride) accentTint else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = chevronRotation }
                )
            }
        }

        val menuContainerColor = if (isLightSurface) {
            MaterialTheme.colorScheme.surfaceContainerLowest
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            offset = DpOffset(x = 0.dp, y = 6.dp),
            shape = RoundedCornerShape(20.dp),
            containerColor = menuContainerColor,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            modifier = Modifier.widthIn(min = 248.dp, max = 296.dp)
        ) {
            Text(
                text = "This time search in:",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp)
            )

            SearchEngineId.entries.forEach { engine ->
                val isSelectedScope = activeScope == OmniboxSearchScope.Engine(engine)
                val engineLabel = remember(engine, customSearchUrlTemplate) {
                    if (engine == SearchEngineId.CUSTOM && engine.hasDistinctCustomTemplate(customSearchUrlTemplate)) {
                        val host = engine.displayName(customSearchUrlTemplate)
                        if (host != engine.label) "Custom ($host)" else engine.label
                    } else {
                        engine.label
                    }
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            text = engineLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        SearchEngineBrandIcon(
                            engine = engine,
                            customSearchUrlTemplate = customSearchUrlTemplate,
                            size = 24.dp,
                            fallbackTint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = if (isSelectedScope) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Selected",
                                tint = accentTint,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        null
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                    onClick = {
                        menuExpanded = false
                        onSelectScope(OmniboxSearchScope.Engine(engine))
                    }
                )
            }

            SearchLocalScopeMenuItem(
                label = "Bookmarks",
                icon = Icons.Rounded.Bookmark,
                selected = activeScope == OmniboxSearchScope.Bookmarks,
                accentTint = accentTint,
                onClick = {
                    menuExpanded = false
                    onSelectScope(OmniboxSearchScope.Bookmarks)
                }
            )

            SearchLocalScopeMenuItem(
                label = "Tabs",
                icon = Icons.Rounded.Tab,
                selected = activeScope == OmniboxSearchScope.Tabs,
                accentTint = accentTint,
                onClick = {
                    menuExpanded = false
                    onSelectScope(OmniboxSearchScope.Tabs)
                }
            )

            if (!isPrivate) {
                SearchLocalScopeMenuItem(
                    label = "History",
                    icon = Icons.Rounded.History,
                    selected = activeScope == OmniboxSearchScope.History,
                    accentTint = accentTint,
                    onClick = {
                        menuExpanded = false
                        onSelectScope(OmniboxSearchScope.History)
                    }
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            DropdownMenuItem(
                text = {
                    Text(
                        text = "Search settings",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                modifier = Modifier.heightIn(min = 48.dp),
                onClick = {
                    menuExpanded = false
                    onOpenSearchSettings()
                }
            )
        }
    }
}

@Composable
private fun SearchLocalScopeMenuItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    accentTint: Color,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) accentTint else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        },
        trailingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Selected",
                    tint = accentTint,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            null
        },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
        modifier = Modifier.heightIn(min = 48.dp),
        onClick = onClick
    )
}

@Composable
internal fun SearchEngineBrandIcon(
    engine: SearchEngineId,
    customSearchUrlTemplate: String,
    size: Dp,
    fallbackTint: Color
) {
    val iconUrl = remember(engine, customSearchUrlTemplate) {
        if (engine == SearchEngineId.CUSTOM) {
            if (engine.hasDistinctCustomTemplate(customSearchUrlTemplate)) {
                engine.resolvedHomepageUrl(customSearchUrlTemplate)
            } else {
                ""
            }
        } else {
            engine.homepageUrl
        }
    }
    if (iconUrl.isBlank()) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Public,
                contentDescription = null,
                tint = fallbackTint,
                modifier = Modifier.size((size * 0.88f).coerceAtLeast(18.dp))
            )
        }
    } else {
        SiteIconBadge(
            url = iconUrl,
            fallbackLabel = engine.label,
            size = size,
            iconSize = size,
            shape = RoundedCornerShape(6.dp),
            containerColor = Color.Transparent,
            tint = fallbackTint,
            fallbackVector = if (engine == SearchEngineId.CUSTOM) Icons.Rounded.Public else null,
            fillImageToContainer = true
        )
    }
}
