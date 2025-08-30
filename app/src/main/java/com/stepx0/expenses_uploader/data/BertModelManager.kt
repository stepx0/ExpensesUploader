package com.stepx0.expenses_uploader.data

import android.content.Context
import com.stepx0.expenses_uploader.model.BertModel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Singleton manager for BERT model to avoid repeated loading/unloading
 */
object BertModelManager {
    private var bertModel: BertModel? = null
    private val mutex = Mutex()

    suspend fun getBertModel(context: Context): BertModel = mutex.withLock {
        if (bertModel == null) {
            bertModel = BertModel(context.applicationContext)
        }
        return bertModel!!
    }

    suspend fun releaseBertModel() = mutex.withLock {
        bertModel?.close()
        bertModel = null
    }

    fun isModelLoaded(): Boolean = bertModel != null
}
