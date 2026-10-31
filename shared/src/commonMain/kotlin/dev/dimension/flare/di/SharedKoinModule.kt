package dev.dimension.flare.di

import dev.dimension.flare.data.datasource.instagram.InstagramSyncService
import dev.dimension.flare.data.datasource.linkedin.LinkedInSyncService
import dev.dimension.flare.data.io.FileStorage
import dev.dimension.flare.data.platform.InstagramPlatformSpec
import dev.dimension.flare.data.platform.LinkedInPlatformSpec
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.model.PlatformSpec
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
@Module
@Configuration
@ComponentScan("dev.dimension.flare")
internal class SharedKoinModule {
    @Single(binds = [PlatformSpec::class])
    fun instagramPlatformSpec(): InstagramPlatformSpec = InstagramPlatformSpec

    @Single(binds = [PlatformSpec::class])
    fun linkedInPlatformSpec(): LinkedInPlatformSpec = LinkedInPlatformSpec

    @Single
    fun instagramSyncService(
        accountService: AccountService,
        fileStorage: FileStorage,
    ): InstagramSyncService = InstagramSyncService(accountService, fileStorage)

    @Single
    fun linkedInSyncService(
        accountService: AccountService,
        fileStorage: FileStorage,
    ): LinkedInSyncService = LinkedInSyncService(accountService, fileStorage)

    @Single
    fun cacheEvictionManager(
        cacheDatabase: dev.dimension.flare.data.database.cache.CacheDatabase,
        offlineCacheRepository: dev.dimension.flare.data.cache.OfflineCacheRepository,
    ): dev.dimension.flare.data.cache.CacheEvictionManager =
        dev.dimension.flare.data.cache
            .CacheEvictionManager(cacheDatabase, offlineCacheRepository)
}
