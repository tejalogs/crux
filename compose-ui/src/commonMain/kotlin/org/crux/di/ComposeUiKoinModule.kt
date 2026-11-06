package org.crux.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module

@Module
@Configuration
@ComponentScan("org.crux.ui.component")
internal class ComposeUiKoinModule
