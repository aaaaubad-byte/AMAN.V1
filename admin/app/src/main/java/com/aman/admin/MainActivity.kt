package com.aman.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.aman.admin.data.AdminRefreshWorker
import com.aman.admin.data.AdminRepository
import com.aman.admin.data.LocalSnapshotCache
import com.aman.admin.data.SupabaseGateway
import com.aman.admin.ui.AdminApp
import com.aman.admin.ui.AdminViewModel
import com.aman.admin.ui.AmanTheme
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val gateway = SupabaseGateway(applicationContext)
        val repository = AdminRepository(gateway, LocalSnapshotCache(applicationContext))
        val viewModel = AdminViewModel(repository)
        val request = PeriodicWorkRequestBuilder<AdminRefreshWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "aman-admin-read-refresh", ExistingPeriodicWorkPolicy.KEEP, request,
        )
        setContent {
            AmanTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AdminApp(viewModel)
                }
            }
        }
    }
}
