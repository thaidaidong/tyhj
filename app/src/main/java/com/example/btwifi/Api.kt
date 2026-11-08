package com.example.btwifi

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.provider.Settings
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class Api(private val ctx: Context, private val token: String, port: Int) : NanoHTTPD(port) {
    private val bt: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private fun json(st: Response.Status, o: JSONObject) =
        newFixedLengthResponse(st, "application/json; charset=utf-8", o.toString())

    private fun ok() = JSONObject().put("ok", true)

    override fun serve(s: IHTTPSession): Response {
        if (!s.uri.startsWith("/api/")) {
            val html = ctx.assets.open("index.html").bufferedReader().use { it.readText() }
            return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
        }
        if (s.headers["x-token"] != token)
            return json(Response.Status.UNAUTHORIZED, JSONObject().put("ok", false).put("error", "Sai token"))
        var body = JSONObject()
        return try {
            if (s.method == Method.POST) {
                val m = HashMap<String, String>(); s.parseBody(m)
                m["postData"]?.takeIf { it.isNotBlank() }?.let { body = JSONObject(it) }
            }
            json(Response.Status.OK, route(s.uri, body))
        } catch (e: Exception) {
            json(Response.Status.INTERNAL_ERROR, JSONObject().put("ok", false).put("error", e.message ?: e.toString()))
        }
    }

    private fun route(path: String, b: JSONObject): JSONObject = when (path) {
        "/api/status" -> status()
        "/api/bt/power" -> {
            if (b.optBoolean("on")) bt.enable() else bt.disable()
            if (!waitBt(b.optBoolean("on"))) throw Exception("Bluetooth không đổi trạng thái")
            ok()
        }
        "/api/bt/scan" -> btScan()
        "/api/bt/pair" -> btPair(b.getString("mac"), b.optString("pin"))
        "/api/bt/unpair" -> {
            val d = bt.getRemoteDevice(b.getString("mac"))
            d.javaClass.getMethod("removeBond").invoke(d)   // API ẩn nhưng chạy được trên Android 8
            ok()
        }
        "/api/wifi/power" -> { wifi.isWifiEnabled = b.optBoolean("on"); Thread.sleep(1500); ok() }
        "/api/wifi/scan" -> wifiScan()
        "/api/wifi/connect" -> wifiConnect(b.getString("ssid"), b.optString("password"))
        "/api/open" -> {
            val i = when (b.optString("target")) {
                "bt" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
                "hotspot" -> Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings")
                else -> throw Exception("target không hợp lệ")
            }
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); ok()
        }
        else -> throw Exception("Không có API $path")
    }

    private fun ipStr(i: Int) = if (i == 0) null else "${i and 255}.${i shr 8 and 255}.${i shr 16 and 255}.${i shr 24 and 255}"

    private fun status(): JSONObject {
        val bonded = JSONArray()
        bt.bondedDevices?.forEach { bonded.put(JSONObject().put("mac", it.address).put("name", it.name ?: "")) }
        val w = wifi.connectionInfo
        return ok()
            .put("bt", JSONObject().put("on", bt.isEnabled).put("bonded", bonded))
            .put("wifi", JSONObject().put("on", wifi.isWifiEnabled)
                .put("ssid", w?.ssid?.trim('"')).put("ip", ipStr(w?.ipAddress ?: 0)))
    }

    private fun waitBt(on: Boolean): Boolean {
        repeat(24) { if (bt.isEnabled == on) return true; Thread.sleep(250) }
        return false
    }

    private fun btScan(): JSONObject {
        if (!bt.isEnabled) { bt.enable(); waitBt(true) }
        val found = Collections.synchronizedMap(LinkedHashMap<String, JSONObject>())
        val done = CountDownLatch(1)
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.action == BluetoothDevice.ACTION_FOUND) {
                    val d = i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                    found[d.address] = JSONObject().put("mac", d.address).put("name", d.name ?: "(không tên)")
                        .put("bonded", d.bondState == BluetoothDevice.BOND_BONDED)
                } else done.countDown()
            }
        }
        ctx.registerReceiver(r, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND); addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        })
        try { bt.cancelDiscovery(); bt.startDiscovery(); done.await(15, TimeUnit.SECONDS) }
        finally { bt.cancelDiscovery(); ctx.unregisterReceiver(r) }
        return ok().put("devices", JSONArray(found.values.toList()))
    }

    private fun btPair(mac: String, pin: String): JSONObject {
        val d = bt.getRemoteDevice(mac)
        if (d.bondState == BluetoothDevice.BOND_BONDED) return ok()
        bt.cancelDiscovery()
        // Thiết bị dùng PIN cố định (tai nghe, HC-05...): tự điền PIN nếu có
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val dev = i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                if (pin.isNotEmpty() && dev.address == mac) try { dev.setPin(pin.toByteArray()) } catch (_: Exception) {}
            }
        }
        ctx.registerReceiver(r, IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST).apply { priority = 999 })
        try {
            d.createBond()
            var seen = false
            for (n in 0 until 90) {
                Thread.sleep(500)
                when (d.bondState) {
                    BluetoothDevice.BOND_BONDED -> return ok()
                    BluetoothDevice.BOND_BONDING -> seen = true
                    else -> if (seen) break
                }
            }
        } finally { ctx.unregisterReceiver(r) }
        throw Exception("Chưa ghép được. Nếu máy hiện hộp thoại xác nhận mã, hãy bấm 'Ghép nối' trên điện thoại.")
    }

    private fun wifiScan(): JSONObject {
        if (!wifi.isWifiEnabled) { wifi.isWifiEnabled = true; Thread.sleep(2500) }
        wifi.startScan(); Thread.sleep(4000)
        val list = wifi.scanResults.filter { it.SSID.isNotEmpty() }
            .sortedByDescending { it.level }.distinctBy { it.SSID }
        return ok().put("networks", JSONArray(list.map {
            JSONObject().put("ssid", it.SSID).put("level", it.level)
                .put("secured", it.capabilities.contains("WPA") || it.capabilities.contains("WEP"))
        }))
    }

    private fun wifiConnect(ssid: String, pw: String): JSONObject {
        if (!wifi.isWifiEnabled) { wifi.isWifiEnabled = true; Thread.sleep(2500) }
        val q = "\"$ssid\""
        var id = wifi.configuredNetworks?.firstOrNull { it.SSID == q }?.networkId ?: -1
        if (id == -1 || pw.isNotEmpty()) {
            if (id != -1) wifi.removeNetwork(id)
            val c = WifiConfiguration().apply {
                SSID = q
                if (pw.isEmpty()) allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE) else preSharedKey = "\"$pw\""
            }
            id = wifi.addNetwork(c)
        }
        if (id == -1) throw Exception("Không thêm được mạng $ssid")
        wifi.disconnect(); wifi.enableNetwork(id, true); wifi.reconnect()
        Thread.sleep(5000)
        return status()
    }
}
