package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.example.ui.ExperimentScreen
import com.example.ui.FocusExperimentScreen
import com.example.ui.LensExperimentScreen
import com.example.ui.V4ExperimentScreen
import com.example.ui.theme.MyApplicationTheme

import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import android.util.Log
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    if (OpenCVLoader.initLocal()) {
        Log.i("OpenCV", "OpenCV initialized: YES")
        Log.i("OpenCV", "OpenCV version: ${Core.VERSION}")
        val mat = org.opencv.core.Mat()
        Log.i("OpenCV", "OpenCV Mat creation works: ${!mat.empty() || mat.empty()}")
    } else {
        Log.e("OpenCV", "OpenCV initialized: NO")
    }

    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        var currentTab by remember { mutableStateOf(4) }
        
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { currentTab = 1 },
                    modifier = Modifier.weight(1f),
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentTab == 1) Color(0xFFD0BCFF) else Color(0xFF2B2930),
                        contentColor = if (currentTab == 1) Color(0xFF381E72) else Color.White
                    )
                ) { Text("V1.1") }
                
                Button(
                    onClick = { currentTab = 2 },
                    modifier = Modifier.weight(1f),
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentTab == 2) Color(0xFFD0BCFF) else Color(0xFF2B2930),
                        contentColor = if (currentTab == 2) Color(0xFF381E72) else Color.White
                    )
                ) { Text("V2") }
                Button(
                    onClick = { currentTab = 3 },
                    modifier = Modifier.weight(1f),
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentTab == 3) Color(0xFFD0BCFF) else Color(0xFF2B2930),
                        contentColor = if (currentTab == 3) Color(0xFF381E72) else Color.White
                    )
                ) { Text("V3 LENS") }
                Button(
                    onClick = { currentTab = 4 },
                    modifier = Modifier.weight(1f),
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentTab == 4) Color(0xFFD0BCFF) else Color(0xFF2B2930),
                        contentColor = if (currentTab == 4) Color(0xFF381E72) else Color.White
                    )
                ) { Text("V4") }
            }
            
            androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
              when (currentTab) {
                  1 -> ExperimentScreen()
                  2 -> FocusExperimentScreen()
                  3 -> LensExperimentScreen()
                  4 -> V4ExperimentScreen()
              }
            }
          }
        }
      }
    }
  }
}


