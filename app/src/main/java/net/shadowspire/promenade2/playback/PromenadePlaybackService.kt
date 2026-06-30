package net.shadowspire.promenade2.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionError
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.shadowspire.promenade2.MainActivity
import net.shadowspire.promenade2.core.model.AutoMuteSettings
import net.shadowspire.promenade2.core.model.PlaybackDelaySettings

class PromenadePlaybackService : MediaSessionService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaSession? = null
    private var audioCoordinator: DualAudioCoordinator? = null

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true)
            .build()

        val coordinator = DualAudioCoordinator(
            context = this,
            musicPlayer = player,
            scope = serviceScope,
        )
        coordinator.attach()
        audioCoordinator = coordinator

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(PracticeSessionCallback(coordinator))
            .setId(SESSION_ID)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        val session = mediaSession
        mediaSession = null
        audioCoordinator?.release()
        audioCoordinator = null
        session?.run {
            player.release()
            release()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private class PracticeSessionCallback(
        private val audioCoordinator: DualAudioCoordinator,
    ) : MediaSession.Callback {
        @androidx.annotation.OptIn(UnstableApi::class)
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(PracticeSessionCommands.BalanceCommand)
                .add(PracticeSessionCommands.CallsMutedCommand)
                .add(PracticeSessionCommands.AutoMuteCommand)
                .add(PracticeSessionCommands.PlaybackDelayCommand)
                .build()
            return MediaSession.ConnectionResult.accept(
                sessionCommands,
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS,
            )
        }

        @androidx.annotation.OptIn(UnstableApi::class)
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                PracticeSessionCommands.SetBalance -> {
                    audioCoordinator.setBalance(args.getFloat(PracticeSessionCommands.Balance, 0.5f))
                }

                PracticeSessionCommands.SetCallsMuted -> {
                    audioCoordinator.setCallsMuted(args.getBoolean(PracticeSessionCommands.CallsMuted, false))
                }

                PracticeSessionCommands.SetAutoMute -> {
                    audioCoordinator.setAutoMute(
                        AutoMuteSettings(
                            muteAfterRepetition = args.positiveIntOrNull(
                                PracticeSessionCommands.MuteAfterRepetition,
                            ),
                            muteWithRepetitionsRemaining = args.positiveIntOrNull(
                                PracticeSessionCommands.MuteWithRepetitionsRemaining,
                            ),
                        ),
                    )
                }

                PracticeSessionCommands.SetPlaybackDelay -> {
                    audioCoordinator.setPlaybackDelay(
                        PlaybackDelaySettings(
                            delaySeconds = args.getInt(PracticeSessionCommands.PlaybackDelaySeconds, 0)
                                .coerceAtLeast(0),
                        ),
                    )
                }

                else -> return Futures.immediateFuture(
                    SessionResult(SessionError.ERROR_NOT_SUPPORTED),
                )
            }

            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        private fun Bundle.positiveIntOrNull(key: String): Int? =
            getInt(key, 0).takeIf { value -> value > 0 }
    }

    private companion object {
        const val SESSION_ID = "promenade_playback"
    }
}
