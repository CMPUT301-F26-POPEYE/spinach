package com.example.spinach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.spinach.ui.OwnerApp
import com.example.spinach.ui.UserLoginScreen
import com.example.spinach.ui.UserProfileOptions
import com.example.spinach.ui.theme.SpinachTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpinachTheme {
                val context = LocalContext.current
                val deviceId = remember { User.readDeviceId(context) }
                var user by remember { mutableStateOf<User?>(null) }
                var activeRole by remember { mutableStateOf(User.Role.ENTRANT) }
                var showProfile by remember { mutableStateOf(false) }

                // Temporary until Ali's CampsiteRepository.ownerCampsites is on main.
                // Then replace this with a state list filled by ownerCampsites { campsites = it }.
                val campsites = remember {
                    listOf(Campsite(id = "test-campsite-talha", name = "Test campsite"))
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val modifier = Modifier.padding(innerPadding)
                    val current = user
                    if (current == null) {
                        UserLoginScreen(
                            onLoggedIn = {
                                user = it
                                activeRole = it.activeRole
                                showProfile = false
                            },
                            modifier = modifier,
                        )
                    } else {
                        // Only owners have a screen so far, so entrants see their profile.
                        val onOwnerApp = activeRole == User.Role.OWNER && !showProfile
                        BackHandler(enabled = showProfile) { showProfile = false }
                        Column(modifier = modifier.fillMaxSize()) {
                            if (activeRole == User.Role.OWNER) {
                                TextButton(
                                    onClick = { showProfile = !showProfile },
                                    modifier = Modifier.padding(start = 8.dp),
                                ) {
                                    Text(if (showProfile) "Back to app" else "Profile")
                                }
                            }
                            if (onOwnerApp) {
                                OwnerApp(
                                    ownerId = current.id,
                                    campsites = campsites,
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                UserProfileOptions(
                                    user = current,
                                    deviceId = deviceId,
                                    onRoleChanged = { activeRole = it },
                                    onLoggedOut = { user = null },
                                    onProfileDeleted = { user = null },
                                    modifier = Modifier.weight(1f),
                                )
                            }
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
