package com.dshremote.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dshremote.app.ui.DshApp
import com.dshremote.app.ui.DshTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DshTheme {
                val viewModel: DshViewModel = viewModel()
                DshApp(viewModel)
            }
        }
    }
}
