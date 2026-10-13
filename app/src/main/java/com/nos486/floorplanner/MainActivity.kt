package com.nos486.floorplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.nos486.floorplanner.ui.PlanScreen
import com.nos486.floorplanner.ui.PlanViewModel
import com.nos486.floorplanner.ui.theme.FloorPlannerTheme

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
