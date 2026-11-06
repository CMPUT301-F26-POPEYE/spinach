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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp

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

                var selectedRole by remember {
                    mutableStateOf("Owner")
                }


                //Current owner/entrant navigation is just a role swithc
                //both screens functional but final integration needed
                //for consistent user identiity, owner authorization , access to all entrant features. NEEDS WORK
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { selectedRole = "Owner" },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Owner")
                            }

                            OutlinedButton(
                                onClick = { selectedRole = "Entrant" },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Entrant")
                            }
                        }

                        if (selectedRole == "Owner") {
                            OwnerApp(
                                ownerId = ownerId,
                                campsites = campsites,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            SearchScreen(
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        LotteryRepository().drawDueLotteries()
    }
}