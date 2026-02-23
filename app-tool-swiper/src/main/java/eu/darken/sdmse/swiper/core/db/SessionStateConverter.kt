package eu.darken.sdmse.swiper.core.db

import androidx.room.TypeConverter
import eu.darken.sdmse.swiper.core.SessionState

class SessionStateConverter {
    @TypeConverter
    fun fromValue(value: String): SessionState = SessionState.valueOf(value)

    @TypeConverter
    fun toValue(state: SessionState): String = state.name
}
