package eu.darken.sdmse.common.donate

import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface DonateRepo {
    val mainWebsite: String

    val donateInfo: Flow<Info>

    suspend fun refresh()

    interface Info {
        val donations: List<Donation>
        val type: Type
    }

    interface Donation {
        val amount: Double
        val currency: String
        val donatedAt: Instant
    }

    enum class Type {
        GPLAY,
        FOSS
    }
}