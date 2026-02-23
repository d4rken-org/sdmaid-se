package eu.darken.sdmse.swiper.core.db

import androidx.room.TypeConverter
import eu.darken.sdmse.swiper.core.SwipeDecision

class SwipeDecisionConverter {
    @TypeConverter
    fun fromValue(value: String): SwipeDecision = SwipeDecision.valueOf(value)

    @TypeConverter
    fun toValue(decision: SwipeDecision): String = decision.name
}
