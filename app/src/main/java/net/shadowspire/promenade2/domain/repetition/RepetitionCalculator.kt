package net.shadowspire.promenade2.domain.repetition

import net.shadowspire.promenade2.core.model.RepetitionMark

object RepetitionCalculator {
    fun currentRepetition(
        positionMs: Long,
        repetitions: List<RepetitionMark>,
    ): Int {
        if (repetitions.isEmpty()) {
            return 0
        }

        val positionSeconds = positionMs / MILLIS_PER_SECOND
        return repetitions.indexOfLast { mark -> mark.start.value <= positionSeconds }
            .let { index -> if (index < 0) 0 else index + 1 }
    }

    private const val MILLIS_PER_SECOND = 1_000.0
}
