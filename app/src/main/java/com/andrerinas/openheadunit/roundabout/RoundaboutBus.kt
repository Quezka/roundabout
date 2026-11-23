package com.andrerinas.openheadunit.roundabout

// rail service -> home fragment requests ; home consumes on resume
object RoundaboutBus {
    // start android auto the way the home card would
    @Volatile var startAndroidAuto = false
    // open the app drawer to fill this favorite slot
    @Volatile var pickFavoriteSlot: Int? = null
    // open the app drawer
    @Volatile var openDrawer = false
}
