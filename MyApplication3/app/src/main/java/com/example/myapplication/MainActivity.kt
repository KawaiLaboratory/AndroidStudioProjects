package com.example.myapplication

import android.Manifest
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.companion.BluetoothLeDeviceFilter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.PRIORITY_MIN
import pub.devrel.easypermissions.EasyPermissions
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {
    val REQUEST_ENABLE_BT = 160

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        /** パーミッションの確認 **/
        val REQUEST_CODE = 150
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

        val bluetooth = BluetoothClass(this)
        val randomHex = (0..0xFFFFFFF).random().toString()
        val advertiseIntent = Intent(this, BleScanService::class.java)

        when(bluetooth.request()){
            bluetooth.BT_NOT_SUPPORTED -> {
                Toast.makeText(this, "BT not Supported", Toast.LENGTH_SHORT).show()
                finish()
            }
            bluetooth.BLE_NOT_SUPPORTED -> {
                Toast.makeText(this, "BLE not Supported", Toast.LENGTH_SHORT).show()
                finish()
            }
            bluetooth.BLE_NOT_RUNNNING -> {
                val enableBtIntent: Intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                startForegroundService(advertiseIntent)
            }
            else ->{
                startForegroundService(advertiseIntent)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when(requestCode){
            REQUEST_ENABLE_BT -> {
                when(resultCode){
                    Activity.RESULT_CANCELED -> {
                        Toast.makeText(this,"BLE not running",Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}

class BluetoothClass(val context: Context){
    private val SERVICE_UUID = ParcelUuid(UUID.fromString("895D1816-CC8A-40E3-B5FC-42D8ED441E50"))
    private val DATA_UUID    = ParcelUuid(UUID.fromString("00004376-0000-1000-8000-00805F9B34FB"))
    val bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    val BT_NOT_SUPPORTED  = -3
    val BLE_NOT_SUPPORTED = -2
    val BLE_NOT_RUNNNING  = -1
    val BLE_RUNNNING      = 0
    val REQUEST_SCAN_BLE  = 170

    fun request(): Int{
        return when{
            (bluetoothAdapter == null) -> {
                BT_NOT_SUPPORTED
            }
            (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) -> {
                BLE_NOT_SUPPORTED
            }
            (bluetoothAdapter.isEnabled) -> {
                BLE_RUNNNING
            }
            else -> {
                BLE_NOT_RUNNNING
            }
        }
    }

    private fun startBleScan(){
        val scanner= bluetoothAdapter!!.bluetoothLeScanner
        val scanFilter: ScanFilter = ScanFilter.Builder().setServiceUuid(SERVICE_UUID).build()
        val scanFilterList: ArrayList<ScanFilter> = arrayListOf(scanFilter)
        val scanSettings: ScanSettings = ScanSettings.Builder().setScanMode(
            ScanSettings.SCAN_MODE_LOW_POWER
        ).build()
        val scanIntent = Intent(context, BleScanReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_SCAN_BLE, scanIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        scanner.startScan(scanFilterList, scanSettings, pendingIntent)
    }

    private fun startBleAdvertise(){
        val advertiser: BluetoothLeAdvertiser = bluetoothAdapter!!.bluetoothLeAdvertiser
        val advertiseSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setConnectable(false)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM).build()
        val advertiseData: AdvertiseData = AdvertiseData.Builder()
            .addServiceUuid(SERVICE_UUID)
            .addServiceData(DATA_UUID, "aaaa".toByteArray(Charsets.UTF_8))
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
        advertiser.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
    }

    fun startScanAndAdvertise(){
        startBleScan()
        startBleAdvertise()
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
                println(scanResult.scanRecord!!.serviceData[ParcelUuid(UUID.fromString("00004376-0000-1000-8000-00805F9B34FB"))]!!.toString(Charsets.UTF_8))
            }
        }
    }
}

class BleScanService : Service(){
    override fun onBind(intent: Intent?): IBinder? {
        TODO("Not yet implemented")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val channelId = "my_service"
        val channelName = "My Background Service"
        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_NONE
        )
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val context = applicationContext
        val bluetooth = BluetoothClass(context)

        channel.description = "Silent Notification"
        channel.setSound(null,null)
        channel.enableLights(false)
        channel.lightColor = Color.BLUE
        channel.enableVibration(false)
        channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        service.createNotificationChannel(channel)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
        val notification = notificationBuilder.setOngoing(true)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(PRIORITY_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        bluetooth.startScanAndAdvertise()

        startForeground(101, notification)

        return START_NOT_STICKY
    }
}
