package com.keeltv.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.keeltv.app.data.net.ArtSizeInterceptor
import okio.Path.Companion.toOkioPath

class KeelTvApp : Application(), SingletonImageLoader.Factory {
  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    container = AppContainer(this)
    container.refresh.startGuideScheduler()
    container.refresh.rederiveIfRulesChanged()
    container.refresh.restoreInterruptedLoad()
  }

  override fun newImageLoader(context: PlatformContext): ImageLoader =
    ImageLoader.Builder(context)
      .components {
        add(ArtSizeInterceptor)
        add(OkHttpNetworkFetcherFactory(callFactory = { container.http }))
      }
      .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
      .diskCache {
        DiskCache.Builder()
          .directory(cacheDir.resolve("images").toOkioPath())
          .maxSizeBytes(128L * 1024 * 1024)
          .build()
      }
      .crossfade(true)
      .build()
}
