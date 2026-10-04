package com.aman.admin.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Periodic read-only refresh. Mutations are deliberately excluded. */
class AdminRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val gateway = SupabaseGateway(applicationContext)
        val cache = LocalSnapshotCache(applicationContext)
        val repository = AdminRepository(gateway, cache)
        if (!gateway.isConfigured()) return Result.success()
        if (gateway.currentUserId() == null) {
            cache.clear()
            return Result.success()
        }
        return try {
            if (!repository.verifyAdmin()) {
                repository.clearLocalSession()
                cache.clear()
                return Result.success()
            }
            listOf(
                AdminSection.HOME,
                AdminSection.SUBSCRIBERS,
                AdminSection.USERS,
                AdminSection.ADDED_NUMBERS,
                AdminSection.ACTIVE_NUMBERS,
                AdminSection.PURCHASES,
                AdminSection.PAYMENT_TASKS,
                AdminSection.PROVIDERS,
                AdminSection.PACKAGES,
                AdminSection.PAYMENT_METHODS,
                AdminSection.TASK_SETTINGS,
                AdminSection.NOTIFICATIONS,
                AdminSection.REPORTS,
                AdminSection.ACCOUNT,
            ).forEach { section -> runCatching { repository.load(section) } }
            Result.success()
        } catch (error: BackendResponseException) {
            when {
                error.statusCode == 401 -> { repository.clearLocalSession(); Result.success() }
                error.statusCode >= 500 -> Result.retry()
                else -> Result.success()
            }
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
