package com.example.myapplication

import android.Manifest
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.companion.BluetoothLeDeviceFilter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
        val DATA_UUID = ParcelUuid(UUID.fromString("00004376-0000-1000-8000-00805F9B34FB"))
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
                /**
                 * バックグラウンド処理用変数
                 */
                val requestCode = 240
                val intent = Intent(this, BleScanReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT)
                /**
                 * BLE受信用変数
                 */
                val scanner= bluetoothAdapter.bluetoothLeScanner
                val scanFilter: ScanFilter = ScanFilter.Builder().setServiceUuid(SERVICE_UUID).build()
                val scanFilterList: ArrayList<ScanFilter> = arrayListOf(scanFilter)
                val scanSettings: ScanSettings = ScanSettings.Builder().setScanMode(
                    ScanSettings.SCAN_MODE_BALANCED
                ).build()

                /**
                 * BLE送信用変数
                 */
                val advertiser: BluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
                val advertiseSettings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                    .setConnectable(false)
                    .setTimeout(0)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM).build()
                val advertiseData: AdvertiseData = AdvertiseData.Builder()
                    .addServiceUuid(SERVICE_UUID)
                    .addServiceData(DATA_UUID, "D".toByteArray(Charsets.UTF_8))
                    .build()
                val advertiseCallback: AdvertiseCallback =  object : AdvertiseCallback() {
                    override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                        Log.d("adv", "success")
                        super.onStartSuccess(settingsInEffect)
                    }

                    override fun onStartFailure(errorCode: Int) {
                        Log.d("adv", errorCode.toString())
                        super.onStartFailure(errorCode)
                    }
                }
                /**
                 * BLE送受信の開始
                 */
                scanner.startScan(scanFilterList, scanSettings, pendingIntent)
                advertiser.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
            }
            else -> {
                finish()
            }
        }
    }
}

class BleScanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val error = intent!!.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, -1)
        if (error != -1) {
            Log.d("error", "BLE Scan error : $error")
            return
        }
        val scanResults: ArrayList<ScanResult> = intent!!.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT)!!
        for (scanResult in scanResults) {
            scanResult.let {
                Log.d("TxPower",
                    scanResult.txPower.toString())
                Log.d("RSSI", scanResult.rssi.toString())
            }
        }
    }
}
