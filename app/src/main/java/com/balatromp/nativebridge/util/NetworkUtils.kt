package com.balatromp.nativebridge.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

    fun getLocalIpAddress(context: Context): String? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return getHotspotIpAddress()
        val linkProperties = connectivityManager.getLinkProperties(activeNetwork) ?: return getHotspotIpAddress()

        for (address in linkProperties.linkAddresses) {
            val inetAddress = address.address
            if (inetAddress is Inet4Address && !inetAddress.isLoopbackAddress) {
                return inetAddress.hostAddress
            }
        }
        
        return getHotspotIpAddress()
    }

    private fun getHotspotIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.name.contains("ap0") || 
                    networkInterface.name.contains("swlan0") || 
                    networkInterface.name.contains("wlan0")) {
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (address is Inet4Address && !address.isLoopbackAddress) {
                            return address.hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
