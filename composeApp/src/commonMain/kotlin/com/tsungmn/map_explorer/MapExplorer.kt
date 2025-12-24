package com.tsungmn.map_explorer

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.tsungmn.map_explorer.ui.MapContainer
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun MapExplorer() {
    MaterialTheme {
        MapContainer()
    }
}