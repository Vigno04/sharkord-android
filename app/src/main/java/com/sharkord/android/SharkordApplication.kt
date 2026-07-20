package com.sharkord.android

import android.app.Application
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SharkordApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        setupCrashHandler()
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            saveCrashLog(thread, exception)
            defaultHandler?.uncaughtException(thread, exception)
        }
    }

    private fun saveCrashLog(thread: Thread, exception: Throwable) {
        try {
            val crashesDir = File(filesDir, "crashes")
            if (!crashesDir.exists()) {
                crashesDir.mkdirs()
            }

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val crashFile = File(crashesDir, "crash_$timeStamp.txt")

            val appVersion = try {
                val pInfo = packageManager.getPackageInfo(packageName, 0)
                pInfo.versionName
            } catch (e: Exception) {
                "Unknown"
            }

            PrintWriter(crashFile).use { writer ->
                writer.println("Date: ${Date()}")
                writer.println("App Version: $appVersion")
                writer.println("OS Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                writer.println("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                writer.println("Thread: ${thread.name}")
                writer.println("\n--- Stack Trace ---")
                exception.printStackTrace(writer)
            }

            // Keep only the last 5 crashes
            val crashFiles = crashesDir.listFiles()?.filter { it.name.startsWith("crash_") && it.name.endsWith(".txt") }
            if (crashFiles != null && crashFiles.size > 5) {
                val sortedFiles = crashFiles.sortedBy { it.lastModified() }
                val filesToDelete = sortedFiles.take(sortedFiles.size - 5)
                for (file in filesToDelete) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e("SharkordApplication", "Failed to save crash log", e)
        }
    }
}
