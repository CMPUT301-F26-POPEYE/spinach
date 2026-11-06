package com.example.spinach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.spinach.ui.AdminApp
import com.example.spinach.ui.EntrantApp
import com.example.spinach.ui.OwnerApp
import com.example.spinach.ui.theme.SpinachTheme
import com.google.firebase.auth.FirebaseAuth

// US 03.11.01: one person can use the app as an entrant, an owner, and an admin if they are one.
private enum class Role { ENTRANT, OWNER, ADMIN }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpinachTheme {
                val auth = FirebaseAuth.getInstance()
                var userId by remember { mutableStateOf(auth.currentUser?.uid ?: "") }

                // Device sign-in, no username or password (US 01.09.01)
                LaunchedEffect(Unit) {
                    if (auth.currentUser == null) {
                        auth.signInAnonymously().addOnCompleteListener {
                            userId = auth.currentUser?.uid ?: ""
                        }
                    }
                }

                // Campsites registered by this owner (Ali's CampsiteRepository)
                var campsites by remember { mutableStateOf(emptyList<Campsite>()) }
                LaunchedEffect(userId) {
                    if (userId.isNotBlank()) CampsiteRepository().ownerCampsites { campsites = it }
                }

                // An admin is a user with a document at admins/{userId}
                var isAdmin by remember { mutableStateOf(false) }
                LaunchedEffect(userId) {
                    if (userId.isNotBlank()) AdminRepository().isAdmin(userId) { isAdmin = it }
                }

                var role by remember { mutableStateOf(Role.ENTRANT) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            RoleButton("Entrant", role == Role.ENTRANT) { role = Role.ENTRANT }
                            RoleButton("Owner", role == Role.OWNER) { role = Role.OWNER }
                            if (isAdmin) RoleButton("Admin", role == Role.ADMIN) { role = Role.ADMIN }
                        }
                        when {
                            userId.isBlank() -> Text("Signing in...", modifier = Modifier.padding(16.dp))
                            role == Role.OWNER -> OwnerApp(
                                ownerId = userId,
                                campsites = campsites,
                                modifier = Modifier.weight(1f),
                            )
                            role == Role.ADMIN && isAdmin -> AdminApp(modifier = Modifier.weight(1f))
                            else -> EntrantApp(entrantId = userId, modifier = Modifier.weight(1f))
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

@Composable
private fun RoleButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}
