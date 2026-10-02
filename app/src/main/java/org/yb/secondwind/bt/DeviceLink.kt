package org.yb.secondwind.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.yb.secondwind.proto.Frame
import org.yb.secondwind.proto.FrameDecoder
import java.io.IOException
import java.util.UUID

/** Why a connect attempt failed; the UI maps these to copy + action. */
sealed class ConnectError(message: String) : IOException(message) {
    /** Every rung failed and no bond exists: device off / out of range / wrong address. */
    class Unreachable(detail: String) : ConnectError(detail)
    /** Bonded, every rung failed, bond removal refused by the platform. User must forget the device manually. */
    class StaleBondNotRemovable(detail: String) : ConnectError(detail)
    /** Bond was removed and the redial still failed (pairing dialog dismissed, or device really unreachable). */
    class RepairFailed(detail: String) : ConnectError(detail)
}

/**
 * One RFCOMM link to a Yellowbrick. docs/PROTOCOL.md §1.
 * Rung ladder SDP → insecure ch1 → secure ch1; when all fail while bonded, the
 * device has evicted our key (another host paired): drop the bond and redial so
 * the system pairing dialog appears.
 */
@SuppressLint("MissingPermission")
class DeviceLink private constructor(private val socket: BluetoothSocket, val rung: String, val repaired: Boolean) {
    private val input = socket.inputStream
    private val output = socket.outputStream
    private val writeLock = Mutex()
    private val decoder = FrameDecoder()

    /** Closed (with cause) when the socket dies. */
    val frames: Channel<Frame> = Channel(Channel.UNLIMITED)

    suspend fun send(bytes: ByteArray) = withContext(Dispatchers.IO) {
        writeLock.withLock {
            output.write(bytes)
            output.flush()
        }
    }

    /** Blocks until the socket closes, then closes [frames]. */
    suspend fun readLoop() = withContext(Dispatchers.IO) {
        val buf = ByteArray(1024)
        try {
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                for (f in decoder.feed(buf, n)) frames.send(f)
            }
            frames.close()
        } catch (e: IOException) {
            frames.close(e)
        }
    }

    fun close() {
        runCatching { socket.close() }
        frames.close()
    }

    companion object {
        private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val CHANNEL = 1
        private const val BOND_SETTLE_MS = 1500L

        suspend fun connect(device: BluetoothDevice, log: (String) -> Unit): DeviceLink = withContext(Dispatchers.IO) {
            val first = try {
                return@withContext climb(device, repaired = false)
            } catch (e: IOException) { e }
            if (device.bondState != BluetoothDevice.BOND_BONDED) throw ConnectError.Unreachable(first.message ?: "connect failed")
            log("all rungs failed while bonded: device has probably been paired by another phone; removing bond and redialling")
            if (!Bluetooth.removeBond(device)) throw ConnectError.StaleBondNotRemovable(first.message ?: "connect failed")
            delay(BOND_SETTLE_MS)
            try {
                climb(device, repaired = true)
            } catch (e: IOException) {
                throw ConnectError.RepairFailed(e.message ?: "connect failed")
            }
        }

        private fun climb(device: BluetoothDevice, repaired: Boolean): DeviceLink {
            val rungs: List<Pair<String, () -> BluetoothSocket>> = listOf(
                "sdp" to { device.createInsecureRfcommSocketToServiceRecord(SPP) },
                "insecure-ch$CHANNEL" to { hidden(device, "createInsecureRfcommSocket") },
                "secure-ch$CHANNEL" to { hidden(device, "createRfcommSocket") },
            )
            val errors = ArrayList<String>(rungs.size)
            for ((name, open) in rungs) {
                val socket = try { open() } catch (e: Exception) { errors += "$name: ${e.message}"; continue }
                try {
                    socket.connect()
                    return DeviceLink(socket, name, repaired)
                } catch (e: IOException) {
                    runCatching { socket.close() }
                    errors += "$name: ${e.message}"
                }
            }
            throw IOException(errors.joinToString("; "))
        }

        private fun hidden(device: BluetoothDevice, method: String): BluetoothSocket =
            device.javaClass.getMethod(method, Int::class.javaPrimitiveType).invoke(device, CHANNEL) as BluetoothSocket
    }
}
