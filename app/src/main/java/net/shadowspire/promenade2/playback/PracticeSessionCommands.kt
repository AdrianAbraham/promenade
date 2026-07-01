package net.shadowspire.promenade2.playback

import android.os.Bundle
import androidx.media3.session.SessionCommand

object PracticeSessionCommands {
    const val SetBalance = "net.shadowspire.promenade2.SET_BALANCE"
    const val SetCallsMuted = "net.shadowspire.promenade2.SET_CALLS_MUTED"
    const val SetAutoMute = "net.shadowspire.promenade2.SET_AUTO_MUTE"
    const val SetPlaybackDelay = "net.shadowspire.promenade2.SET_PLAYBACK_DELAY"
    const val CancelPlaybackDelay = "net.shadowspire.promenade2.CANCEL_PLAYBACK_DELAY"

    const val Balance = "balance"
    const val CallsMuted = "callsMuted"
    const val MuteAfterRepetition = "muteAfterRepetition"
    const val MuteWithRepetitionsRemaining = "muteWithRepetitionsRemaining"
    const val PlaybackDelaySeconds = "playbackDelaySeconds"

    val BalanceCommand = SessionCommand(SetBalance, Bundle.EMPTY)
    val CallsMutedCommand = SessionCommand(SetCallsMuted, Bundle.EMPTY)
    val AutoMuteCommand = SessionCommand(SetAutoMute, Bundle.EMPTY)
    val PlaybackDelayCommand = SessionCommand(SetPlaybackDelay, Bundle.EMPTY)
    val CancelPlaybackDelayCommand = SessionCommand(CancelPlaybackDelay, Bundle.EMPTY)
}
