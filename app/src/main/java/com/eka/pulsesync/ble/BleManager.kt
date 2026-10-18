package com.eka.pulsesync.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var gatt: BluetoothGatt? = null
    private var isScanning = false

    private val _connectionState = MutableStateFlow("DISCONNECTED")
    val connectionState = _connectionState.asStateFlow()

    private val _batteryLevel = MutableStateFlow<Int?>(null)
    val batteryLevel = _batteryLevel.asStateFlow()

    private val _heartRate = MutableStateFlow<Int?>(null)
    val heartRate = _heartRate.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    private val authKeyBytes = hexStringToByteArray("132e1feab2a726c499284af2204445d3")

    companion object {
        val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATTERY_CHAR_UUID: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

        val IMMEDIATE_ALERT_SERVICE: UUID = UUID.fromString("00001802-0000-1000-8000-00805f9b34fb")
        val ALERT_LEVEL_CHAR: UUID = UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb")

        val HEART_RATE_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_CHAR: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")

        val CLIENT_CONFIG_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private fun addLog(msg: String) {
        _logs.value = (listOf(msg) + _logs.value).take(10)
        Log.d("PulseSyncBLE", msg)
    }

    fun connect(macAddress: String) {
        if (adapter == null || !adapter.isEnabled) {
            addLog("[BLE ERR] Bluetooth HP mati. Aktifkan dulu!")
            return
        }

        try {
            val device = adapter.getRemoteDevice(macAddress)
            addLog("[BLE] Target MAC: $macAddress. Memulai scanning & direct GATT...")
            _connectionState.value = "CONNECTING..."

            gatt?.disconnect()
            gatt?.close()
            gatt = null

            // Connect using TRANSPORT_LE for BLE
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: Exception) {
            addLog("[BLE ERR] Gagal connect: ${e.javaClass.simpleName} (${e.message ?: "Izin Bluetooth ditolak"})")
            _connectionState.value = "ERROR"
        }
    }

    fun startScanAndConnect(targetMac: String) {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            addLog("[BLE] Scanner tidak tersedia, fallback direct connect...")
            connect(targetMac)
            return
        }

        if (isScanning) return
        isScanning = true
        addLog("[SCAN] Scanning Smartband $targetMac di udara...")
        _connectionState.value = "SCANNING..."

        val filter = ScanFilter.Builder().setDeviceAddress(targetMac).build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(listOf(filter), settings, object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (result.device.address.equals(targetMac, ignoreCase = true)) {
                    addLog("[SCAN FOUND] Sinyal terdeteksi (RSSI: ${result.rssi} dBm)! Menghentikan scan & connect...")
                    scanner.stopScan(this)
                    isScanning = false
                    gatt = result.device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                addLog("[SCAN FAIL] Error scan code $errorCode, direct connect...")
                isScanning = false
                connect(targetMac)
            }
        })
    }

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _connectionState.value = "DISCONNECTED"
        addLog("[BLE] Terputus.")
    }

    fun readBattery() {
        val g = gatt
        if (g == null) {
            addLog("[BATT ERR] Jam belum terhubung GATT!")
            return
        }
        val battService = g.getService(BATTERY_SERVICE_UUID)
        val battChar = battService?.getCharacteristic(BATTERY_CHAR_UUID)
        if (battChar != null) {
            val ok = g.readCharacteristic(battChar)
            addLog("[GATT] Request read 0x2A19 -> $ok")
        } else {
            addLog("[GATT] Service 0x180F tidak ada di jam, cek auth!")
        }
    }

    fun sendVibrateAlert() {
        val g = gatt
        if (g == null) {
            addLog("[ALERT ERR] GATT belum connect!")
            return
        }

        // Coba Immediate Alert (0x1802 -> 0x2A06)
        val alertService = g.getService(IMMEDIATE_ALERT_SERVICE)
        if (alertService != null) {
            val alertChar = alertService.getCharacteristic(ALERT_LEVEL_CHAR)
            if (alertChar != null) {
                alertChar.value = byteArrayOf(0x02) // High Alert / Vibrate
                val ok = g.writeCharacteristic(alertChar)
                addLog("[GATT 0x2A06] Write Vibrate -> $ok")
                return
            }
        }

        // Fallback: Scan characteristic yang bisa di-write
        for (service in g.services) {
            for (ch in service.characteristics) {
                if ((ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                    (ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                    val sUuid = service.uuid.toString().substring(4, 8)
                    val cUuid = ch.uuid.toString().substring(4, 8)
                    if (sUuid.equals("fee0", true) || sUuid.equals("1802", true)) {
                        ch.value = byteArrayOf(0x08, 0x01)
                        g.writeCharacteristic(ch)
                        addLog("[GATT 0x$sUuid:0x$cUuid] Write Vibrate Fallback")
                        return
                    }
                }
            }
        }
        addLog("[GATT ALERT] Immediate Alert (0x1802) tidak ditemukan di service terbuka.")
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionState.value = "CONNECTED (GATT OK)"
                addLog("[BLE OK] Terhubung! Status $status. Discovering services...")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = "DISCONNECTED"
                addLog("[BLE] Terputus (GATT status $status). Pastikan Mi Fitness di-force stop!")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val serviceList = gatt.services.map { it.uuid.toString().substring(4, 8) }.joinToString(", ")
                addLog("[SERVICES] Ditemukan: $serviceList")

                // Coba baca baterai jika ada 0x180F
                val battService = gatt.getService(BATTERY_SERVICE_UUID)
                val battChar = battService?.getCharacteristic(BATTERY_CHAR_UUID)
                if (battChar != null) {
                    gatt.readCharacteristic(battChar)
                    addLog("[GATT] Request baca persentase baterai asli...")
                }

                // Coba subscribe Heart Rate jika ada 0x180D
                val hrService = gatt.getService(HEART_RATE_SERVICE)
                val hrChar = hrService?.getCharacteristic(HEART_RATE_CHAR)
                if (hrChar != null) {
                    gatt.setCharacteristicNotification(hrChar, true)
                    val desc = hrChar.getDescriptor(CLIENT_CONFIG_DESCRIPTOR)
                    if (desc != null) {
                        desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(desc)
                        addLog("[GATT] Subscribe detak jantung 0x2A37 aktif!")
                    }
                }
            } else {
                addLog("[BLE ERR] Gagal discover services, status $status")
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic.uuid == BATTERY_CHAR_UUID) {
                    val batt = characteristic.value?.firstOrNull()?.toInt()?.and(0xFF) ?: 0
                    _batteryLevel.value = batt
                    addLog("[BATERAI ASLI] SINKRON: $batt%")
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == HEART_RATE_CHAR) {
                val value = characteristic.value
                if (value != null && value.isNotEmpty()) {
                    val hr = value[1].toInt().and(0xFF)
                    _heartRate.value = hr
                    addLog("[HR ASLI] Detak jantung live: $hr BPM")
                }
            }
        }
    }

    private fun encryptAes(keyBytes: ByteArray, dataBytes: ByteArray): ByteArray {
        val keySpec = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        return cipher.doFinal(dataBytes)
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
