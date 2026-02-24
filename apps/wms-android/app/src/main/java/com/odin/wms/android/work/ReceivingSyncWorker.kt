package com.odin.wms.android.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.odin.wms.android.data.local.dao.ReceivingItemDao
import com.odin.wms.android.data.local.dao.ReceivingOrderDao
import com.odin.wms.android.data.local.dao.ReceivingSyncQueueDao
import com.odin.wms.android.data.local.entity.ReceivingSyncQueueEntity
import com.odin.wms.android.data.remote.WmsApiService
import com.odin.wms.android.data.remote.dto.CompleteReceivingDto
import com.odin.wms.android.data.remote.dto.ConfirmItemRequestDto
import com.odin.wms.android.data.remote.dto.DivergenceRequestDto
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.File

@HiltWorker
class ReceivingSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val apiService: WmsApiService,
    private val syncQueueDao: ReceivingSyncQueueDao,
    private val orderDao: ReceivingOrderDao,
    private val itemDao: ReceivingItemDao,
    private val gson: Gson
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val tenantId = inputData.getString(KEY_TENANT_ID) ?: return@withContext Result.failure()

        val pendingItems = syncQueueDao.getPendingByTenant(tenantId)
        if (pendingItems.isEmpty()) return@withContext Result.success()

        var hasFailure = false

        for (item in pendingItems) {
            val outcome = processQueueItem(item)
            when (outcome) {
                SyncOutcome.SUCCESS        -> syncQueueDao.updateStatus(item.id, "SYNCED", item.retryCount)
                SyncOutcome.CONFLICT       -> {
                    syncQueueDao.updateStatus(item.id, "SYNC_CONFLICT", item.retryCount)
                    notifyUser("Conflito de sincronização", "Operação para ordem ${item.orderId} foi cancelada remotamente.")
                }
                SyncOutcome.RETRY         -> {
                    val newRetryCount = item.retryCount + 1
                    if (newRetryCount >= MAX_RETRIES) {
                        syncQueueDao.updateStatus(item.id, "SYNC_FAILED", newRetryCount)
                        notifyUser("Falha de sincronização", "Operação para ordem ${item.orderId} falhou após $MAX_RETRIES tentativas.")
                    } else {
                        syncQueueDao.updateStatus(item.id, "PENDING", newRetryCount)
                        hasFailure = true
                    }
                }
                SyncOutcome.PERMANENT_ERROR -> {
                    syncQueueDao.updateStatus(item.id, "SYNC_FAILED", item.retryCount)
                    notifyUser("Falha de sincronização", "Operação inválida para ordem ${item.orderId}.")
                }
            }
        }

        syncQueueDao.deleteSynced()
        if (hasFailure) Result.retry() else Result.success()
    }

    private suspend fun processQueueItem(item: ReceivingSyncQueueEntity): SyncOutcome {
        return try {
            val response: Response<*> = when (item.operationType) {
                "CONFIRM" -> {
                    val request = gson.fromJson(item.payload, ConfirmItemRequestDto::class.java)
                    apiService.confirmReceivingItem(item.orderId, item.itemId ?: return SyncOutcome.PERMANENT_ERROR, request)
                }
                "DIVERGENCE" -> {
                    val payloadType = object : TypeToken<Map<String, Any>>() {}.type
                    val payloadMap: Map<String, Any> = gson.fromJson(item.payload, payloadType)
                    val photoPaths = (payloadMap["photoPaths"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    val photos = photoPaths.mapNotNull { path ->
                        val file = File(path)
                        if (file.exists()) file.readText() else null
                    }
                    val request = DivergenceRequestDto(
                        type = payloadMap["type"] as? String ?: "",
                        actualQty = (payloadMap["actualQty"] as? Double)?.toInt() ?: 0,
                        notes = payloadMap["notes"] as? String ?: "",
                        photos = photos
                    )
                    apiService.reportDivergence(item.orderId, item.itemId ?: return SyncOutcome.PERMANENT_ERROR, request)
                }
                "SIGNATURE" -> {
                    val request = gson.fromJson(item.payload, CompleteReceivingDto::class.java)
                    apiService.submitSignature(item.orderId, request)
                }
                "COMPLETE" -> {
                    apiService.completeReceivingOrder(item.orderId)
                }
                else -> return SyncOutcome.PERMANENT_ERROR
            }

            when {
                response.isSuccessful -> {
                    // If divergence, clean up photo files
                    if (item.operationType == "DIVERGENCE") {
                        val payloadType = object : TypeToken<Map<String, Any>>() {}.type
                        val payloadMap: Map<String, Any> = gson.fromJson(item.payload, payloadType)
                        (payloadMap["photoPaths"] as? List<*>)?.filterIsInstance<String>()?.forEach { path ->
                            File(path).delete()
                        }
                    }
                    // Update local caches
                    if (item.operationType == "COMPLETE") {
                        orderDao.updateStatus(item.orderId, "COMPLETED")
                    }
                    if (item.operationType == "CONFIRM" && item.itemId != null) {
                        val request = gson.fromJson(item.payload, ConfirmItemRequestDto::class.java)
                        itemDao.updateLocalStatus(item.itemId, "CONFIRMED", request.quantity)
                    }
                    SyncOutcome.SUCCESS
                }
                response.code() == 404 -> SyncOutcome.CONFLICT
                response.code() in 400..499 -> SyncOutcome.PERMANENT_ERROR
                else -> SyncOutcome.RETRY   // 5xx and network issues
            }
        } catch (e: Exception) {
            SyncOutcome.RETRY
        }
    }

    private fun notifyUser(title: String, message: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Sincronização de Recebimento",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID++, notification)
    }

    private enum class SyncOutcome {
        SUCCESS, CONFLICT, RETRY, PERMANENT_ERROR
    }

    companion object {
        const val WORK_NAME = "receiving_sync"
        const val KEY_TENANT_ID = "tenant_id"
        private const val MAX_RETRIES = 3
        private const val NOTIFICATION_CHANNEL_ID = "receiving_sync_channel"
        private var NOTIFICATION_ID = 1000
    }
}
