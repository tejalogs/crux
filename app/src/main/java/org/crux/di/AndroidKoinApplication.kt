package org.crux.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module

@KoinApplication
internal class AndroidKoinApplication

@Module
@Configuration
@ComponentScan("org.crux.common", "org.crux.di")
internal class AndroidKoinModule
