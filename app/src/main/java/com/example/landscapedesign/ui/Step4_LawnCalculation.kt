package com.example.landscapedesign.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.landscapedesign.R
import com.example.landscapedesign.geometry.PolygonClipper
import com.example.landscapedesign.viewmodel.LandscapeViewModel

/**
 * STEP 4 — Net Lawn Area & Seeding Density.
 *
 * Reads the base garden area (Step 1) and every border/tree-zone element
 * configured in Step 3, and applies the strict subtractive hierarchy:
 *   Net Lawn Area = Base Area - (Border Footprint Areas + Tree Zone Areas)
 * via [PolygonClipper.netLawnArea] (already wired into the ViewModel so this
 * screen only needs to read reactive state). The user then picks a lawn
 * seeding density (20-50 shoots/m², +5 steps) and total required shoots is
 * calculated and rounded up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Step4LawnScreen(
    viewModel: LandscapeViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    val densityOptions = remember { (20..50 step 5).toList() }
    var expanded by remember { mutableStateOf(false) }

    val borderArea = remember(state.borders) { PolygonClipper.totalBorderFootprintArea(state.borders) }
    val treeZoneArea = remember(state.shapes) { PolygonClipper.totalTreeZoneArea(state.shapes) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.step4_title)) }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // --- Subtraction breakdown card (transparency for the user) ---
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.reference_area_label, state.gardenAreaM2),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("مساحة المحددات (الأسوار): %.2f م²".format(borderArea))
                    Text("مساحة مناطق الأشجار والمحيطات: %.2f م²".format(treeZoneArea))
                }
            }

            // --- Net lawn area result ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.net_lawn_area_label, state.netLawnAreaM2),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // --- Lawn density dropdown ---
            Text(stringResource(R.string.lawn_density_label), style = MaterialTheme.typography.titleMedium)
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = "${state.lawnDensityPerM2}",
                    onValueChange = {},
                    label = { Text(stringResource(R.string.lawn_density_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    densityOptions.forEach { d ->
                        DropdownMenuItem(
                            text = { Text("$d") },
                            onClick = {
                                viewModel.updateLawnDensity(d)
                                expanded = false
                            }
                        )
                    }
                }
            }

            // --- Total lawn plants result ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.total_lawn_plants_label, state.totalLawnPlants),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(onClick = onBack) { Text(stringResource(R.string.btn_back)) }
                Button(onClick = onNext) { Text(stringResource(R.string.btn_next_step)) }
            }
        }
    }
}
