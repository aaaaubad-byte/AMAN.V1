package com.aman.customer.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class CustomerRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val gateway = SupabaseGateway(applicationContext)
        val userId = gateway.currentUserId() ?: return Result.success()
        val repository = CustomerRepository(applicationContext, gateway, CustomerCache(applicationContext))
        try {
            repository.queuedPurchases(userId).filter { it.optString("status") == "queued" }.forEach { request ->
                try {
                    repository.submitPurchase(request.optString("package_id"), request.optString("payment_method_id"),
                        request.optString("payment_reference"), request.optString("idempotency_key"))
                    repository.removeQueuedPurchase(userId, request.optString("idempotency_key"))
                } catch (e: CancellationException) { throw e }
                catch (e: CustomerBackendException) { repository.markQueuedPurchaseNeedsAttention(userId, request.optString("idempotency_key")) }
            }
            listOf(CustomerScreen.HOME, CustomerScreen.ACTIVE_NUMBERS, CustomerScreen.INACTIVE_NUMBERS,
                CustomerScreen.POINTS, CustomerScreen.OPERATIONS, CustomerScreen.ADMIN_ALERTS,
                CustomerScreen.NOTIFICATIONS, CustomerScreen.ACCOUNT).forEach { repository.load(it) }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { return Result.retry() }
        return Result.success()
    }
}

fun scheduleCustomerRefresh(context: Context) {
    val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    val request = PeriodicWorkRequestBuilder<CustomerRefreshWorker>(6, TimeUnit.HOURS)
        .setConstraints(constraints).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork("aman-customer-refresh", ExistingPeriodicWorkPolicy.KEEP, request)
}

fun scheduleCustomerOneTimeRefresh(context: Context) {
    val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    val request = OneTimeWorkRequestBuilder<CustomerRefreshWorker>().setConstraints(constraints).build()
    WorkManager.getInstance(context).enqueueUniqueWork("aman-customer-refresh-now", ExistingWorkPolicy.KEEP, request)
}
