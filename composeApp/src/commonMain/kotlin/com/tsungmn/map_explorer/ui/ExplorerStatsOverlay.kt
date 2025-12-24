package com.tsungmn.map_explorer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tsungmn.map_explorer.model.BigDouble
import com.tsungmn.map_explorer.viewmodel.ExplorerStatsViewModel

@Composable
fun ExplorerStatsOverlay(
    modifier: Modifier = Modifier,
    viewModel: ExplorerStatsViewModel = viewModel()
) {
    val landPercent by viewModel.landPercent.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = formatLandPercent(landPercent),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White
        )
    }
}

private fun formatLandPercent(value: Double): String {
    val areaPercent = BigDouble(value).value

    return "$areaPercent% of land explored"
}