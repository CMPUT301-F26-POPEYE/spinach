package com.example.spinach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.spinach.ui.AdminApp
import com.example.spinach.ui.EntrantApp
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
                val ownerId = user?.id.orEmpty()

                var campsites by remember { mutableStateOf(emptyList<Campsite>()) }
                LaunchedEffect(ownerId) {
                    campsites = emptyList()
                    if (ownerId.isNotBlank()) CampsiteRepository().ownerCampsites { campsites = it }
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
                        // Saved so the role is still selected on the next launch.
                        fun switchRole(role: User.Role) {
                            showProfile = false
                            if (role != current.activeRole && current.switchRole(role)) {
                                activeRole = role
                                UserRepository().save(current)
                            }
                        }

                        BackHandler(enabled = showProfile) { showProfile = false }
                        Column(modifier = modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Bookers have one role, so they have nothing to switch to.
                                val roles = listOf(User.Role.OWNER, User.Role.ENTRANT, User.Role.ADMIN)
                                    .filter { current.hasRole(it) }
                                if (roles.size > 1) {
                                    roles.forEach { role ->
                                        OutlinedButton(
                                            onClick = { switchRole(role) },
                                            contentPadding = PaddingValues(horizontal = 4.dp),
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text(role.name.lowercase().replaceFirstChar { it.uppercase() })
                                        }
                                    }
                                }
                                TextButton(onClick = { showProfile = !showProfile }) {
                                    Text(if (showProfile) "Back to app" else "Profile")
                                }
                            }
                            when {
                                showProfile -> UserProfileOptions(
                                    user = current,
                                    deviceId = deviceId,
                                    onRoleChanged = { activeRole = it },
                                    onLoggedOut = { user = null },
                                    onProfileDeleted = { user = null },
                                    modifier = Modifier.weight(1f),
                                )
                                activeRole == User.Role.OWNER -> OwnerApp(
                                    ownerId = current.id,
                                    campsites = campsites,
                                    modifier = Modifier.weight(1f),
                                )
                                // US 03.01.01 to 03.08.01, 03.12.01
                                activeRole == User.Role.ADMIN -> AdminApp(modifier = Modifier.weight(1f))
                                // Search, lotteries, reservations and the policy screen
                                else -> EntrantApp(entrantId = current.id, modifier = Modifier.weight(1f))
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
