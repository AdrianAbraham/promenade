package net.shadowspire.promenade2.domain.balance

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class ChannelVolumes(
    val music: Float,
    val calls: Float,
)

object BalanceCurve {
    fun volumes(
        balance: Float,
        callsMuted: Boolean,
    ): ChannelVolumes {
        val clamped = balance.coerceIn(0f, 1f)
        val angle = clamped * PI.toFloat() / 2f
        return ChannelVolumes(
            music = cos(angle),
            calls = if (callsMuted) 0f else sin(angle),
        )
    }
}
