package evgeniy.kurinnoy.musicdownloader.di

import com.github.kiulian.downloader.YoutubeDownloader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object UtilsModule {

    @Provides
    fun provideYoutubeDownloader(): YoutubeDownloader {
        return YoutubeDownloader()
    }
}