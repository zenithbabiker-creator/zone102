package com.example.landscapedesign

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.landscapedesign.ui.Step1AreaCaptureScreen
import com.example.landscapedesign.ui.Step2SoilVolumeScreen
import com.example.landscapedesign.ui.Step3DesignStudioScreen
import com.example.landscapedesign.ui.Step4LawnScreen
import com.example.landscapedesign.ui.Step5ReportScreen
import com.example.landscapedesign.ui.theme.LandscapeDesignTheme
import com.example.landscapedesign.viewmodel.LandscapeViewModel

object Routes {
    const val STEP1 = "step1_area"
    const val STEP2 = "step2_soil"
    const val STEP3 = "step3_studio"
    const val STEP4 = "step4_lawn"
    const val STEP5 = "step5_report"
}

class MainActivity : ComponentActivity() {

    // Shared across every step/screen in the flow.
    private val viewModel: LandscapeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LandscapeDesignTheme {
                LandscapeNavHost(viewModel)
            }
        }
    }
}

@Composable
fun LandscapeNavHost(viewModel: LandscapeViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.STEP1, modifier = Modifier) {
        composable(Routes.STEP1) {
            Step1AreaCaptureScreen(
                viewModel = viewModel,
                onConfirmed = { navController.navigate(Routes.STEP2) }
            )
        }
        composable(Routes.STEP2) {
            Step2SoilVolumeScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNext = { navController.navigate(Routes.STEP3) }
            )
        }
        composable(Routes.STEP3) {
            Step3DesignStudioScreen(
                viewModel = viewModel,
                onNext = { navController.navigate(Routes.STEP4) }
            )
        }
        composable(Routes.STEP4) {
            Step4LawnScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNext = { navController.navigate(Routes.STEP5) }
            )
        }
        composable(Routes.STEP5) {
            Step5ReportScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
