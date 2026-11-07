package org.crux

import android.app.Application
import android.os.Build
import android.webkit.WebView
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import coil3.video.VideoFrameDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath
import org.crux.common.AnimatedPngDecoder
import org.crux.common.AnimatedWebPDecoder
import org.crux.data.network.ktorClient
import org.crux.data.privacy.PrivacySettingsRepository
import org.crux.di.AndroidKoinApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.component.get
import org.koin.plugin.module.dsl.startKoin

class App :
    Application(),
    SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WebView.setDataDirectorySuffix("cruxbrowser")
        }
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        val koinApplication =
            startKoin<AndroidKoinApplication> {
                androidContext(this@App)
            }
        // Prime the privacy policy before any network client can issue a request. The HTTP
        // pipeline reads this synchronously, so leaving it at defaults would let a blocked host
        // through during the first moments after launch.
        koinApplication.koin.get<PrivacySettingsRepository>().let { repository ->
            CoroutineScope(Dispatchers.IO).launch {
                repository.loadOnce()
                repository.observe()
            }
        }
        koinApplication.koin.getOrNull<org.crux.data.cache.CacheEvictionManager>()?.let { evictionManager ->
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    evictionManager.triggerBackgroundEvictionIfDue()
                }
            }
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val offlineRepo =
            runCatching {
                org.koin.core.context.GlobalContext
                    .getOrNull()
                    ?.get<org.crux.data.cache.OfflineCacheRepository>()
            }.getOrNull()
        val maxMb = offlineRepo?.settings?.value?.maxMediaCacheSizeMb ?: 512
        return ImageLoader
            .Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(factory = AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(AnimatedPngDecoder.Factory())
                add(SvgDecoder.Factory())
                add(AnimatedWebPDecoder.Factory())
                add(VideoFrameDecoder.Factory())
                add(
                    KtorNetworkFetcherFactory(
                        httpClient =
                            ktorClient {
                                useDefaultTransformers = false
                            },
                    ),
                )
            }.diskCache {
                coil3.disk.DiskCache
                    .Builder()
                    .directory(cacheDir.resolve("crux_media_cache").toOkioPath())
                    .maxSizeBytes(maxMb.toLong() * 1024L * 1024L)
                    .build()
            }.memoryCache {
                coil3.memory.MemoryCache
                    .Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }.crossfade(true)
            .build()
    }
}
