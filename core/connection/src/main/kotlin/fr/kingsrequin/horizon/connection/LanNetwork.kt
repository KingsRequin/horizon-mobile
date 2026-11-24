package fr.kingsrequin.horizon.connection

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import javax.net.SocketFactory

/**
 * Le réseau local du téléphone (Wi-Fi ou Ethernet), suivi par un rappel du système : aucune
 * interrogation périodique. `onChange(vrai)` quand un tel réseau apparaît, `onChange(faux)`
 * quand le dernier disparaît. Les données mobiles ne comptent pas : le PC n'y est pas.
 */
class LanNetwork(context: Context, private val onChange: (Boolean) -> Unit) {
    private val networks = LinkedHashSet<Network>()

    init {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            // Un réseau local sans accès à Internet convient aussi.
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
        connectivity.registerNetworkCallback(
            request,
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = change { networks.add(network) }

                override fun onLost(network: Network) = change { networks.remove(network) }
            },
        )
    }

    private fun change(update: () -> Unit) {
        val (before, after) = synchronized(networks) {
            val before = networks.isNotEmpty()
            update()
            before to networks.isNotEmpty()
        }
        if (before != after) onChange(after)
    }

    /**
     * Fabrique de connexions qui sortent par le réseau local, même quand les données mobiles sont
     * le réseau par défaut du téléphone ; `null` sans réseau local.
     */
    fun socketFactory(): SocketFactory? = synchronized(networks) { networks.firstOrNull() }?.socketFactory
}
