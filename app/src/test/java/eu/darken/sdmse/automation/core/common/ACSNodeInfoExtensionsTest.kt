package eu.darken.sdmse.automation.core.common

import android.graphics.Rect
import io.kotest.matchers.doubles.shouldBeExactly
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestACSNodeInfo
import testhelpers.TestApplication
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class ACSNodeInfoExtensionsTest : BaseTest() {

    @Test
    fun `distanceTo calculates distance between node centers correctly`() {
        // Node1 at (0, 0) with size 100x50 -> center at (50, 25)
        val node1 = TestACSNodeInfo(bounds = Rect(0, 0, 100, 50))

        // Node2 at (200, 100) with size 100x50 -> center at (250, 125)
        val node2 = TestACSNodeInfo(bounds = Rect(200, 100, 300, 150))

        // Distance between centers: sqrt((250-50)² + (125-25)²) = sqrt(200² + 100²) = sqrt(50000) ≈ 223.61
        val expectedDistance = sqrt(200.0 * 200.0 + 100.0 * 100.0)

        val actualDistance = node1.distanceTo(node2)

        actualDistance shouldBeExactly expectedDistance
    }

    @Test
    fun `distanceTo returns zero for nodes with same center`() {
        val node1 = TestACSNodeInfo(bounds = Rect(0, 0, 100, 100))
        val node2 = TestACSNodeInfo(bounds = Rect(0, 0, 100, 100))

        val distance = node1.distanceTo(node2)

        distance shouldBeExactly 0.0
    }

    @Test
    fun `distanceTo calculates horizontal distance correctly`() {
        // Both nodes have same Y coordinates, different X
        val node1 = TestACSNodeInfo(bounds = Rect(0, 0, 50, 50))    // center: (25, 25)
        val node2 = TestACSNodeInfo(bounds = Rect(100, 0, 150, 50)) // center: (125, 25)

        // Distance should be 125 - 25 = 100
        val distance = node1.distanceTo(node2)

        distance shouldBeExactly 100.0
    }

    @Test
    fun `distanceTo calculates vertical distance correctly`() {
        // Both nodes have same X coordinates, different Y
        val node1 = TestACSNodeInfo(bounds = Rect(0, 0, 50, 50))    // center: (25, 25)
        val node2 = TestACSNodeInfo(bounds = Rect(0, 100, 50, 150)) // center: (25, 125)

        // Distance should be 125 - 25 = 100
        val distance = node1.distanceTo(node2)

        distance shouldBeExactly 100.0
    }

    @Test
    fun `distanceTo calculates diagonal distance correctly`() {
        // Node1 at origin
        val node1 = TestACSNodeInfo(bounds = Rect(0, 0, 2, 2))      // center: (1, 1)
        // Node2 at (3,3) -> (5,5) 
        val node2 = TestACSNodeInfo(bounds = Rect(3, 3, 5, 5))      // center: (4, 4)

        // Distance should be sqrt((4-1)² + (4-1)²) = sqrt(9 + 9) = sqrt(18) ≈ 4.24
        val expectedDistance = sqrt(9.0 + 9.0)
        val distance = node1.distanceTo(node2)

        distance shouldBeExactly expectedDistance
    }

    @Test
    fun `distanceTo works with overlapping nodes`() {
        // Overlapping nodes but different centers
        val node1 = TestACSNodeInfo(bounds = Rect(0, 0, 100, 100))   // center: (50, 50)
        val node2 = TestACSNodeInfo(bounds = Rect(25, 25, 125, 125)) // center: (75, 75)

        // Distance between centers: sqrt((75-50)² + (75-50)²) = sqrt(625 + 625) = sqrt(1250) ≈ 35.36
        val expectedDistance = sqrt(25.0 * 25.0 + 25.0 * 25.0)
        val distance = node1.distanceTo(node2)

        distance shouldBeExactly expectedDistance
    }

    @Test
    fun `distanceTo is symmetric`() {
        val node1 = TestACSNodeInfo(bounds = Rect(0, 0, 50, 50))
        val node2 = TestACSNodeInfo(bounds = Rect(100, 100, 150, 150))

        val distance1to2 = node1.distanceTo(node2)
        val distance2to1 = node2.distanceTo(node1)

        distance1to2 shouldBeExactly distance2to1
    }

    @Test
    fun `distanceTo handles negative coordinates`() {
        val node1 = TestACSNodeInfo(bounds = Rect(-100, -100, -50, -50)) // center: (-75, -75)
        val node2 = TestACSNodeInfo(bounds = Rect(50, 50, 100, 100))      // center: (75, 75)

        // Distance: sqrt((75-(-75))² + (75-(-75))²) = sqrt(150² + 150²) = sqrt(45000) ≈ 212.13
        val expectedDistance = sqrt(150.0 * 150.0 + 150.0 * 150.0)
        val distance = node1.distanceTo(node2)

        distance shouldBeExactly expectedDistance
    }
}