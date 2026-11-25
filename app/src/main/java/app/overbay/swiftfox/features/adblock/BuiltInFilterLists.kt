package app.overbay.swiftfox.features.adblock

import app.overbay.swiftfox.data.db.FilterListEntity

/**
 * Authoritative catalog of built-in Swiftfox adblock/annoyance filter lists and pure reconciliation
 * logic so existing installs pick up fixed upstream URLs and newly added built-in lists without
 * overwriting user toggles or duplicating custom lists with the same URL.
 */
object BuiltInFilterLists {

    const val ID_EASYLIST = "fl_easylist"
    const val ID_EASYPRIVACY = "fl_easyprivacy"
    const val ID_UBO_FILTERS = "fl_ubo_filters"
    const val ID_UBO_BADWARE = "fl_ubo_badware"
    const val ID_UBO_ANNOYANCES = "fl_ubo_annoyances"
    const val ID_ADGUARD_MOBILE_APP_BANNERS = "fl_adguard_mobile_app_banners"
    const val ID_BYPASS_PAYWALLS_CLEAN = "fl_bypass_paywalls_clean"

    val defaults: List<FilterListEntity> = listOf(
        FilterListEntity(
            id = ID_EASYLIST,
            name = "EasyList",
            url = "https://easylist.to/easylist/easylist.txt",
            enabled = true,
            isBuiltIn = true
        ),
        FilterListEntity(
            id = ID_EASYPRIVACY,
            name = "EasyPrivacy",
            url = "https://easylist.to/easylist/easyprivacy.txt",
            enabled = true,
            isBuiltIn = true
        ),
        FilterListEntity(
            id = ID_UBO_FILTERS,
            name = "uBlock Origin Filters",
            url = "https://ublockorigin.github.io/uAssetsCDN/filters/filters.min.txt",
            enabled = true,
            isBuiltIn = true
        ),
        FilterListEntity(
            id = ID_UBO_BADWARE,
            name = "uBlock Badware Risks",
            url = "https://ublockorigin.github.io/uAssetsCDN/filters/badware.min.txt",
            enabled = true,
            isBuiltIn = true
        ),
        FilterListEntity(
            id = ID_UBO_ANNOYANCES,
            name = "uBlock Annoyances",
            url = "https://ublockorigin.github.io/uAssetsCDN/filters/annoyances.min.txt",
            enabled = false,
            isBuiltIn = true
        ),
        FilterListEntity(
            id = ID_ADGUARD_MOBILE_APP_BANNERS,
            name = "AdGuard Mobile App Banners",
            url = "https://filters.adtidy.org/extension/ublock/filters/20.txt",
            enabled = true,
            isBuiltIn = true
        ),
        FilterListEntity(
            id = ID_BYPASS_PAYWALLS_CLEAN,
            name = "Bypass Paywalls Clean",
            url = "https://gitflic.ru/project/magnolia1234/bypass-paywalls-clean-filters/blob/raw?file=bpc-paywall-filter.txt",
            enabled = false,
            isBuiltIn = true
        )
    )

    /**
     * Returns only the [FilterListEntity] rows that must be inserted or updated to reconcile
     * [existing] with [defaults].
     *
     * - Existing built-in lists whose URL changed (e.g. fixing a stale 404 URL) have their URL
     *   updated and stale `etag`/`lastError`/`lastUpdatedAt`/`ruleCount` cleared while preserving
     *   the user's `enabled` state.
     * - Newly introduced built-in lists are inserted unless the user already has a list with the
     *   same URL.
     */
    fun computeReconciliationUpserts(existing: List<FilterListEntity>): List<FilterListEntity> {
        val existingById = existing.associateBy { it.id }
        val existingUrls = existing.mapTo(HashSet(existing.size * 2)) { it.url.trim().lowercase() }
        val upserts = mutableListOf<FilterListEntity>()

        for (builtIn in defaults) {
            val current = existingById[builtIn.id]
            if (current != null) {
                if (current.isBuiltIn && !current.url.trim().equals(builtIn.url, ignoreCase = false)) {
                    upserts.add(
                        current.copy(
                            name = builtIn.name,
                            url = builtIn.url,
                            ruleCount = 0,
                            lastUpdatedAt = 0L,
                            etag = null,
                            lastError = null
                        )
                    )
                }
            } else {
                val normalizedUrl = builtIn.url.trim().lowercase()
                if (normalizedUrl !in existingUrls) {
                    upserts.add(builtIn)
                    existingUrls.add(normalizedUrl)
                }
            }
        }
        return upserts
    }
}
