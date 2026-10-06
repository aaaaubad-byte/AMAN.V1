package com.aman.customer.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class CustomerRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val gateway = SupabaseGateway(applicationContext)
        if (!gateway.hasSession()) return Result.success()
        val repository = CustomerRepository(applicationContext, gateway, CustomerCache(applicationContext))
        try {
            listOf(
                CustomerScreen.HOME,
                CustomerScreen.BUY_POINTS,
                CustomerScreen.POINTS_HISTORY,
                CustomerScreen.ADDED_NUMBERS,
                CustomerScreen.ACTIVE_NUMBERS,
                CustomerScreen.EXPIRED_NUMBERS,
                CustomerScreen.NOTIFICATIONS,
                CustomerScreen.SUPPORT,
                CustomerScreen.ACCOUNT,
            ).forEach { repository.load(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return Result.retry()
        }
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
