package com.odin.wms.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.odin.wms.android.ui.navigation.WmsNavGraph
import com.odin.wms.android.ui.theme.WmsAndroidTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WmsAndroidTheme {
                WmsNavGraph()
            }
        }
    }
}
