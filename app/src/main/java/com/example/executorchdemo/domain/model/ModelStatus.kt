package com.example.executorchdemo.domain.model

sealed interface ModelStatus {
    data object Uninitialized : ModelStatus
    data class MissingFiles(val missingFiles: List<String>, val targetDir: String) : ModelStatus
    data object Loading : ModelStatus
    data object Ready : ModelStatus
    data class Error(val message: String) : ModelStatus
}
