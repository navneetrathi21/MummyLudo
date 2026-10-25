package com.mummyludo.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Looper
import android.provider.Settings
import androidx.activity.result.ActivityResult
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.model.InstallStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo
import org.robolectric.shadows.ShadowWifiInfo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h900dp")
class MainActivityTest {
    private val controllers = mutableListOf<ActivityController<MainActivity>>()

    @After
    fun destroyActivities() {
        controllers.asReversed().forEach { controller ->
            runCatching { controller.pause().stop().destroy() }
        }
        controllers.clear()
    }

    @Test
    fun lifecycleAcceptsQaDiceInputAndNoAvailableUpdate() {
        val fake = fakeManager().apply { setUpdateNotAvailable() }
        val activity = createActivity(fake, "6,bad,0,3,7")

        assertFalse(activity.isFinishing)
        assertFalse(activity.updateReadyToInstall)
        assertNull(activity.wifiName)
    }

    @Test
    fun scannerAndInstallCallbacksIgnoreInvalidResultsAndAcceptValidOnes() {
        val activity = createActivity(fakeManager().apply { setUpdateNotAvailable() })

        activity.handleScannerResult(null)
        activity.handleScannerResult(Intent())
        assertEquals("", activity.scannedGameCode)
        activity.handleScannerResult(Intent().putExtra("SCAN_RESULT", "mummyludo://join?valid"))
        assertEquals("mummyludo://join?valid", activity.scannedGameCode)
        activity.handleScannerActivityResult(
            ActivityResult(Activity.RESULT_OK, Intent().putExtra("SCAN_RESULT", "scanned-result")),
        )
        assertEquals("scanned-result", activity.scannedGameCode)
        activity.clearScannedGameCode()
        assertEquals("", activity.scannedGameCode)
        activity.handleUpdateFlowResult(ActivityResult(Activity.RESULT_CANCELED, null))

        activity.handleInstallStatus(InstallStatus.PENDING)
        assertFalse(activity.updateReadyToInstall)
        activity.handleInstallStatus(InstallStatus.DOWNLOADED)
        assertTrue(activity.updateReadyToInstall)
        activity.dismissReadyUpdate()
        assertFalse(activity.updateReadyToInstall)
    }

    @Test
    fun updateReadyDialogRendersAndHandlesRestartAndLater() {
        val fake = fakeManager().apply { setUpdateNotAvailable() }
        val activity = createActivity(fake)
        activity.updateReadyToInstall = true
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(activity.updateReadyToInstall)
        activity.completeReadyUpdate()
        activity.dismissReadyUpdate()
        assertFalse(activity.updateReadyToInstall)
    }

    @Test
    fun cameraPermissionControlsWhetherScannerIsOpened() {
        val activity = createActivity(fakeManager().apply { setUpdateNotAvailable() })
        val application = RuntimeEnvironment.getApplication()

        shadowOf(application).denyPermissions(Manifest.permission.CAMERA)
        activity.handleCameraPermissionResult(false)
        assertNull(shadowOf(activity).peekNextStartedActivityForResult())

        activity.requestCameraAndScan()
        assertTrue(
            shadowOf(activity).lastRequestedPermission.requestedPermissions
                .contains(Manifest.permission.CAMERA),
        )

        activity.handleCameraPermissionResult(true)
        val scan = shadowOf(activity).nextStartedActivityForResult.intent
        assertEquals("com.google.zxing.client.android.SCAN", scan.action)

        shadowOf(application).grantPermissions(Manifest.permission.CAMERA)
        activity.requestCameraAndScan()
        assertEquals(
            "com.google.zxing.client.android.SCAN",
            shadowOf(activity).nextStartedActivityForResult.intent.action,
        )
    }

    @Test
    fun wifiPermissionRequestsPlatformPermissionsAndRefreshesSafely() {
        val activity = createActivity(fakeManager().apply { setUpdateNotAvailable() })

        activity.requestWifiPermission()
        val requested = shadowOf(activity).lastRequestedPermission.requestedPermissions.toSet()
        assertTrue(Manifest.permission.ACCESS_FINE_LOCATION in requested)
        assertTrue(Manifest.permission.NEARBY_WIFI_DEVICES in requested)

        activity.handleWifiPermissionResult()
        assertNull(activity.wifiName)
    }

    @Test
    fun modernWifiTransportProvidesCleanNetworkName() {
        val activity = createActivity(fakeManager().apply { setUpdateNotAvailable() })
        val connectivity = activity.getSystemService(ConnectivityManager::class.java)
        val networkInfo = wifiNetworkInfo()
        val connectivityShadow = shadowOf(connectivity)
        connectivityShadow.setActiveNetworkInfo(networkInfo)
        val wifiInfo = ShadowWifiInfo.newInstance()
        shadowOf(wifiInfo).setSSID("\"Family Wi-Fi\"")
        val capabilities = ShadowNetworkCapabilities.newInstance()
        val capabilitiesShadow = shadowOf(capabilities)
        capabilitiesShadow.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        capabilitiesShadow.setTransportInfo(wifiInfo)
        connectivityShadow.setNetworkCapabilities(connectivity.activeNetwork, capabilities)

        activity.refreshWifiName()

        assertEquals("Family Wi-Fi", activity.wifiName)
    }

    @Test
    @Config(sdk = [30], qualifiers = "w411dp-h900dp")
    fun legacyWifiConnectionProvidesCleanNetworkName() {
        val activity = createActivity(fakeManager().apply { setUpdateNotAvailable() })
        val connectivity = activity.getSystemService(ConnectivityManager::class.java)
        val networkInfo = wifiNetworkInfo()
        val connectivityShadow = shadowOf(connectivity)
        connectivityShadow.setActiveNetworkInfo(networkInfo)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        connectivityShadow.setNetworkCapabilities(connectivity.activeNetwork, capabilities)
        val wifiInfo = ShadowWifiInfo.newInstance()
        shadowOf(wifiInfo).setSSID("Legacy Wi-Fi")
        shadowOf(activity.getSystemService(WifiManager::class.java)).setConnectionInfo(wifiInfo)

        activity.refreshWifiName()

        assertEquals("Legacy Wi-Fi", activity.wifiName)
    }

    @Test
    fun updateAvailabilityDownloadAndDestructionAreHandled() {
        val fake = fakeManager().apply { setUpdateAvailable(10) }
        val activity = createActivity(fake)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(fake.isConfirmationDialogVisible)

        fake.userAcceptsUpdate()
        fake.downloadStarts()
        fake.downloadCompletes()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(activity.updateReadyToInstall)

        activity.dismissReadyUpdate()
        activity.checkForDownloadedUpdate()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(activity.updateReadyToInstall)
        activity.completeReadyUpdate()

        controllers.removeAt(controllers.lastIndex).pause().stop().destroy()
        activity.dismissReadyUpdate()
        fake.downloadCompletes()
        assertFalse(activity.updateReadyToInstall)
    }

    @Test
    fun locationPermissionAndSettingsShortcutWorkCorrectly() {
        val activity = createActivity(fakeManager().apply { setUpdateNotAvailable() })
        val application = RuntimeEnvironment.getApplication()

        shadowOf(application).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        assertFalse(activity.checkLocationPermission())

        activity.openAppSettings()
        val nextIntent = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, nextIntent.action)
        assertEquals("package:${activity.packageName}", nextIntent.dataString)

        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        assertTrue(activity.checkLocationPermission())
        activity.handleWifiPermissionResult()
        assertTrue(activity.hasLocationPermission)
    }

    private fun fakeManager() = FakeAppUpdateManager(RuntimeEnvironment.getApplication())

    @Suppress("DEPRECATION")
    private fun wifiNetworkInfo(): NetworkInfo = ShadowNetworkInfo.newInstance(
        NetworkInfo.DetailedState.CONNECTED,
        ConnectivityManager.TYPE_WIFI,
        0,
        true,
        true,
    )

    private fun createActivity(
        updateManager: FakeAppUpdateManager,
        qaDiceSequence: String? = null,
    ): MainActivity {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.get()
        activity.appUpdateManagerOverride = updateManager
        activity.intent = Intent().apply {
            qaDiceSequence?.let { putExtra("qaDiceSequence", it) }
        }
        controllers += controller
        controller.create().start().resume().visible()
        shadowOf(Looper.getMainLooper()).idle()
        return activity
    }
}
