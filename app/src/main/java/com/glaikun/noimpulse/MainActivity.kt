package com.glaikun.noimpulse

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glaikun.noimpulse.ui.HomeScreen
import com.glaikun.noimpulse.ui.HomeViewModel
import com.glaikun.noimpulse.ui.theme.NoImpulseTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i(MainActivity::class.simpleName, "Creating")
        enableEdgeToEdge()
        setContent {
            NoImpulseTheme {
                val vm: HomeViewModel = hiltViewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                HomeScreen(state)
            }
        }
    }
}
