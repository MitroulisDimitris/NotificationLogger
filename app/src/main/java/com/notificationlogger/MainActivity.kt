package com.notificationlogger

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.notificationlogger.service.NotificationLoggerService
import com.notificationlogger.ui.MainViewModel
import com.notificationlogger.util.BiometricHelper
import com.notificationlogger.util.PrefsHelper

class MainActivity : AppCompatActivity() {

    lateinit var viewModel: MainViewModel
    private lateinit var prefs: PrefsHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PrefsHelper(this)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        if (prefs.isBiometricEnabled() && BiometricHelper.isAvailable(this)) {
            authenticateThenSetup()
        } else {
            setupNavigation()
            checkNotificationPermission()
        }
    }

    private fun authenticateThenSetup() {
        BiometricHelper.prompt(
            activity = this,
            onSuccess = {
                setupNavigation()
                checkNotificationPermission()
            },
            onFail = {
                Toast.makeText(this, "Authentication failed. Closing app.", Toast.LENGTH_LONG).show()
                finish()
            }
        )
    }

    private fun setupNavigation() {
        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setupWithNavController(navController)
    }

    private fun checkNotificationPermission() {
        if (!isNotificationListenerEnabled()) {
            AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage(
                    "Notification Logger needs access to your notifications.\n\n" +
                    "On the next screen, find \"Notification Logger\" and enable it."
                )
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                .setNegativeButton("Not Now", null)
                .show()
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val cn = ComponentName(this, NotificationLoggerService::class.java)
        return flat.split(":").any {
            try { ComponentName.unflattenFromString(it) == cn } catch (e: Exception) { false }
        }
    }
}
