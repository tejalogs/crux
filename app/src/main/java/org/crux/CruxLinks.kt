package org.crux

/** Single source of truth for Crux's public project URLs. */
internal object CruxLinks {
    const val REPOSITORY = "https://github.com/tejalogs/crux"
    const val RELEASES = "$REPOSITORY/releases"
    const val LATEST_RELEASE_API = "https://api.github.com/repos/tejalogs/crux/releases/latest"
    const val ISSUES = "$REPOSITORY/issues"
    const val LICENSE = "$REPOSITORY/blob/main/LICENSE"
    const val PRIVACY = "$REPOSITORY/blob/main/docs/PRIVACY.md"
    const val UPSTREAM = "https://github.com/DimensionDev/Flare"
}
