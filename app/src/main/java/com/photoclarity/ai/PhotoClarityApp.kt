package com.photoclarity.ai

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.photoclarity.ai.core.session.ScanCoordinator
import javax.inject.Inject

@HiltAndroidApp
class PhotoClarityApp : Application() {
    @Inject lateinit var scans: ScanCoordinator
    override fun onCreate() {
        super.onCreate()
        scans.initialize()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = scans.setVisible(true)
            override fun onStop(owner: LifecycleOwner) = scans.setVisible(false)
        })
    }
}
