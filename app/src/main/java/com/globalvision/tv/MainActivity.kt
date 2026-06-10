package com.globalvision.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.globalvision.tv.ui.theme.GlobalVisionTvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GlobalVisionTvTheme {
                TvApp()
            }
        }
    }
}
