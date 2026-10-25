package com.mummyludo.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.mummyludo.app.game.*
import com.mummyludo.app.nearby.*
import com.google.zxing.integration.android.IntentIntegrator
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var appUpdateManager: AppUpdateManager
    internal var appUpdateManagerOverride: AppUpdateManager? = null
    internal var updateReadyToInstall by mutableStateOf(false)
        internal set
    private val updateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
        ::handleUpdateFlowResult,
    )
    private val updateListener = InstallStateUpdatedListener { state ->
        handleInstallStatus(state.installStatus())
    }
    internal var scannedGameCode by mutableStateOf("")
        private set
    internal var wifiName by mutableStateOf<String?>(null)
        private set
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ::handleCameraPermissionResult,
    )
    private val wifiPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
        ::handleWifiPermissionResult,
    )
    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        ::handleScannerActivityResult,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshWifiName()
        appUpdateManager = appUpdateManagerOverride ?: AppUpdateManagerFactory.create(this)
        appUpdateManager.registerListener(updateListener)
        val qaValues = if (BuildConfig.DEBUG) {
            intent.getStringExtra("qaDiceSequence")
                ?.split(',')
                ?.mapNotNull(String::toIntOrNull)
                ?.filter { it in 1..6 }
                ?.let(::ArrayDeque)
        } else null
        val engine = qaValues?.let { values ->
            LudoEngine { values.removeFirstOrNull() ?: kotlin.random.Random.nextInt(1, 7) }
        }
        setContent {
            MummyLudoTheme {
                Box(Modifier.fillMaxSize()) {
                    MummyLudoApp(
                        providedEngine = engine,
                        onOpenWifiSettings = { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) },
                        wifiName = wifiName,
                        scannedGameCode = scannedGameCode,
                        onClearScannedCode = ::clearScannedGameCode,
                        onRequestWifiPermission = { requestWifiPermission() },
                        onScanGameCode = { requestCameraAndScan() },
                        onExitApp = { finish() },
                    )
                    if (updateReadyToInstall) {
                        UpdateReadyDialog(
                            onRestart = ::completeReadyUpdate,
                            onLater = ::dismissReadyUpdate,
                        )
                    }
                }
            }
        }
        checkForPlayStoreUpdate()
    }

    override fun onResume() {
        super.onResume()
        refreshWifiName()
        if (::appUpdateManager.isInitialized) {
            checkForDownloadedUpdate()
        }
    }

    internal fun requestCameraAndScan() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchGameScanner()
        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    internal fun handleCameraPermissionResult(granted: Boolean) {
        if (granted) launchGameScanner()
    }

    internal fun handleUpdateFlowResult(@Suppress("UNUSED_PARAMETER") result: ActivityResult) = Unit

    internal fun launchGameScanner() {
        scannerLauncher.launch(
            IntentIntegrator(this).setDesiredBarcodeFormats(listOf(IntentIntegrator.QR_CODE))
                .setPrompt("Scan the host's Mummy Ludo game code").setBeepEnabled(false).createScanIntent(),
        )
    }

    internal fun requestWifiPermission() {
        wifiPermissionLauncher.launch(requiredWifiPermissions(Build.VERSION.SDK_INT).toTypedArray())
    }

    internal fun handleWifiPermissionResult(@Suppress("UNUSED_PARAMETER") result: Map<String, Boolean> = emptyMap()) {
        refreshWifiName()
    }

    internal fun handleScannerActivityResult(result: ActivityResult) {
        handleScannerResult(result.data)
    }

    internal fun handleScannerResult(data: Intent?) {
        data?.getStringExtra("SCAN_RESULT")?.let { scannedGameCode = it }
    }

    internal fun clearScannedGameCode() {
        scannedGameCode = ""
    }

    internal fun handleInstallStatus(status: Int) {
        if (status == InstallStatus.DOWNLOADED) {
            updateReadyToInstall = true
        }
    }

    internal fun dismissReadyUpdate() {
        updateReadyToInstall = false
    }

    internal fun completeReadyUpdate() {
        appUpdateManager.completeUpdate()
    }

    internal fun refreshWifiName() {
        wifiName = runCatching {
            val connectivity = applicationContext.getSystemService(ConnectivityManager::class.java)
            val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) return@runCatching null
            val wifiManager = applicationContext.getSystemService(WifiManager::class.java)
            @Suppress("DEPRECATION")
            val fromWifiManager = cleanWifiSsid(wifiManager?.connectionInfo?.ssid)
            if (fromWifiManager != null) return@runCatching fromWifiManager

            val wifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                capabilities.transportInfo as? WifiInfo
            } else {
                @Suppress("DEPRECATION")
                wifiManager?.connectionInfo
            }
            cleanWifiSsid(wifiInfo?.ssid)
        }.getOrNull()
    }

    override fun onDestroy() {
        if (::appUpdateManager.isInitialized) {
            appUpdateManager.unregisterListener(updateListener)
        }
        super.onDestroy()
    }

    internal fun checkForPlayStoreUpdate() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (
                info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                appUpdateManager.startUpdateFlowForResult(
                    info,
                    updateLauncher,
                    AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                )
            }
        }
    }

    internal fun checkForDownloadedUpdate() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                updateReadyToInstall = true
            }
        }
    }
}

@Composable
internal fun UpdateReadyDialog(onRestart: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Update ready") },
        text = { Text("A new version has been downloaded from Play Store.") },
        confirmButton = {
            TextButton(onClick = onRestart) {
                Text("Restart")
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) {
                Text("Later")
            }
        },
    )
}
