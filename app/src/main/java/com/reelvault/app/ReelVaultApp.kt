package com.reelvault.app

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File

class ReelVaultApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        AppLock.init(this)
        SafeMode.on = getSharedPreferences("rv", MODE_PRIVATE).getBoolean("safe", false)
        // Save any crash so the next launch can show it (no PC / logcat needed).
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { File(filesDir, "crash.txt").writeText(Log.getStackTraceString(e)) }
            previous?.uncaughtException(t, e)
        }
    }

    /** Low-RAM image loader: small memory cache, RGB565, on-disk thumbnail cache. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(VideoFrameDecoder.Factory()) }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.10).build() }
        .diskCache {
            DiskCache.Builder().directory(cacheDir.resolve("thumbs")).maxSizeBytes(150L * 1024 * 1024).build()
        }
        .allowRgb565(true)
        .crossfade(true)
        .build()
}
