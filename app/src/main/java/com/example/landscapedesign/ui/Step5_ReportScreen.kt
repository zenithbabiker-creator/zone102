package com.example.landscapedesign.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.landscapedesign.R
import com.example.landscapedesign.report.ReportGenerator
import com.example.landscapedesign.viewmodel.LandscapeViewModel

/**
 * STEP 5 — Automated Technical Report Generator.
 *
 * Compiles the entire [com.example.landscapedesign.model.DesignLayoutState]
 * (Steps 1-4) into a single detailed Arabic narrative via
 * [ReportGenerator.generateFinalLandscapeReport], displayed in a scrollable
 * text card that updates live whenever any underlying design parameter
 * changes (state is a StateFlow, so recomposition happens automatically).
 */
@Composable
fun Step5ReportScreen(
    viewModel: LandscapeViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // Recomputed on every recomposition driven by state changes -> always up to date.
    val report = remember(state) { ReportGenerator.generateFinalLandscapeReport(state) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.step5_title)) }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // --- Scrollable report text ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = report,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // --- Actions ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        copyToClipboard(context, report)
                        Toast.makeText(
                            context,
                            context.getString(R.string.report_copied_toast),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Text(stringResource(R.string.btn_copy_report))
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        saveDesignDataLocally(context, state)
                        Toast.makeText(
                            context,
                            context.getString(R.string.design_saved_toast),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Text(stringResource(R.string.btn_save_design))
                }
            }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onBack
            ) {
                Text(stringResource(R.string.btn_back))
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("landscape_report", text)
    clipboard.setPrimaryClip(clip)
}

/**
 * Persists the raw design parameters (not just the narrative text) to local
 * app storage as JSON-ish key/value pairs via SharedPreferences, so the
 * design can be reopened or fed into a downstream generative-AI rendering
 * stage without re-parsing the Arabic report text.
 */
private fun saveDesignDataLocally(
    context: Context,
    state: com.example.landscapedesign.model.DesignLayoutState
) {
    val prefs = context.getSharedPreferences("landscape_design_data", Context.MODE_PRIVATE)
    prefs.edit().apply {
        putFloat("garden_area_m2", state.gardenAreaM2)
        putInt("soil_thickness_cm", state.soilThicknessCm)
        putFloat("soil_volume_m3", state.soilVolumeM3)
        putFloat("net_lawn_area_m2", state.netLawnAreaM2)
        putInt("lawn_density_per_m2", state.lawnDensityPerM2)
        putInt("total_lawn_plants", state.totalLawnPlants)
        putInt("royal_palm_count", state.royalPalmCount)
        putInt("no_thorn_count", state.noThornCount)
        apply()
    }
}
