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
import com.odin.wms.android.data.local.dao.PickingItemDao
import com.odin.wms.android.data.local.dao.PickingSyncQueueDao
import com.odin.wms.android.data.local.dao.ShippingOrderDao
import com.odin.wms.android.data.local.dao.ShippingPackageDao
import com.odin.wms.android.data.local.entity.PickingSyncQueueEntity
import com.odin.wms.android.data.remote.WmsApiService
import com.odin.wms.android.data.remote.dto.ConfirmPickItemRequestDto
import com.odin.wms.android.data.remote.dto.LoadPackageRequestDto
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@HiltWorker
class PickingSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val apiService: WmsApiService,
    private val pickingSyncQueueDao: PickingSyncQueueDao,
    private val pickingItemDao: PickingItemDao,
    private val shippingOrderDao: ShippingOrderDao,
    private val shippingPackageDao: ShippingPackageDao,
    private val gson: Gson
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val tenantId = inputData.getString(KEY_TENANT_ID) ?: return@withContext Result.failure()

        val pendingItems = pickingSyncQueueDao.getPendingByTenant(tenantId) // FIFO by createdAt
        if (pendingItems.isEmpty()) return@withContext Result.success()

        var hasFailure = false

        for (item in pendingItems) {
            val outcome = processQueueItem(item)
            when (outcome) {
                SyncOutcome.SUCCESS -> {
                    pickingSyncQueueDao.updateStatus(item.id, "SYNCED")
                }
                SyncOutcome.CONFLICT -> {
                    // 404 — permanent conflict, do not retry
                    pickingSyncQueueDao.updateStatus(item.id, "SYNC_CONFLICT")
                    notifyUser(
                        "Conflito de sincronização",
                        "Operação ${item.operationType} não encontrada remotamente."
                    )
                }
                SyncOutcome.RETRY -> {
                    val newRetryCount = item.retryCount + 1
                    if (newRetryCount >= MAX_RETRIES) {
                        pickingSyncQueueDao.updateStatus(item.id, "SYNC_FAILED")
                        notifyUser(
                            "Falha na sincronização",
                            "Operação ${item.operationType} falhou após $MAX_RETRIES tentativas."
                        )
                    } else {
                        pickingSyncQueueDao.incrementRetryCount(item.id)
                        hasFailure = true
                    }
                }
                SyncOutcome.PERMANENT_ERROR -> {
                    pickingSyncQueueDao.updateStatus(item.id, "SYNC_FAILED")
                    notifyUser(
                        "Falha na sincronização",
                        "Operação inválida ${item.operationType} para tarefa ${item.taskId}."
                    )
                }
            }
        }

        pickingSyncQueueDao.deleteSynced()
        if (hasFailure) Result.retry() else Result.success()
    }

    private suspend fun processQueueItem(item: PickingSyncQueueEntity): SyncOutcome {
        return try {
            val response: Response<*> = when (item.operationType) {
                "CONFIRM_PICK" -> {
                    val request = gson.fromJson(item.payload, ConfirmPickItemRequestDto::class.java)
                    val itemId = item.itemId ?: return SyncOutcome.PERMANENT_ERROR
                    apiService.confirmItemPicked(item.taskId, itemId, request)
                }
                "COMPLETE_TASK" -> {
                    apiService.completePickingOrder(item.taskId)
                }
                "LOAD_PACKAGE" -> {
                    val request = gson.fromJson(item.payload, LoadPackageRequestDto::class.java)
                    val packageId = item.itemId ?: return SyncOutcome.PERMANENT_ERROR
                    apiService.loadPackage(item.taskId, packageId, request)
                }
                "COMPLETE_SHIPPING" -> {
                    apiService.completeShippingOrder(item.taskId)
                }
                else -> return SyncOutcome.PERMANENT_ERROR
            }

            when {
                response.isSuccessful -> {
                    // Update local caches after successful sync
                    when (item.operationType) {
                        "CONFIRM_PICK" -> {
                            if (item.itemId != null) {
                                val request = gson.fromJson(item.payload, ConfirmPickItemRequestDto::class.java)
                                pickingItemDao.updateLocalStatusAndQty(item.itemId, "PICKED", request.quantity)
                            }
                        }
                        "COMPLETE_TASK" -> {
                            // Task already marked locally; no additional update needed
                        }
                        "LOAD_PACKAGE" -> {
                            if (item.itemId != null) {
                                shippingPackageDao.updateStatus(item.itemId, "LOADED")
                                val allPackages = shippingPackageDao.getByOrderId(item.taskId)
                                val loadedCount = allPackages.count { it.status == "LOADED" }
                                shippingOrderDao.updateLoadedPackages(item.taskId, loadedCount)
                            }
                        }
                        "COMPLETE_SHIPPING" -> {
                            shippingOrderDao.updateStatus(item.taskId, "COMPLETED")
                        }
                    }
                    SyncOutcome.SUCCESS
                }
                response.code() == 404 -> SyncOutcome.CONFLICT
                response.code() in 400..499 -> SyncOutcome.PERMANENT_ERROR
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
                "Sincronização de Picking",
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

        // FIX QA-8.2-004: AtomicInteger for NOTIFICATION_ID — prevents duplicates in concurrent contexts
        notificationManager.notify(NOTIFICATION_ID.getAndIncrement(), notification)
    }

    private enum class SyncOutcome {
        SUCCESS, CONFLICT, RETRY, PERMANENT_ERROR
    }

    companion object {
        const val TAG = "picking_sync"
        const val KEY_TENANT_ID = "tenant_id"
        private const val MAX_RETRIES = 3
        private const val NOTIFICATION_CHANNEL_ID = "picking_sync_channel"
        // FIX QA-8.2-004: AtomicInteger for NOTIFICATION_ID — not a simple var Int
        private val NOTIFICATION_ID = AtomicInteger(2000)
    }
}
