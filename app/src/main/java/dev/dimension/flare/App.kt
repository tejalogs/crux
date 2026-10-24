package dev.dimension.flare

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
import dev.dimension.flare.common.AnimatedPngDecoder
import dev.dimension.flare.common.AnimatedWebPDecoder
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.privacy.PrivacySettingsRepository
import dev.dimension.flare.di.AndroidKoinApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.component.get
import org.koin.plugin.module.dsl.startKoin

class App :
    Application(),
    SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        WebView.setDataDirectorySuffix("prismbrowser")
        val koinApplication = startKoin<AndroidKoinApplication> {
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
    }

    @OptIn(ExperimentalCoilApi::class)
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
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
            }.crossfade(true)
            .build()
}
