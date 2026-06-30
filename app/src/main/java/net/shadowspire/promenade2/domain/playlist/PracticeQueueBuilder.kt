package net.shadowspire.promenade2.domain.playlist

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import net.shadowspire.promenade2.core.model.Track
import net.shadowspire.promenade2.playback.PracticeMediaMetadata

object PracticeQueueBuilder {
    fun build(tracks: List<Track>): List<MediaItem> =
        tracks.map { track -> track.toMediaItem() }

    fun Track.toMediaItem(): MediaItem {
        val extras = Bundle().apply {
            putString(PracticeMediaMetadata.CallsUri, callsRef?.uriString)
            putDoubleArray(
                PracticeMediaMetadata.RepetitionStarts,
                repetitions.map { repetition -> repetition.start.value }.toDoubleArray(),
            )
            putString(PracticeMediaMetadata.Intro, intro)
        }

        return MediaItem.Builder()
            .setMediaId(id.jsonFileName)
            .setUri(musicRef.uriString.toUri())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setSubtitle(intro)
                    .setExtras(extras)
                    .build(),
            )
            .build()
    }
}
