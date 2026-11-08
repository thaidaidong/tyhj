package com.example.btwifi

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.net.NetworkInterface

class MainActivity : Activity() {
    private lateinit var info: TextView
    private lateinit var btn: Button
    private lateinit var acc: Button

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        info = TextView(this).apply { textSize = 18f; setPadding(32, 32, 32, 32) }
        btn = Button(this)
        acc = Button(this).apply {
            text = "Cài đặt Trợ năng (bật tự xác nhận ghép nối)"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        btn.setOnClickListener {
            val i = Intent(this, WebService::class.java)
            if (WebService.running) stopService(i) else startForegroundService(i)
            btn.postDelayed({ refresh() }, 700)
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(info); addView(btn); addView(acc)
        })
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        val urls = ips().joinToString("\n") { "http://$it:${WebService.PORT}" }
        info.text = "Token: ${WebService.token(this)}\n\n$urls\n\n" +
            (if (WebService.running) "SERVER: ĐANG CHẠY" else "SERVER: ĐÃ DỪNG") + "\n" +
            "Tự xác nhận ghép nối: " + (if (AutoPairService.connected) "BẬT" else "TẮT")
        btn.text = if (WebService.running) "Dừng server" else "Bật server"
    }

    private fun ips() = NetworkInterface.getNetworkInterfaces().toList()
        .flatMap { it.inetAddresses.toList() }
        .filter { !it.isLoopbackAddress && it.address.size == 4 }
        .map { it.hostAddress }
}
