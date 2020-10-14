package com.example.myapplication

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.widget.Toast
import pub.devrel.easypermissions.EasyPermissions
import java.util.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }


    override fun onResume() {
        super.onResume()

        val bluetoothManager: BluetoothManager =
            getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
        val REQUEST_ENABLE_BT: Int = 1
        val REQUEST_CODE = 150
        val SERVICE_UUID = ParcelUuid(UUID.fromString("895D1816-CC8A-40E3-B5FC-42D8ED441E50"))
        /**
         * https://stackoverflow.com/questions/50527426/ble-how-to-set-uuid-to-16bit
         */
        val DATA_UUID = ParcelUuid(UUID.fromString("00004376-0000-1000-8000-00805F9B34FB"))
        val USER_UUID =  UUID.randomUUID().toString().toByteArray()
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.INTERNET
        )

        if (!EasyPermissions.hasPermissions(this, *permissions)) {
            EasyPermissions.requestPermissions(this, "パーミッションに関する説明", REQUEST_CODE, *permissions)
            return
        }

        when {
            (bluetoothAdapter == null) -> {
                finish()
            }
            (packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) -> {
                val advertiser: BluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
                val settings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                    .setConnectable(false)
                    .setTimeout(0)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH).build()
                val data: AdvertiseData = AdvertiseData.Builder()
                    .addServiceUuid(SERVICE_UUID)
                    .addServiceData(DATA_UUID, "D".toByteArray(Charsets.UTF_8))
                    .build()
                val callback: AdvertiseCallback =  object : AdvertiseCallback() {
                    override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                        Log.d("adv", "success")
                        super.onStartSuccess(settingsInEffect)
                    }

                    override fun onStartFailure(errorCode: Int) {
                        Log.d("adv", errorCode.toString())
                        super.onStartFailure(errorCode)
                    }
                }
                advertiser.startAdvertising(settings, data, callback)
            }
            else -> {
                finish()
            }
        }
    }
}