package com.globalvision.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.rememberNavController
import com.globalvision.tv.core.network.TvRepository
import com.globalvision.tv.navigation.TvNavGraph

@Composable
fun TvApp() {
    val navController = rememberNavController()
    val repository = remember { TvRepository() }
    TvNavGraph(
        navController = navController,
        repository = repository,
    )
}
