package com.example.btwifi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import fi.iki.elonen.NanoHTTPD
import java.util.UUID

class WebService : Service() {
    private var api: Api? = null
    private var wl: PowerManager.WakeLock? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("srv", "Server", NotificationManager.IMPORTANCE_LOW))
        startForeground(1, Notification.Builder(this, "srv")
            .setContentTitle("Web server đang chạy cổng $PORT")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).build())
        wl = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "btwifi:srv").apply { acquire() }
        if (api == null) {
            api = Api(this, token(this), PORT).also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
        }
        running = true
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        api?.stop(); api = null
        wl?.release()
        super.onDestroy()
    }

    companion object {
        const val PORT = 8080
        @Volatile var running = false
        fun token(c: Context): String {
            val p = c.getSharedPreferences("p", 0)
            if (!p.contains("token")) p.edit().putString("token", UUID.randomUUID().toString().take(8)).apply()
            return p.getString("token", "")!!
        }
    }
}

/** Tự chạy server khi máy khởi động (hữu ích khi tháo pin, hay mất điện). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED) c.startForegroundService(Intent(c, WebService::class.java))
    }
}
