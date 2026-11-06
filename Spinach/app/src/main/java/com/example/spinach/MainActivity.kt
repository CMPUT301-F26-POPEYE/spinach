package com.example.spinach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.spinach.ui.theme.SpinachTheme//T-for lottery
import com.example.spinach.ui.CreateLotteryScreen//T-for lottery


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            SpinachTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CreateLotteryScreen(
                        campsiteId = "test-campsite-talha",// this is hardcodedm, will need to be changed once all owner UI is complete
                        userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
    override fun onResume() {
        super.onResume()
        LotteryRepository().drawDueLotteries()
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SpinachTheme {
        Greeting("Android")
    }
}