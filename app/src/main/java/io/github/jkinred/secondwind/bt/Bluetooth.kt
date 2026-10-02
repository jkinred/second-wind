package io.github.jkinred.secondwind.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class FoundDevice(val name: String, val address: String, val bonded: Boolean)

/** Adapter access: enable state, bonded/discovered Yellowbricks, bond removal. Callers hold BLUETOOTH_CONNECT/SCAN. */
@SuppressLint("MissingPermission")
class Bluetooth(private val context: Context) {
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    val isAvailable: Boolean get() = adapter != null
    val isEnabled: Boolean get() = adapter?.isEnabled == true

    fun device(address: String): BluetoothDevice? = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()

    fun isBonded(address: String) = device(address)?.bondState == BluetoothDevice.BOND_BONDED

    /** Hidden API; returns false when the platform refuses. */
    fun forgetBond(address: String): Boolean = device(address)?.let(::removeBond) ?: false

    fun cancelDiscovery() { runCatching { if (adapter?.isDiscovering == true) adapter.cancelDiscovery() } }

    /** Bonded Yellowbricks first, then live discovery until the adapter finishes. */
    fun scan(): Flow<FoundDevice> = callbackFlow {
        val ad = adapter ?: run { close(); return@callbackFlow }
        ad.bondedDevices.orEmpty().filter { isYellowbrick(it.name) }.forEach {
            trySend(FoundDevice(it.name, it.address, bonded = true))
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
                        val name = d.name ?: intent.getStringExtra(BluetoothDevice.EXTRA_NAME) ?: return
                        if (isYellowbrick(name)) trySend(FoundDevice(name, d.address, d.bondState == BluetoothDevice.BOND_BONDED))
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> close()
                }
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            },
        )
        if (ad.isDiscovering) ad.cancelDiscovery()
        ad.startDiscovery()
        awaitClose {
            runCatching { ad.cancelDiscovery() }
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    companion object {
        fun isYellowbrick(name: String?): Boolean = name?.uppercase()?.startsWith("YELLOWBRICK") == true

        internal fun removeBond(d: BluetoothDevice): Boolean =
            runCatching { d.javaClass.getMethod("removeBond").invoke(d) as Boolean }.getOrDefault(false)
    }
}
