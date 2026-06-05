package com.geeplayer.di

import com.geeplayer.upnp.core.UpnpStack
import com.geeplayer.upnp.http.UpnpHttpServer
import com.geeplayer.upnp.services.avt.AVTStateManager
import com.geeplayer.upnp.ssdp.SsdpServer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideUpnpStack(): UpnpStack = UpnpStack()

    @Provides
    @Singleton
    fun provideAVTStateManager(): AVTStateManager = AVTStateManager()
}
