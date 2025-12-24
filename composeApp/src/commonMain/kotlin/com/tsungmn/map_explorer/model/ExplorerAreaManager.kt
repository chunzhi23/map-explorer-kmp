package com.tsungmn.map_explorer.model

expect object ExplorerAreaManager {
    fun exploredAreaMeters(): Double
    fun exploredPercentOfEarth(): Double
    fun exploredPercentOfLand(): Double
}