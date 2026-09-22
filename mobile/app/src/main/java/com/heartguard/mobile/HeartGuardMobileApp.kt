package com.heartguard.mobile

import android.app.Application
import com.heartguard.mobile.service.HeartRateLowAlertCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HeartGuardMobileApp : Application() {

    @Inject lateinit var heartRateLowAlertCoordinator: HeartRateLowAlertCoordinator

    override fun onCreate() {
        super.onCreate()
        heartRateLowAlertCoordinator.start()
    }
}
