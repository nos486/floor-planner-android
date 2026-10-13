package org.geekio.floorplanner

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.geekio.floorplanner.core.Kind
import org.geekio.floorplanner.core.P
import org.geekio.floorplanner.core.PlanOps
import org.geekio.floorplanner.core.Selection
import org.geekio.floorplanner.core.pointInPolygon
import org.geekio.floorplanner.model.Door
import org.geekio.floorplanner.model.PlanWindow
import org.geekio.floorplanner.model.Project
import org.geekio.floorplanner.model.Wall
import org.geekio.floorplanner.ui.PlanScreen
import org.geekio.floorplanner.ui.PlanViewModel
import org.geekio.floorplanner.ui.Tool
import org.geekio.floorplanner.ui.theme.FloorPlannerTheme
import org.junit.Rule
import org.junit.Test

/**
 * Renders the real app screen with a sample apartment for the Play Store listing.
 * Run `./gradlew recordPaparazziDebug`; images land in app/src/test/snapshots/images.
 */
class StoreScreenshots {
    @get:Rule
    val paparazzi = Paparazzi(
        // 9:16 phone screenshot, as Google Play requires an aspect ratio of at most 2:1.
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 1920, screenWidth = 1080),
        // Keep full 1080x1920 instead of Paparazzi's default 1000px downscale.
        useDeviceResolution = true,
    )

    @Test
    fun shot1_plan_overview() = shoot(Tool.SELECT) { null }

    @Test
    fun shot2_wall_selected() = shoot(Tool.SELECT) { Selection(Kind.WALL, "w_mid") }

    @Test
    fun shot3_room_selected_dark() = shoot(Tool.SELECT, dark = true) { p ->
        Selection(Kind.ROOM, p.rooms.first { pointInPolygon(P(600.0, 175.0), it.polygon) }.id)
    }

    @Test
    fun shot4_door_tool() = shoot(Tool.DOOR) { Selection(Kind.DOOR, "d_bed") }

    private fun shoot(tool: Tool, dark: Boolean = false, select: (Project) -> Selection?) {
        val vm = PlanViewModel(Application())
        val plan = samplePlan()
        vm.showPreview(plan, tool)
        vm.showPreview(vm.project, tool, select(vm.project))
        val owner = FakeActivityOwner()
        paparazzi.snapshot {
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides owner,
                LocalOnBackPressedDispatcherOwner provides owner,
            ) {
                FloorPlannerTheme(dark = dark, dynamicColor = false) { PlanScreen(vm) }
            }
        }
    }

    /** An 8 m x 6 m apartment: kitchen, living room, bedroom and bathroom. */
    private fun samplePlan(): Project {
        fun w(id: String, x1: Int, y1: Int, x2: Int, y2: Int, t: Double = 20.0) =
            Wall.create(id, P(x1.toDouble(), y1.toDouble()), P(x2.toDouble(), y2.toDouble()), t)
        val walls = listOf(
            w("w_top", 0, 0, 800, 0),
            w("w_right", 800, 0, 800, 600),
            w("w_bottom", 800, 600, 0, 600),
            w("w_left", 0, 600, 0, 0),
            w("w_mid", 450, 0, 450, 600, 12.0),
            w("w_kitchen", 0, 280, 450, 280, 12.0),
            w("w_bath", 450, 380, 800, 380, 12.0),
        )
        var p = PlanOps.withRooms(
            Project(
                projectName = "Sample apartment",
                walls = walls,
                doors = listOf(
                    Door("d_bed", "w_mid", 250.0, 90.0, side = -1),
                    Door("d_bath", "w_mid", 470.0, 80.0, flipped = true, side = -1),
                    Door("d_main", "w_bottom", 650.0, 100.0, side = -1),
                ),
                windows = listOf(
                    PlanWindow("win_1", "w_top", 220.0, 150.0),
                    PlanWindow("win_2", "w_top", 620.0, 160.0),
                    PlanWindow("win_3", "w_left", 160.0, 160.0),
                ),
            )
        )
        val names = mapOf(
            P(220.0, 140.0) to "Kitchen",
            P(220.0, 440.0) to "Living room",
            P(620.0, 190.0) to "Bedroom",
            P(620.0, 490.0) to "Bathroom",
        )
        for ((pt, name) in names) {
            p.rooms.firstOrNull { pointInPolygon(pt, it.polygon) }?.let { p = PlanOps.renameRoom(p, it.id, name) }
        }
        return p
    }
}

/** Stands in for the Activity so launchers and back handling can be composed. */
private class FakeActivityOwner : OnBackPressedDispatcherOwner, ActivityResultRegistryOwner {
    private val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    override val lifecycle: Lifecycle get() = registry
    override val onBackPressedDispatcher = OnBackPressedDispatcher()
    override val activityResultRegistry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) = Unit
    }
}
