package com.andrerinas.openheadunit.roundabout.system

import android.content.Context
import android.provider.Settings
import com.andrerinas.openheadunit.utils.Settings as Oh

// best cfg for prestigio `PMT3797` (mt8321 ~quad a7, 1 gb, 1280x800 ~7", 5.1) as car headunit
// run @ setup done + "apply tablet profile" ; ! off main thread
object TabletProfile {

    fun apply(context: Context) {
        applyApp(Oh(context))
        applySystem(context)
    }

    fun applyApp(s: Oh) {
        // video: 720p h264 hw ; 1080p = decoder too slow @ mt8321 ; 30 fps <- cpu + heat in car
        s.resolutionId = Oh.Resolution._1280x720.id
        s.videoCodec = "H.264"
        s.fpsLimit = 30
        s.forceSoftwareDecoding = false
        // textureview <- surfaceview misplaced by overscan ; no forced scale
        s.viewMode = Oh.ViewMode.TEXTURE
        s.forcedScale = false
        // ~7" 1280x800 => aa dpi 160 = car-size targets
        s.dpiPixelDensity = 160
        // roundabout = home + rail ; no oh floating button / lock hold
        s.enableCarLauncher = true
        s.enableFloatingButton = false
        s.showOverLockScreen = false
        s.startInFullscreenMode = true
        s.showToastMessages = false
        // usb cable first ; start on plug, back on reconnect
        if (s.primaryConnection == Oh.ConnectionKind.UNSET) s.primaryConnection = Oh.ConnectionKind.USB_CABLE
        s.autoStartOnUsb = true
        s.usbConnectOnPlugIn = true
        s.autoConnectLastSession = true
        s.autoConnectSingleUsbDevice = true
        s.reopenOnReconnection = true
        s.killOnDisconnect = false
        // audio: aac decode = cpu cost -> pcm ; aaudio n/a < 8.1
        s.enableAudioSink = true
        s.useAacAudio = false
        s.useAAudioOutput = false
        // phone mic > tablet mic in car
        s.useHeadUnitMicrophone = false
        // media card + volume
        s.syncMediaSessionWithAaMetadata = true
        s.volumeKeysToPhone = false
        // ignition off -> screen off
        s.externalPowerScreenOff = true
        s.autoStartOnBoot = true
        s.autoKillOemApps = false
        s.commit()
    }

    private fun applySystem(c: Context) {
        val sys = SystemAccess
        // awake while powered (ac + usb + wireless = 7) ; battery -> off after 5 min
        sys.putGlobal(c, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, "7")
        sys.putSystem(c, Settings.System.SCREEN_OFF_TIMEOUT, "300000")
        // wifi never sleeps <- wireless aa + hotspot
        sys.putGlobal(c, Settings.Global.WIFI_SLEEP_POLICY, Settings.Global.WIFI_SLEEP_POLICY_NEVER.toString())
        // weak gpu -> half animations
        DisplayPanel.animations(c, "0.5")
        // network time + zone
        sys.putGlobal(c, Settings.Global.AUTO_TIME, "1")
        sys.putGlobal(c, Settings.Global.AUTO_TIME_ZONE, "1")
        sys.putSystem(c, Settings.System.TIME_12_24, "24")
        // landscape lock
        sys.putSystem(c, Settings.System.ACCELEROMETER_ROTATION, "0")
        sys.putSystem(c, Settings.System.USER_ROTATION, "1")
        // quiet car: no touch clicks, no lock sounds, no haptics
        sys.putSystem(c, Settings.System.SOUND_EFFECTS_ENABLED, "0")
        sys.putSystem(c, Settings.System.HAPTIC_FEEDBACK_ENABLED, "0")
        sys.putSystem(c, "lockscreen_sounds_enabled", "0")
        // no lock screen ; no setup wizard after flash
        sys.putSecure(c, "lockscreen.disabled", "1")
        sys.putGlobal(c, Settings.Global.DEVICE_PROVISIONED, "1")
        sys.putSecure(c, "user_setup_complete", "1")
        // updates of roundabout apk by hand
        sys.putGlobal(c, "install_non_market_apps", "1")
        sys.putGlobal(c, "package_verifier_enable", "0")
        // gps for oh location + speed dial
        sys.putSecure(c, "location_mode", "3")
        // no heads-up pops over aa
        sys.putGlobal(c, "heads_up_notifications_enabled", "0")
        // bluetooth on <- phone link
        sys.putGlobal(c, Settings.Global.BLUETOOTH_ON, "1")
        try { android.bluetooth.BluetoothAdapter.getDefaultAdapter()?.enable() } catch (e: Exception) { }
        sys.grantAll(c)
    }
}
