package testhelpers

import eu.darken.sdmse.common.datastore.DataStoreValue
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf

fun <T> mockDataStoreValue(value: T) = mockk<DataStoreValue<T>>().apply {
    every { flow } returns flowOf(value)
}