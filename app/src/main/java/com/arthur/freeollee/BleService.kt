package com.arthur.freeollee

import android.app.*
import android.bluetooth.*
import android.content.*
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.*

class BleService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null
    private lateinit var notificationManager: NotificationManager
    private var gatt: BluetoothGatt? = null
    private var deviceAddress: String? = null
    private lateinit var prefs: SharedPreferences

    private var isConnecting = false
    private var isConnected = false
    private var servicesReady = false

    private var pendingValue: String? = null

    companion object {
        const val CHANNEL_ID = "ble_service_channel"
        const val ACTION_SEND_VALUE = "com.arthur.freeollee.SEND_VALUE"

        val SERVICE_UUID =
            UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

        val CHAR_UUID =
            UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
    }

    // ========================
    // RECEIVER VALEUR EXTERNE
    // ========================

    private val valueReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

            val value = intent.getStringExtra("value") ?: return

            val formatted = value.padEnd(6, ' ')
            pendingValue = formatted

            log("📥 Broadcast reçu: $value → $formatted")

            trySend()
        }
    }

    // ========================
    // BLUETOOTH STATE
    // ========================

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {

                val state = intent.getIntExtra(
                    BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR
                )

                when (state) {

                    BluetoothAdapter.STATE_OFF -> {
                        log("🔴 Bluetooth OFF")
                        isConnected = false
                        servicesReady = false
                        gatt?.close()
                        gatt = null
                    }

                    BluetoothAdapter.STATE_ON -> {
                        log("🟢 Bluetooth ON → reconnexion")
                        Handler(Looper.getMainLooper()).postDelayed({
                            connect()
                        }, 1000)
                    }
                }
            }
        }
    }

    // ========================
    // LIFECYCLE
    // ========================

    override fun onCreate() {
        super.onCreate()

        prefs = getSharedPreferences("data", MODE_PRIVATE)
        deviceAddress = prefs.getString("device_address", null)

        notificationManager = getSystemService(NotificationManager::class.java)

        createNotificationChannel()
        startForeground(1, createNotification("🔄 Initialisation..."))

        // ✅ Receiver Bluetooth
        registerReceiver(
            btReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        )

        // ✅ Receiver valeur externe
        registerReceiver(
            valueReceiver,
            IntentFilter(ACTION_SEND_VALUE),
            RECEIVER_EXPORTED
        )

        log("🚀 Service créé")

        Handler(Looper.getMainLooper()).post {
            connect()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(btReceiver)
        unregisterReceiver(valueReceiver)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        intent?.getStringExtra("device_address")?.let {
            deviceAddress = it
            prefs.edit().putString("device_address", it).apply()
        }

        val value = intent?.getStringExtra("value")

        if (value != null) {
            val formatted = value.take(4).padStart(4, ' ')
            pendingValue = formatted
            log("📥 Intent reçu: $value → $formatted")
            trySend()
        }

        if (gatt == null && !isConnecting) {
            connect()
        }

        return START_STICKY
    }

    // ========================
    // CONNECTION
    // ========================

    private fun connect() {

        if (isConnecting) return

        val addr = deviceAddress ?: run {
            log("❌ Pas d'adresse")
            return
        }

        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val device = manager.adapter.getRemoteDevice(addr)

        gatt?.close()
        gatt = null

        log("🔗 Connexion à $addr")

        isConnecting = true

        gatt = device.connectGatt(
            this,
            false,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE
        )
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {

            isConnecting = false

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("✅ Connecté")

                isConnected = true
                servicesReady = false
                gatt = g

                g.discoverServices()
                updateNotification("🟢 Connecté")
            }

            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("❌ Déconnecté")

                isConnected = false
                servicesReady = false

                gatt?.close()
                gatt = null

                updateNotification("🔴 Reconnexion...")

                Handler(Looper.getMainLooper()).postDelayed({
                    connect()
                }, 3000)
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            servicesReady = true
            Handler(Looper.getMainLooper()).postDelayed({
                trySend()
            }, 500)
        }
    }

    // ========================
    // ENVOI
    // ========================

    private fun trySend() {

        val value = pendingValue ?: return

        if (!isConnected || !servicesReady) {
            log("⏳ Pas prêt")
            return
        }

        sendToWatch(value)
        pendingValue = null
    }

    private fun sendToWatch(value: String) {

        val g = gatt ?: return

        val service = g.getService(SERVICE_UUID) ?: return
        val charac = service.getCharacteristic(CHAR_UUID) ?: return

        val payload = byteArrayOf(
            0x02, 0x2f
        ) + value.toByteArray(Charsets.US_ASCII)

        val crc = crc16(payload)

        val packet = byteArrayOf(
            0x00,
            (payload.size + 4).toByte(),
            0xaa.toByte(),
            0x55,
            (crc shr 8).toByte(),
            (crc and 0xFF).toByte()
        ) + payload

        charac.value = packet
        charac.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        g.writeCharacteristic(charac)

        log("📤 Envoyé → $value")
    }

    private fun crc16(data: ByteArray): Int {
        var crc = 0xFFFF
        for (b in data) {
            crc = crc xor ((b.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if ((crc and 0x8000) != 0)
                    (crc shl 1) xor 0x1021
                else crc shl 1
                crc = crc and 0xFFFF
            }
        }
        return crc
    }

    // ========================
    // NOTIF + LOG
    // ========================

    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BLE → Watch")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        notificationManager.notify(1, createNotification(text))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "BLE Service",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }

    private fun log(msg: String) {
        Log.d("BleService", msg)
    }
}