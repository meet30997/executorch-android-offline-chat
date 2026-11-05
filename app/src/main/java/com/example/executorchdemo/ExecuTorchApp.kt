package com.example.executorchdemo

import android.app.Application
import android.util.Log
import com.facebook.soloader.SoLoader

class ExecuTorchApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initNativeLibraries()
    }

    private fun initNativeLibraries() {
        try {
            // Initialize Meta SoLoader for JNI library resolution
            SoLoader.init(this, /* nativeExopackage = */ false)
            Log.i(TAG, "SoLoader initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SoLoader", e)
        }
    }

    companion object {
        private const val TAG = "ExecuTorchApp"
    }
}
