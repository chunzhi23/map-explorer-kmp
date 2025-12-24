package com.tsungmn.map_explorer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsungmn.map_explorer.model.ExplorerAreaManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ExplorerStatsViewModel : ViewModel() {

    private val _landPercent = MutableStateFlow(0.0)
    val landPercent: StateFlow<Double> = _landPercent

    fun refresh() {
        viewModelScope.launch(Dispatchers.Default) {
            val value = ExplorerAreaManager.exploredPercentOfLand()
            _landPercent.value = value
        }
    }
}