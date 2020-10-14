package com.example.myapplication

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import pub.devrel.easypermissions.EasyPermissions
import java.util.*


class MainActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks{
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    /**
     * https://qiita.com/sacred-sanctuary/items/b710d9bf37d0cd362648
     */
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
//                        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
//                        startActivityForResult(intent, REQUEST_ENABLE_BT)

                        val advertiser: BluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
                        val settings = AdvertiseSettings.Builder()
                                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                                .setConnectable(false)
                                .setTimeout(0)
                                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW).build()
                        val data: AdvertiseData = AdvertiseData.Builder().addServiceData(
                                ParcelUuid(UUID.fromString(SERVICE_UUID)),
                                USER_UUID.toByteArray()
                        ).build()

                        advertiser.startAdvertising(settings, data, object : AdvertiseCallback() {
                            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                                Toast.makeText(this@MainActivity, "started", Toast.LENGTH_LONG).show()
                                super.onStartSuccess(settingsInEffect)
                            }

                            override fun onStartFailure(errorCode: Int) {
                                Toast.makeText(this@MainActivity, "error!", Toast.LENGTH_LONG).show()
                                super.onStartFailure(errorCode)
                            }
                        })
                    }
                    else -> {
                        Toast.makeText(this, "BLE not supported", Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            }
        }
    }

    override fun onPause() {
        val bluetoothManager: BluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
        val advertiser: BluetoothLeAdvertiser = bluetoothAdapter!!.bluetoothLeAdvertiser

        super.onPause()
    }

    override fun onPermissionsGranted(requestCode: Int, list: List<String>) {
        recreate()
    }

    override fun onPermissionsDenied(requestCode: Int, list: List<String>) {
        finish()
    }
}

