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
import com.odin.wms.android.data.local.dao.InventoryItemDao
import com.odin.wms.android.data.local.dao.InventorySessionDao
import com.odin.wms.android.data.local.dao.InventorySyncQueueDao
import com.odin.wms.android.data.local.dao.TransferDao
import com.odin.wms.android.data.local.entity.InventorySyncQueueEntity
import com.odin.wms.android.data.remote.WmsApiService
import com.odin.wms.android.data.remote.dto.CountItemRequestDto
import com.odin.wms.android.data.remote.dto.CreateTransferRequestDto
import com.odin.wms.android.data.remote.dto.DoubleCountRequestDto
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@HiltWorker
class InventorySyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val wmsApiService: WmsApiService,
    private val inventorySyncQueueDao: InventorySyncQueueDao,
    private val inventoryItemDao: InventoryItemDao,
    private val inventorySessionDao: InventorySessionDao,
    private val transferDao: TransferDao,
    private val gson: Gson
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val tenantId = inputData.getString(KEY_TENANT_ID) ?: return@withContext Result.failure()

        val pendingItems = inventorySyncQueueDao.getPendingByTenant(tenantId)
        if (pendingItems.isEmpty()) return@withContext Result.success()

        var hasFailure = false

        for (item in pendingItems) {
            val outcome = processQueueItem(item)
            when (outcome) {
                SyncOutcome.SUCCESS -> {
                    inventorySyncQueueDao.updateStatus(item.id, "SYNCED")
                }
                SyncOutcome.IDEMPOTENT -> {
                    // 409 — transfer already exists remotely — treat as SYNCED (idempotency)
                    inventorySyncQueueDao.updateStatus(item.id, "SYNCED")
                }
                SyncOutcome.CONFLICT -> {
                    // 404 — permanent conflict, do not retry
                    inventorySyncQueueDao.updateStatus(item.id, "SYNC_CONFLICT")
                    notifyUser(
                        "Conflito de sincronização",
                        "Operação ${item.operationType} não encontrada remotamente."
                    )
                }
                SyncOutcome.RETRY -> {
                    val newRetryCount = item.retryCount + 1
                    if (newRetryCount >= MAX_RETRIES) {
                        inventorySyncQueueDao.updateStatus(item.id, "SYNC_FAILED")
                        notifyUser(
                            "Falha na sincronização",
                            "Operação ${item.operationType} falhou após $MAX_RETRIES tentativas."
                        )
                    } else {
                        inventorySyncQueueDao.incrementRetryCount(item.id)
                        hasFailure = true
                    }
                }
                SyncOutcome.PERMANENT_ERROR -> {
                    inventorySyncQueueDao.updateStatus(item.id, "SYNC_FAILED")
                    notifyUser(
                        "Falha na sincronização",
                        "Operação inválida ${item.operationType}."
                    )
                }
            }
        }

        inventorySyncQueueDao.deleteSynced()
        if (hasFailure) Result.retry() else Result.success()
    }

    private suspend fun processQueueItem(item: InventorySyncQueueEntity): SyncOutcome {
        return try {
            val response: Response<*>? = when (item.operationType) {
                "COUNT_ITEM" -> {
                    val req = gson.fromJson(item.payload, CountItemRequestDto::class.java)
                    val itemId = item.itemId ?: return SyncOutcome.PERMANENT_ERROR
                    wmsApiService.countItem(item.sessionId, itemId, req)
                }
                "DOUBLE_COUNT" -> {
                    val req = gson.fromJson(item.payload, DoubleCountRequestDto::class.java)
                    val itemId = item.itemId ?: return SyncOutcome.PERMANENT_ERROR
                    wmsApiService.doubleCountItem(item.sessionId, itemId, req)
                }
                "SUBMIT_SESSION" -> {
                    wmsApiService.submitInventorySession(item.sessionId)
                }
                "TRANSFER" -> {
                    val req = gson.fromJson(item.payload, CreateTransferRequestDto::class.java)
                    val createResponse = wmsApiService.createTransfer(req)
                    if (createResponse.isSuccessful) {
                        val transferId = createResponse.body()?.id ?: return SyncOutcome.PERMANENT_ERROR
                        // Update local cache with real ID from server if needed
                        val confirmResponse = wmsApiService.confirmTransfer(transferId)
                        if (confirmResponse.isSuccessful) {
                            transferDao.updateStatus(item.sessionId, "CONFIRMED")
                        }
                        confirmResponse
                    } else {
                        createResponse
                    }
                }
                else -> return SyncOutcome.PERMANENT_ERROR
            }

            when {
                response?.isSuccessful == true -> {
                    // Update local Room caches after successful sync
                    when (item.operationType) {
                        "COUNT_ITEM" -> {
                            if (item.itemId != null) {
                                inventoryItemDao.updateLocalStatus(item.itemId, "COUNTED")
                            }
                        }
                        "SUBMIT_SESSION" -> {
                            inventorySessionDao.updateStatus(item.sessionId, "COMPLETED")
                        }
                    }
                    SyncOutcome.SUCCESS
                }
                response?.code() == 409 -> {
                    // Idempotency: transfer already exists remotely — treat as SYNCED
                    SyncOutcome.IDEMPOTENT
                }
                response?.code() == 404 -> SyncOutcome.CONFLICT
                response?.code() in 400..499 -> SyncOutcome.PERMANENT_ERROR
                else -> SyncOutcome.RETRY // 5xx and network issues
            }
        } catch (e: IOException) {
            SyncOutcome.RETRY
        } catch (e: Exception) {
            SyncOutcome.RETRY
        }
    }

    private fun notifyUser(title: String, message: String) {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Sincronização de Inventário",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        // FIX QA-8.2-004: AtomicInteger for NOTIFICATION_ID — different from PickingSyncWorker(2000)
        notificationManager.notify(NOTIFICATION_ID.getAndIncrement(), notification)
    }

    private enum class SyncOutcome {
        SUCCESS, IDEMPOTENT, CONFLICT, RETRY, PERMANENT_ERROR
    }

    companion object {
        const val TAG = "inventory_sync"
        const val KEY_TENANT_ID = "tenant_id"
        private const val MAX_RETRIES = 3
        private const val NOTIFICATION_CHANNEL_ID = "inventory_sync_channel"
        // FIX QA-8.2-004: AtomicInteger for NOTIFICATION_ID — different from PickingSyncWorker(2000)
        private val NOTIFICATION_ID = AtomicInteger(3000)
    }
}
