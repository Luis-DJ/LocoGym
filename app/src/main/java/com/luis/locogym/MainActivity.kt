package com.luis.locogym

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.luis.locogym.data.LocoGymDatabase
import com.luis.locogym.ui.LocoGymApp

class MainActivity : ComponentActivity() {
    private val viewModel: LocoGymViewModel by viewModels {
        LocoGymViewModel.Factory(LocoGymDatabase.getInstance(this).exerciseDao())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LocoGymApp(viewModel) }
    }
}
