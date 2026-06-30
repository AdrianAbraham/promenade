package net.shadowspire.promenade2.data.library

import net.shadowspire.promenade2.core.model.FolderRef

data class FolderSummary(
    val ref: FolderRef,
    val available: Boolean,
    val itemCount: Int,
)
