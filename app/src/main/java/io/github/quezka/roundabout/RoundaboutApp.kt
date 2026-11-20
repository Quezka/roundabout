package io.github.quezka.roundabout

import android.app.Application

// android 5.1 libcore hands out an AF_INET6 listener by default ; the phone dials
// us over ipv4 and those connections never reach the socket. force the v4 stack.
class RoundaboutApp : Application() {
    override fun onCreate() {
        System.setProperty("java.net.preferIPv4Stack", "true")
        super.onCreate()
    }
}
