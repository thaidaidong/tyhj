package com.example.btwifi

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.net.NetworkInterface

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        val info = TextView(this).apply { textSize = 18f; setPadding(32, 32, 32, 32) }
        val btn = Button(this)
        fun refresh() {
            val urls = ips().joinToString("\n") { "http://$it:${WebService.PORT}" }
            info.text = "Token: ${WebService.token(this)}\n\n$urls\n\n" +
                if (WebService.running) "ĐANG CHẠY" else "ĐÃ DỪNG"
            btn.text = if (WebService.running) "Dừng server" else "Bật server"
        }
        btn.setOnClickListener {
            val i = Intent(this, WebService::class.java)
            if (WebService.running) stopService(i) else startForegroundService(i)
            btn.postDelayed({ refresh() }, 700)
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(info); addView(btn)
        })
        refresh()
    }

    private fun ips() = NetworkInterface.getNetworkInterfaces().toList()
        .flatMap { it.inetAddresses.toList() }
        .filter { !it.isLoopbackAddress && it.address.size == 4 }
        .map { it.hostAddress }
}
