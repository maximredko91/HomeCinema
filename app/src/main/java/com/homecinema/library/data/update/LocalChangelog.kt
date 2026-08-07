package com.homecinema.library.data.update

import com.homecinema.library.R

/**
 * The changelog screen used to fetch this from the GitHub Releases API at runtime, which meant
 * it was unreadable offline - the one place in the app that actually needed a network
 * connection just to show static text. Baked in instead: every release from here on should add
 * its entry at the top of this list (newest first), with a matching string resource pair
 * (values/strings.xml + values-en/strings.xml) for [ReleaseNote.bodyRes], so a rebuilt APK
 * always ships its own changelog in whichever language the user has picked.
 */
val LOCAL_CHANGELOG: List<ReleaseNote> = listOf(
    ReleaseNote(version = "1.21", name = "v1.21", publishedAt = "2026-08-07", bodyRes = R.string.changelog_1_21),
    ReleaseNote(version = "1.20", name = "v1.20", publishedAt = "2026-08-07", bodyRes = R.string.changelog_1_20),
    ReleaseNote(version = "1.19", name = "v1.19", publishedAt = "2026-08-07", bodyRes = R.string.changelog_1_19),
    ReleaseNote(version = "1.18", name = "v1.18", publishedAt = "2026-08-07", bodyRes = R.string.changelog_1_18),
    ReleaseNote(version = "1.17", name = "v1.17", publishedAt = "2026-08-07", bodyRes = R.string.changelog_1_17),
    ReleaseNote(version = "1.16", name = "v1.16", publishedAt = "2026-08-07", bodyRes = R.string.changelog_1_16),
    ReleaseNote(version = "1.15", name = "v1.15", publishedAt = "2026-08-07", bodyRes = R.string.changelog_1_15),
    ReleaseNote(version = "1.14", name = "v1.14", publishedAt = "2026-08-06", bodyRes = R.string.changelog_1_14),
    ReleaseNote(version = "1.13.02", name = "v1.13.02", publishedAt = "2026-08-06", bodyRes = R.string.changelog_1_13_02),
    ReleaseNote(version = "1.13.01", name = "v1.13.01", publishedAt = "2026-08-06", bodyRes = R.string.changelog_1_13_01),
    ReleaseNote(version = "1.12", name = "v1.12", publishedAt = "2026-08-05", bodyRes = R.string.changelog_1_12),
    ReleaseNote(version = "1.11", name = "v1.11", publishedAt = "2026-08-05", bodyRes = R.string.changelog_1_11),
    ReleaseNote(version = "1.10.01", name = "v1.10.01", publishedAt = "2026-08-05", bodyRes = R.string.changelog_1_10_01),
    ReleaseNote(version = "1.10", name = "v1.10", publishedAt = "2026-08-05", bodyRes = R.string.changelog_1_10),
    ReleaseNote(version = "1.9", name = "v1.9", publishedAt = "2026-08-05", bodyRes = R.string.changelog_1_9),
    ReleaseNote(version = "1.8", name = "v1.8", publishedAt = "2026-08-05", bodyRes = R.string.changelog_1_8),
    ReleaseNote(version = "1.7", name = "v1.7", publishedAt = "2026-08-05", bodyRes = R.string.changelog_1_7),
    ReleaseNote(version = "1.6.02", name = "v1.6.02", publishedAt = "2026-08-05", bodyRes = R.string.changelog_1_6_02),
    ReleaseNote(version = "1.6", name = "v1.6", publishedAt = "2026-08-05", bodyRes = R.string.changelog_1_6),
    ReleaseNote(version = "1.5.01", name = "v1.5.01", publishedAt = "2026-08-05", bodyRes = R.string.changelog_1_5_01),
    ReleaseNote(version = "1.5", name = "v1.5", publishedAt = "2026-08-04", bodyRes = R.string.changelog_1_5),
    ReleaseNote(version = "1.4", name = "v1.4", publishedAt = "2026-08-04", bodyRes = R.string.changelog_1_4),
    ReleaseNote(version = "1.3", name = "v1.3", publishedAt = "2026-08-04", bodyRes = R.string.changelog_1_3),
    ReleaseNote(version = "1.2", name = "v1.2", publishedAt = "2026-08-03", bodyRes = R.string.changelog_1_2),
    ReleaseNote(version = "1.1", name = "v1.1", publishedAt = "2026-08-03", bodyRes = R.string.changelog_1_1)
)
