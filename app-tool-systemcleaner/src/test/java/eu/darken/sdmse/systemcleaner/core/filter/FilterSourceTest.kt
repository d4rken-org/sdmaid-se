package eu.darken.sdmse.systemcleaner.core.filter

import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterConfig
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterLoader
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterRepo
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2

class FilterSourceTest : BaseTest() {

    private fun stubFactory(
        identifier: String,
        enabled: Boolean,
    ): SystemCleanerFilter.Factory {
        val filter = mockk<SystemCleanerFilter>(relaxed = true).apply {
            every { this@apply.identifier } returns identifier
        }
        return mockk<SystemCleanerFilter.Factory>().apply {
            coEvery { isEnabled() } returns enabled
            coEvery { create() } returns filter
        }
    }

    private suspend fun build(
        factories: Set<SystemCleanerFilter.Factory>,
        customConfigs: List<CustomFilterConfig> = emptyList(),
        customFactoryProvider: (CustomFilterConfig) -> SystemCleanerFilter.Factory = { stubFactory("custom-${it.identifier}", true) },
    ): FilterSource {
        val repo = mockk<CustomFilterRepo>().apply {
            every { configs } returns flowOf(customConfigs)
        }
        // Pre-create loaders per config so the factory itself is synchronous.
        val loadersByConfig = customConfigs.associateWith { cfg ->
            val underlying = customFactoryProvider(cfg)
            mockk<CustomFilterLoader>().apply {
                coEvery { isEnabled() } returns underlying.isEnabled()
                coEvery { create() } returns underlying.create()
            }
        }
        val loaderFactory = mockk<CustomFilterLoader.Factory>().apply {
            every { create(any()) } answers {
                loadersByConfig[firstArg<CustomFilterConfig>()]!!
            }
        }
        return FilterSource(
            filterFactories = factories,
            customFilterRepo = repo,
            customFilterLoader = loaderFactory,
        )
    }

    @Test
    fun `create onlyEnabled true skips disabled factories`() = runTest2 {
        val source = build(
            factories = setOf(
                stubFactory("enabled", enabled = true),
                stubFactory("disabled", enabled = false),
            ),
        )

        val filters = source.create(onlyEnabled = true)

        filters.map { it.identifier } shouldBe listOf("enabled")
    }

    @Test
    fun `create onlyEnabled false includes disabled factories`() = runTest2 {
        val source = build(
            factories = setOf(
                stubFactory("enabled", enabled = true),
                stubFactory("disabled", enabled = false),
            ),
        )

        val filters = source.create(onlyEnabled = false)

        filters.map { it.identifier } shouldContainExactlyInAnyOrder listOf("enabled", "disabled")
    }

    @Test
    fun `create calls initialize on every returned filter`() = runTest2 {
        val filterA = mockk<SystemCleanerFilter>(relaxed = true).apply {
            every { identifier } returns "a"
        }
        val filterB = mockk<SystemCleanerFilter>(relaxed = true).apply {
            every { identifier } returns "b"
        }
        val factoryA = mockk<SystemCleanerFilter.Factory>().apply {
            coEvery { isEnabled() } returns true
            coEvery { create() } returns filterA
        }
        val factoryB = mockk<SystemCleanerFilter.Factory>().apply {
            coEvery { isEnabled() } returns true
            coEvery { create() } returns filterB
        }
        val source = build(factories = setOf(factoryA, factoryB))

        source.create(onlyEnabled = true)

        coVerify(exactly = 1) { filterA.initialize() }
        coVerify(exactly = 1) { filterB.initialize() }
    }

    @Test
    fun `create mixes built-in and custom filters`() = runTest2 {
        val builtIn = stubFactory("builtin", enabled = true)
        val customConfig = CustomFilterConfig(
            identifier = "cf1",
            label = "Custom 1",
        )
        val source = build(
            factories = setOf(builtIn),
            customConfigs = listOf(customConfig),
        )

        val filters = source.create(onlyEnabled = true)

        filters.map { it.identifier } shouldContainExactlyInAnyOrder listOf("builtin", "custom-cf1")
    }
}
