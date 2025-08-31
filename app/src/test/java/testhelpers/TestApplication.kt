package testhelpers

import android.app.Application

/**
 * Minimal test application for Robolectric tests.
 * Prevents the real app class from being instantiated during unit tests.
 */
class TestApplication : Application() {
    // No initialization - keep tests fast and isolated
}