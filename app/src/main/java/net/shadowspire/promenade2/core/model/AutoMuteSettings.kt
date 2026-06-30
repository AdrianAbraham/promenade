package net.shadowspire.promenade2.core.model

data class AutoMuteSettings(
    val muteAfterRepetition: Int? = null,
    val muteWithRepetitionsRemaining: Int? = null,
) {
    val isEnabled: Boolean
        get() = muteAfterRepetition != null || muteWithRepetitionsRemaining != null
}
