package com.arthur.freeollee

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.app.AlertDialog

class MainActivity : AppCompatActivity() {

    private lateinit var textView: TextView
    private lateinit var btnPermission: Button
    private lateinit var btnSelectDevice: Button

    private var currentStatus: String = ""

    // ========================
    // RECEIVER STATUS UNIQUEMENT
    // ========================

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            currentStatus = intent.getStringExtra("status") ?: ""
            updateUI()
        }
    }

    // ========================
    // LIFECYCLE
    // ========================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40)
        }

        textView = TextView(this).apply {
            textSize = 18f
            gravity = Gravity.CENTER
        }

        btnPermission = Button(this).apply {
            text = getString(R.string.permission_button)
        }

        btnSelectDevice = Button(this).apply {
            text = getString(R.string.select_device)
        }

        layout.addView(textView)
        layout.addView(btnPermission)
        layout.addView(btnSelectDevice)

        setContentView(layout)

        btnPermission.setOnClickListener { requestPermissions() }
        btnSelectDevice.setOnClickListener { showPairedDevices() }

        updateUI()

        startBleServiceSafe()
    }

    override fun onStart() {
        super.onStart()

        ContextCompat.registerReceiver(
            this,
            statusReceiver,
            IntentFilter("BLE_STATUS"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(statusReceiver)
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    // ========================
    // BLE SERVICE
    // ========================

    private fun startBleService(address: String) {
        val intent = Intent(this, BleService::class.java)
        intent.putExtra("device_address", address)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForegroundService(intent)
        else
            startService(intent)
    }

    private fun startBleServiceSafe() {
        val addr = getSharedPreferences("data", MODE_PRIVATE)
            .getString("device_address", null)

        if (addr != null) {
            startBleService(addr)
        } else {
            Toast.makeText(this, getString(R.string.no_device), Toast.LENGTH_SHORT).show()
        }
    }

    // ========================
    // UI SIMPLIFIÉE
    // ========================

    private fun updateUI() {

        val statusText = if (currentStatus.isNotEmpty())
            currentStatus
        else
            getString(R.string.inactive)

        textView.text = """
            🔵 ${getString(R.string.status)}: $statusText
        """.trimIndent()
    }

    // ========================
    // DEVICE SELECTION
    // ========================

    private fun disableBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            val intent = Intent(
                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            )

            intent.data = android.net.Uri.parse("package:$packageName")

            startActivity(intent)
        }
    }

    @SuppressLint("MissingPermission")
    private fun showPairedDevices() {

        if (!hasBluetoothPermission()) {
            Toast.makeText(this, getString(R.string.permission_button), Toast.LENGTH_SHORT).show()
            return
        }

        val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()

        if (adapter == null || !adapter.isEnabled) {
            Toast.makeText(this, "Bluetooth required", Toast.LENGTH_SHORT).show()
            return
        }

        val devices = adapter.bondedDevices

        if (devices.isEmpty()) {
            Toast.makeText(this, "No paired devices", Toast.LENGTH_SHORT).show()
            return
        }

        val list = devices.map { "${it.name} - ${it.address}" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_device))
            .setItems(list) { _, which ->

                val device = devices.elementAt(which)

                saveDevice(device.address)

                Toast.makeText(this, device.name, Toast.LENGTH_SHORT).show()

                startBleService(device.address)
            }
            .show()
    }

    private fun saveDevice(address: String) {
        getSharedPreferences("data", MODE_PRIVATE)
            .edit()
            .putString("device_address", address)
            .apply()
    }

    // ========================
    // PERMISSIONS
    // ========================

    private fun requestPermissions() {

        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
        }

        if (Build.VERSION.SDK_INT >= 34) {
            perms.add(Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        ActivityCompat.requestPermissions(this, perms.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        results: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, results)

        if (results.isNotEmpty() && results.all { it == PackageManager.PERMISSION_GRANTED }) {

            Toast.makeText(this, "Permissions OK", Toast.LENGTH_SHORT).show()

            disableBatteryOptimization()

            startBleServiceSafe()

        } else {
            Toast.makeText(this, "Permissions denied ❌", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        else true
    }
}