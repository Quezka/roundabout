package com.andrerinas.openheadunit.roundabout.system

// app settings snapshot from the real tablet (2026-11-25) = tablet profile
// - phone macs, wifi direct names/keys, caches, migration flags, setup state, primary-connection
// regen: pull `shared_prefs/settings.xml` -> same deny rules (see git log)
internal object TabletProfileSnapshot {
    sealed class V(val key: String)
    class B(key: String, val v: Boolean) : V(key)
    class I(key: String, val v: Int) : V(key)
    class L(key: String, val v: Long) : V(key)
    class S(key: String, val v: String) : V(key)
    class Set(key: String, val v: kotlin.collections.Set<String>) : V(key)

    private fun b(k: String, v: Boolean) = B(k, v)
    private fun i(k: String, v: Int) = I(k, v)
    private fun l(k: String, v: Long) = L(k, v)
    private fun s(k: String, v: String) = S(k, v)
    private fun set(k: String, vararg v: String) = Set(k, v.toSet())

    val values: List<V> = listOf(
        b("advanced-settings-active", true),
        b("attach_hw_dsp_equalizer", true),
        b("auto-connect-last-session", true),
        b("auto-connect-single-usb", true),
        b("auto-enable-hotspot", true),
        b("auto-kill-oem-apps", false),
        b("auto-monochrome-buttons-at-night", true),
        b("auto-restart-wifi-p2p-stuck", true),
        b("auto-resume-playback-on-reconnect", true),
        b("auto-start-on-boot", true),
        b("auto-start-on-screen-on", true),
        b("auto-start-on-usb", true),
        b("auto-start-on-wifi", false),
        b("debug-video-low-latency", true),
        b("enable-audio-sink", true),
        b("enable-car-launcher", true),
        b("enable-floating-button", false),
        b("external-power-disconnect", true),
        b("external-power-screen-off", true),
        b("force-software-decoding", false),
        b("forced_scale", false),
        b("gps-navigation", false),
        b("kill-on-disconnect", false),
        b("native-aa-enable-bluetooth", true),
        b("reopen-on-reconnection", true),
        b("show-over-lock-screen", false),
        b("show-toast-messages", false),
        b("usb-connect-on-plug-in", true),
        b("sync-media-session-aa-metadata", true),
        b("use-aac-audio", false),
        b("use-aaudio-output", false),
        b("use-head-unit-microphone", false),
        b("video-profile-starvation-cap", false),
        b("volume-keys-to-phone", false),
        i("dpi-pixel-density", 160),
        i("external-power-disconnect-delay-seconds", 5),
        i("fps-limit", 30),
        i("fullscreen-mode", 1),
        i("home-background-night-mode", 1),
        i("inset-bottom", 0),
        i("inset-left", 0),
        i("inset-right", 0),
        i("inset-top", 0),
        i("mic-input-source", 2),
        i("mic-sample-rate", 16000),
        i("native-driver-selection-timeout", 17),
        i("resolutionId", 2),
        i("screen-orientation", 2),
        i("speed-volume-mode", 1),
        i("ui-scale-home-percent", 100),
        i("ui-scale-settings-percent", 100),
        i("video-fit-mode", 0),
        i("view-mode", 1),
        i("wifi-5ghz-channel", 0),
        i("wifi-direct-band", 2),
        s("display-size-preset", "SMALL_7_8"),
        s("head-unit-make", "Mini"),
        s("head-unit-model", "Cooper D JCW"),
        s("rb-drive-widget", "SPEED"),
        s("vehicle-display-name", "Mini Cooper"),
        s("vehicle-make", "Mini"),
        s("vehicle-model", "Cooper D JCW"),
        s("vehicle-year", "2008"),
        s("video-codec", "H.264"),
        set("connection-modes", "wifi"),
    )
}
