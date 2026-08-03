package com.homecinema.library.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.homecinema.library.data.db.DownloadState
import com.homecinema.library.data.db.MediaItemEntity
import com.homecinema.library.data.db.MediaType
import com.homecinema.library.ui.theme.HomeCinemaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * MediaPosterCard is a self-contained composable (data + callback in, no HomeCinemaApp
 * singleton dependency) so it's safe to render in isolation without touching the real
 * app's database/settings on whatever device runs this.
 */
@RunWith(AndroidJUnit4::class)
class MediaPosterCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun item(
        title: String = "Интерстеллар",
        year: Int? = 2014,
        genres: String = "Фантастика, Драма",
        mediaType: MediaType = MediaType.MOVIE,
        downloadState: DownloadState = DownloadState.NONE,
        playbackPositionMs: Long = 0,
        durationMs: Long = 0
    ) = MediaItemEntity(
        id = "1",
        title = title,
        year = year,
        genres = genres,
        plot = "",
        rating = null,
        mediaType = mediaType,
        folderPath = "smb://host/share/1/",
        videoFilePath = "smb://host/share/1/video.mkv",
        posterLocalPath = null,
        lastScanned = 0L,
        downloadState = downloadState,
        playbackPositionMs = playbackPositionMs,
        durationMs = durationMs
    )

    @Test
    fun showsTitleAndGenreYearSubtitle() {
        composeRule.setContent {
            HomeCinemaTheme { MediaPosterCard(item = item(), onClick = {}) }
        }

        composeRule.onNodeWithText("Интерстеллар").assertIsDisplayed()
        composeRule.onNodeWithText("Фантастика • 2014").assertIsDisplayed()
    }

    @Test
    fun clickInvokesCallback() {
        var clicked = false
        composeRule.setContent {
            HomeCinemaTheme { MediaPosterCard(item = item(), onClick = { clicked = true }) }
        }

        composeRule.onNodeWithText("Интерстеллар").performClick()
        assert(clicked) { "onClick was not invoked" }
    }

    @Test
    fun showsDownloadedBadgeOnlyWhenCompleted() {
        composeRule.setContent {
            HomeCinemaTheme {
                MediaPosterCard(item = item(downloadState = DownloadState.COMPLETED), onClick = {})
            }
        }
        composeRule.onNodeWithContentDescription("Скачано").assertIsDisplayed()
    }

    @Test
    fun noDownloadedBadgeWhenNotDownloaded() {
        composeRule.setContent {
            HomeCinemaTheme {
                MediaPosterCard(item = item(downloadState = DownloadState.DOWNLOADING), onClick = {})
            }
        }
        composeRule.onNodeWithContentDescription("Скачано").assertDoesNotExist()
    }

    @Test
    fun showsWatchedBadgeWhenNearlyFinished() {
        composeRule.setContent {
            HomeCinemaTheme {
                MediaPosterCard(
                    item = item(playbackPositionMs = 96_000, durationMs = 100_000),
                    onClick = {}
                )
            }
        }
        composeRule.onNodeWithContentDescription("Просмотрено").assertIsDisplayed()
    }

    @Test
    fun noWatchedBadgeWhenOnlyPartiallyWatched() {
        composeRule.setContent {
            HomeCinemaTheme {
                MediaPosterCard(
                    item = item(playbackPositionMs = 40_000, durationMs = 100_000),
                    onClick = {}
                )
            }
        }
        composeRule.onNodeWithContentDescription("Просмотрено").assertDoesNotExist()
    }

    @Test
    fun tvShowTitleIsShownRegardlessOfMediaType() {
        composeRule.setContent {
            HomeCinemaTheme {
                MediaPosterCard(item = item(title = "Улица Сезам", mediaType = MediaType.TV_SHOW), onClick = {})
            }
        }
        composeRule.onNodeWithText("Улица Сезам").assertIsDisplayed()
    }
}
