package com.example.spinach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.spinach.ui.OwnerApp
import com.example.spinach.ui.theme.SpinachTheme
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpinachTheme {
                val auth = FirebaseAuth.getInstance()
                var ownerId by remember { mutableStateOf(auth.currentUser?.uid ?: "") }

                // Temporary sign-in until the device identity story (US 01.09.01) is in
                LaunchedEffect(Unit) {
                    if (auth.currentUser == null) {
                        auth.signInAnonymously().addOnCompleteListener {
                            ownerId = auth.currentUser?.uid ?: ""
                        }
                    }
                }

                // Temporary until Ali's CampsiteRepository.ownerCampsites is on main.
                // Then replace this with a state list filled by ownerCampsites { campsites = it }.
                var campsites by remember { mutableStateOf(emptyList<Campsite>()) }
                LaunchedEffect(ownerId) {
                    if (ownerId.isNotBlank()) CampsiteRepository().ownerCampsites { campsites = it }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    OwnerApp(
                        ownerId = ownerId,
                        campsites = campsites,
                        modifier = Modifier.padding(innerPadding),
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