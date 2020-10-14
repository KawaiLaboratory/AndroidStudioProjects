package com.example.myapplication

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.companion.BluetoothLeDeviceFilter
import android.content.Context
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.widget.Toast
import pub.devrel.easypermissions.EasyPermissions
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        super.onResume()

        val bluetoothManager: BluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
        val REQUEST_ENABLE_BT: Int = 1
        val REQUEST_CODE = 150
        val SERVICE_UUID = ParcelUuid(UUID.fromString("895D1816-CC8A-40E3-B5FC-42D8ED441E50"))
        val permissions = arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.INTERNET
        )

        if(!EasyPermissions.hasPermissions(this, *permissions)){
            EasyPermissions.requestPermissions(this, "パーミッションに関する説明", REQUEST_CODE, *permissions)
            return
        }

        when{
            (bluetoothAdapter == null) -> {
                finish()
            }
            (packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) -> {
                val scanner= bluetoothAdapter.bluetoothLeScanner
                val scanFilter: ScanFilter = ScanFilter.Builder().setServiceUuid(SERVICE_UUID).build()
                val scanFilterList: ArrayList<ScanFilter> = arrayListOf(scanFilter)
                val scanSettings: ScanSettings = ScanSettings.Builder().setScanMode(
                    ScanSettings.SCAN_MODE_BALANCED
                ).build()
                val callback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult?) {
                        super.onScanResult(callbackType, result)
                        Log.d("scanResult", result!!.device.address)
                    }
                    override fun onScanFailed(errorCode: Int) {
                        super.onScanFailed(errorCode)
                        Log.d("scanResult", errorCode.toString())
                    }
                }
                scanner.startScan(scanFilterList, scanSettings, callback)
            }
            else -> {
                finish()
            }
        }
    }
}