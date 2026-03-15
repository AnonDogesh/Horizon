package com.horizonweb.di

import com.horizonweb.browser.TabManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
object TabManagerModule {
    @Provides
    @ViewModelScoped
    fun provideTabManager(tabManager: TabManager): TabManager = tabManager
}
