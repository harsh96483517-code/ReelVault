package com.reelvault.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache

/** Low-RAM image loader: small memory cache, RGB565, on-disk thumbnail cache. */
class ReelVaultApp : Application(), ImageLoaderFactory {
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
