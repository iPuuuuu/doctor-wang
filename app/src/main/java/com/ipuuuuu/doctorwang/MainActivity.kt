package com.ipuuuuu.doctorwang

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import com.ipuuuuu.doctorwang.ui.DoctorWangApp
import com.ipuuuuu.doctorwang.ui.theme.DoctorWangTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DoctorWangTheme {
                DoctorWangApp()
            }
        }
    }
}
