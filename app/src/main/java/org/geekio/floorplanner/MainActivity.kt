package org.geekio.floorplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import org.geekio.floorplanner.ui.PlanScreen
import org.geekio.floorplanner.ui.PlanViewModel
import org.geekio.floorplanner.ui.theme.FloorPlannerTheme

class MainActivity : ComponentActivity() {
    private val vm: PlanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FloorPlannerTheme {
                PlanScreen(vm)
            }
        }
    }
}
