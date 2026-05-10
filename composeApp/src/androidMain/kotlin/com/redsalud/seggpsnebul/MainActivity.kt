package com.redsalud.seggpsnebul

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.redsalud.seggpsnebul.data.local.DatabaseDriverFactory
import com.redsalud.seggpsnebul.domain.model.User
import com.redsalud.seggpsnebul.domain.model.UserRole
import com.redsalud.seggpsnebul.location.DeviceIdProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startGpsService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DeviceIdProvider.init(this)
        AppContainer.init(applicationContext, DatabaseDriverFactory(this))
        enableEdgeToEdge()
        setContent {
            App()
        }
        observeCurrentUser()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(Intent(this, GpsTrackingService::class.java))
    }

    private fun observeCurrentUser() {
        scope.launch {
            AppContainer.currentUser.collect { user ->
                if (user != null && user.role.needsGps()) {
                    requestLocationPermissions()
                } else {
                    stopService(Intent(this@MainActivity, GpsTrackingService::class.java))
                }
            }
        }
    }

    private fun requestLocationPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        locationPermissionRequest.launch(perms.toTypedArray())
    }

    private fun startGpsService() {
        val intent = Intent(this, GpsTrackingService::class.java)
        startForegroundService(intent)
    }

    // Toda la brigada en campo captura GPS+IMU: jefe coordina, nebulizador rocia,
    // anotador anota lo nebulizado, chofer abastece. El admin web es el unico
    // rol sin captura.
    private fun UserRole.needsGps(): Boolean = when (this) {
        UserRole.JEFE_BRIGADA,
        UserRole.NEBULIZADOR,
        UserRole.ANOTADOR,
        UserRole.CHOFER -> true
    }
}
