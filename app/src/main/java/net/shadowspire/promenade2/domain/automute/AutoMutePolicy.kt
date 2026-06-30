package net.shadowspire.promenade2.domain.automute

import net.shadowspire.promenade2.core.model.AutoMuteSettings

object AutoMutePolicy {
    fun shouldMute(
        currentRepetition: Int,
        totalRepetitions: Int,
        settings: AutoMuteSettings,
    ): Boolean {
        val muteAfter = settings.muteAfterRepetition
        if (muteAfter != null && currentRepetition >= muteAfter) {
            return true
        }

        val muteRemaining = settings.muteWithRepetitionsRemaining
        if (muteRemaining != null && totalRepetitions > 0) {
            val remaining = (totalRepetitions - currentRepetition).coerceAtLeast(0)
            if (remaining <= muteRemaining) {
                return true
            }
        }

        return false
    }
}
