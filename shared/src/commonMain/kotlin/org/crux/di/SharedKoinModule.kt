package org.crux.di

import org.crux.data.datasource.instagram.InstagramSyncService
import org.crux.data.datasource.linkedin.LinkedInSyncService
import org.crux.data.io.FileStorage
import org.crux.data.platform.InstagramPlatformSpec
import org.crux.data.platform.LinkedInPlatformSpec
import org.crux.data.repository.AccountService
import org.crux.model.PlatformSpec
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
@Module
@Configuration
@ComponentScan("org.crux")
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
        cacheDatabase: org.crux.data.database.cache.CacheDatabase,
        offlineCacheRepository: org.crux.data.cache.OfflineCacheRepository,
    ): org.crux.data.cache.CacheEvictionManager =
        org.crux.data.cache
            .CacheEvictionManager(cacheDatabase, offlineCacheRepository)

    @Single
    fun sendDraftUseCase(
        draftRepository: org.crux.data.repository.DraftRepository,
        accountRepository: org.crux.data.repository.AccountRepository,
        draftMediaStore: org.crux.data.repository.DraftMediaStore,
    ): org.crux.ui.presenter.compose.SendDraftUseCase =
        org.crux.ui.presenter.compose.SendDraftUseCase(
            draftRepository = draftRepository,
            accountRepository = accountRepository,
            draftMediaStore = draftMediaStore,
        )
}
