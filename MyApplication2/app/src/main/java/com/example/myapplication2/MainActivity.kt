package com.example.myapplication2

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.ParcelUuid
import android.widget.Toast
import pub.devrel.easypermissions.EasyPermissions
import java.util.*

class MainActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        super.onResume()

        val bluetoothManager: BluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
        val REQUEST_ENABLE_BT: Int = 1
        val SERVICE_UUID: String = "895D1816-CC8A-40E3-B5FC-42D8ED441E50"
        val USER_UUID = UUID.randomUUID().toString()
        val permissions = arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION)
        val REQUEST_CODE = 0

        if(!EasyPermissions.hasPermissions(this, *permissions)){
            EasyPermissions.requestPermissions(this, "パーミッションに関する説明", REQUEST_CODE, *permissions)
            return
        }

        when(bluetoothAdapter) {
            null -> {
                Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show()
                finish()
            }
            else -> {
                when(packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)){
                    true -> {
                        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        startActivityForResult(intent, REQUEST_ENABLE_BT)

                        val scanner= bluetoothAdapter.bluetoothLeScanner
                        val callback = object : ScanCallback() {
                            override fun onScanResult(callbackType: Int, result: ScanResult) {
                                super.onScanResult(callbackType, result)
                                Toast.makeText(this@MainActivity, "received", Toast.LENGTH_LONG).show()
                            }
                            override fun onScanFailed(errorCode: Int) {
                                super.onScanFailed(errorCode)
                                Toast.makeText(this@MainActivity, "error", Toast.LENGTH_LONG).show()
                            }
                        }
                        scanner.startScan(callback)
                    }
                    else -> {
                        Toast.makeText(this, "BLE not supported", Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        val bluetoothManager: BluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
        when(bluetoothAdapter) {
            null -> {
                return
            }
            else -> {
                val scanner= bluetoothAdapter.bluetoothLeScanner
                val callback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        super.onScanResult(callbackType, result)
                        Toast.makeText(this@MainActivity, "received", Toast.LENGTH_LONG).show()
                    }
                    override fun onScanFailed(errorCode: Int) {
                        super.onScanFailed(errorCode)
                        Toast.makeText(this@MainActivity, "error", Toast.LENGTH_LONG).show()
                    }
                }
                scanner.stopScan(callback)
            }
        }


        super.onDestroy()
    }

    override fun onPermissionsGranted(requestCode: Int, list: List<String>) {
        recreate()
    }

    override fun onPermissionsDenied(requestCode: Int, list: List<String>) {
        finish()
    }
}