package com.heartguard.mobile.ui.dashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.heartguard.mobile.ui.contacts.ContactsScreen
import com.heartguard.mobile.ui.theme.HeartGuardMobileTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            HeartGuardMobileTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var showContacts by remember { mutableStateOf(false) }

                    if (showContacts) {
                        ContactsScreen(onBack = { showContacts = false })
                    } else {
                        DashboardScreen(
                            onNavigateToContacts = { showContacts = true }
                        )
                    }
                }
            }
        }
    }
}
