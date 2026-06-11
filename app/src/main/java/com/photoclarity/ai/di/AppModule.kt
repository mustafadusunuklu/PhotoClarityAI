package com.photoclarity.ai.di

import android.content.Context
import androidx.room.Room
import com.photoclarity.ai.core.analysis.BurstDetector
import com.photoclarity.ai.core.analysis.PhotoAnalyzer
import com.photoclarity.ai.core.analysis.QualityScorer
import com.photoclarity.ai.core.hash.AverageHasher
import com.photoclarity.ai.core.hash.CryptographicHasher
import com.photoclarity.ai.core.hash.DifferenceHasher
import com.photoclarity.ai.core.hash.HammingDistance
import com.photoclarity.ai.core.hash.PerceptualHasher
import com.photoclarity.ai.core.media.MediaStoreScanner
import com.photoclarity.ai.core.util.BitmapUtils
import com.photoclarity.ai.core.util.StorageUtils
import com.photoclarity.ai.data.local.db.HashCacheDao
import com.photoclarity.ai.data.local.db.PhotoClarityDatabase
import com.photoclarity.ai.data.local.preferences.SettingsDataStore
import com.photoclarity.ai.data.repository.PhotoRepositoryImpl
import com.photoclarity.ai.domain.repository.PhotoRepository
import com.photoclarity.ai.domain.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context

    // ─── Database ────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PhotoClarityDatabase =
        Room.databaseBuilder(context, PhotoClarityDatabase::class.java, "photoclarity.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideHashCacheDao(db: PhotoClarityDatabase): HashCacheDao = db.hashCacheDao()

    // ─── Utils ───────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideBitmapUtils(@ApplicationContext context: Context): BitmapUtils =
        BitmapUtils(context)

    @Provides
    @Singleton
    fun provideStorageUtils(@ApplicationContext context: Context): StorageUtils =
        StorageUtils(context)

    // ─── Hashers ─────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideCryptoHasher(@ApplicationContext context: Context): CryptographicHasher =
        CryptographicHasher(context)

    @Provides
    @Singleton
    fun providePerceptualHasher(@ApplicationContext context: Context, bitmapUtils: BitmapUtils): PerceptualHasher =
        PerceptualHasher(context, bitmapUtils)

    @Provides
    @Singleton
    fun provideAverageHasher(@ApplicationContext context: Context, bitmapUtils: BitmapUtils): AverageHasher =
        AverageHasher(context, bitmapUtils)

    @Provides
    @Singleton
    fun provideDifferenceHasher(@ApplicationContext context: Context, bitmapUtils: BitmapUtils): DifferenceHasher =
        DifferenceHasher(context, bitmapUtils)

    @Provides
    @Singleton
    fun provideHammingDistance(): HammingDistance = HammingDistance()

    // ─── Analysis ────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideQualityScorer(): QualityScorer = QualityScorer()

    @Provides
    @Singleton
    fun provideBurstDetector(): BurstDetector = BurstDetector()

    @Provides
    @Singleton
    fun providePhotoAnalyzer(
        cryptoHasher: CryptographicHasher,
        pHasher: PerceptualHasher,
        aHasher: AverageHasher,
        dHasher: DifferenceHasher,
        hamming: HammingDistance,
        qualityScorer: QualityScorer,
        burstDetector: BurstDetector,
        bitmapUtils: BitmapUtils,
        hashCacheDao: HashCacheDao
    ): PhotoAnalyzer = PhotoAnalyzer(
        cryptoHasher, pHasher, aHasher, dHasher,
        hamming, qualityScorer, burstDetector, bitmapUtils, hashCacheDao
    )

    // ─── Media ───────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideMediaStoreScanner(@ApplicationContext context: Context, storageUtils: StorageUtils): MediaStoreScanner =
        MediaStoreScanner(context, storageUtils)

    // ─── Repositories ────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun providePhotoRepository(
        @ApplicationContext context: Context,
        scanner: MediaStoreScanner,
        storageUtils: StorageUtils
    ): PhotoRepository = PhotoRepositoryImpl(context, scanner, storageUtils)

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository =
        SettingsDataStore(context)
}
