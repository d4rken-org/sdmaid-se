object Versions {
    object Kotlin {
        const val core = "2.2.10"
        const val coroutines = "1.10.2"
        const val serialization = "1.8.1"
    }

    object Dagger {
        const val core = "2.59.2"
    }

    object AndroidX {
        object Navigation {
            const val core = "2.9.7"
        }

        object Compose {
            const val bom = "2026.06.00"
        }

        object Glance {
            // 1.2.0-rc01: no stable 1.2.0 exists (train went rc01 → 1.3.0-alpha); the rc is required
            // for the generated widget-picker preview API (providePreview/setWidgetPreviews, A15+).
            const val core = "1.2.0-rc01"
        }

        object Navigation3 {
            const val core = "1.0.1"
            const val lifecycleVm = "2.10.0"
        }

        object Media3 {
            const val core = "1.10.0"
        }
    }

    object Desugar {
        const val core = "2.1.5"
    }

    object Robolectric {
        const val core = "4.16.1"
    }
}
