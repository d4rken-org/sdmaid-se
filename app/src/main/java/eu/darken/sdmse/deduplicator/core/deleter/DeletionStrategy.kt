package eu.darken.sdmse.deduplicator.core.deleter

data class DeletionStrategy(
    val criteria: List<Criterium> = listOf(
        Criterium.MediaProvider(),
        Criterium.StorageLocation(),
        Criterium.Nesting(),
        Criterium.LastModified(),
    )
) {
    sealed interface Criterium {

        object DeleteAll : Criterium

        data class MediaProvider(
            val mode: Mode = Mode.KEEP_INDEXED
        ) : Criterium {
            enum class Mode {
                KEEP_INDEXED,
                KEEP_UNKNOWN,
            }
        }

        data class StorageLocation(
            val mode: Mode = Mode.KEEP_PRIMARY
        ) : Criterium {
            enum class Mode {
                KEEP_PRIMARY,
                KEEP_SECONDARY,
            }
        }

        data class Nesting(
            val mode: Mode = Mode.KEEP_SHALLOW
        ) : Criterium {
            enum class Mode {
                KEEP_SHALLOW,
                KEEP_DEEPER,
            }
        }

        data class LastModified(
            val mode: Mode = Mode.KEEP_OLDER
        ) : Criterium {
            enum class Mode {
                KEEP_OLDER,
                KEEP_NEWER,
            }
        }
    }

}