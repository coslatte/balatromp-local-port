package com.balatromp.nativebridge.util

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {

    private const val TAG = "NetworkUtils"
    private val HOTSPOT_INTERFACE_PATTERNS = listOf("ap0", "swlan0", "wlan0")

    fun getLocalIpAddress(context: Context): String? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val linkProperties = connectivityManager.activeNetwork
            ?.let { connectivityManager.getLinkProperties(it) }
        val activeIp = linkProperties?.linkAddresses
            ?.asSequence()
            ?.map { it.address }
            ?.filterIsInstance<Inet4Address>()
            ?.firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
        return activeIp ?: getHotspotIpAddress()
    }

    private fun getHotspotIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            Collections.list(interfaces).asSequence()
                .filter { iface -> HOTSPOT_INTERFACE_PATTERNS.any { iface.name.contains(it) } }
                .flatMap { iface -> Collections.list(iface.inetAddresses).asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress
        } catch (e: Exception) {
            Log.w(TAG, "Hotspot IP lookup failed", e)
            null
        }
    }
}
