package com.stepx0.expenses_uploader.data

import android.content.Context
import com.stepx0.expenses_uploader.model.BertModel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Singleton manager for BERT model to avoid repeated loading/unloading
 */
object BertModelManager {
    private var _bertModel: BertModel? = null
    private val mutex = Mutex()

    suspend fun getBertModel(context: Context): BertModel = mutex.withLock {
        _bertModel ?: BertModel.load(context).also { _bertModel = it }
    }

    suspend fun releaseBertModel() = mutex.withLock {
        _bertModel?.close()
        _bertModel = null
    }

    fun isModelLoaded(): Boolean = _bertModel != null
}
