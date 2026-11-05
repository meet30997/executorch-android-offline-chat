package com.example.executorchdemo.data

import android.content.Context
import java.io.File

data class ModelPaths(
    val modelPtePath: String,
    val tokenizerPath: String
)

sealed interface ModelFileStatus {
    data object Ready : ModelFileStatus
    data class Missing(val missingFiles: List<String>, val expectedDirectory: String) : ModelFileStatus
}

class ModelManager(private val context: Context) {

    // Default target filenames for Llama 3.2 1B INT4
    companion object {
        const val MODEL_FILENAME = "Llama-3.2-1B-Instruct-SpinQuant_INT4_EO8.pte"
        const val TOKENIZER_FILENAME = "tokenizer.model"
    }

    /**
     * Storage directory for model binaries.
     * Path: /sdcard/Android/data/com.example.executorchdemo/files/
     * This directory is accessible via ADB without root permissions.
     */
    private val modelDir: File
        get() = context.getExternalFilesDir(null) ?: context.filesDir

    val modelFile: File
        get() = File(modelDir, MODEL_FILENAME)

    val tokenizerFile: File
        get() = File(modelDir, TOKENIZER_FILENAME)

    /**
     * Inspects storage to confirm both model and tokenizer exist and are non-empty.
     */
    fun checkStatus(): ModelFileStatus {
        val missing = mutableListOf<String>()

        if (!modelFile.exists() || modelFile.length() == 0L) {
            missing.add(MODEL_FILENAME)
        }
        if (!tokenizerFile.exists() || tokenizerFile.length() == 0L) {
            missing.add(TOKENIZER_FILENAME)
        }

        return if (missing.isEmpty()) {
            ModelFileStatus.Ready
        } else {
            ModelFileStatus.Missing(
                missingFiles = missing,
                expectedDirectory = modelDir.absolutePath
            )
        }
    }

    /**
     * Returns verified paths or throws IllegalStateException if files are absent.
     */
    fun getVerifiedPaths(): ModelPaths {
        val status = checkStatus()
        check(status is ModelFileStatus.Ready) {
            "Required model files are missing from ${modelDir.absolutePath}"
        }
        return ModelPaths(
            modelPtePath = modelFile.absolutePath,
            tokenizerPath = tokenizerFile.absolutePath
        )
    }

    fun getStorageDirectoryPath(): String = modelDir.absolutePath
}
