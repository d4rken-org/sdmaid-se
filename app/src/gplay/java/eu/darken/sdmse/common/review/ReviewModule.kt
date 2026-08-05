package eu.darken.sdmse.common.review

import android.content.Context
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReviewModule {

    @Binds
    @Singleton
    abstract fun reviewTool(tool: GplayReviewTool): ReviewTool

    companion object {
        @Provides
        @Singleton
        fun reviewManager(@ApplicationContext context: Context): ReviewManager = ReviewManagerFactory.create(context)
    }
}
