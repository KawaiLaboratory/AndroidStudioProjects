package com.example.myapplication

import android.Manifest
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.PRIORITY_MIN
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.core.ResponseResultOf
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import pub.devrel.easypermissions.EasyPermissions
import java.util.*
import kotlin.collections.ArrayList

lateinit var commonHeaderClass: HeaderClass

class MainActivity : AppCompatActivity() {
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
            Manifest.permission.INTERNET,
            Manifest.permission.FOREGROUND_SERVICE
        )

        if(!EasyPermissions.hasPermissions(this, *permissions)){
            EasyPermissions.requestPermissions(this, "パーミッションに関する説明", REQUEST_CODE, *permissions)
            return
        }

        val DB = DatabaseHelper(this)
        val bluetooth = BluetoothClass(this)
        val server = RailsServer()

        DB.existDB()

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
//                val enableBtIntent: Intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
//                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                Toast.makeText(this, "BLE not Running", Toast.LENGTH_SHORT).show()
                finish()
            }
            else ->{
                val advertiseIntent = Intent(this, BleScanService::class.java)
                when{
                    DB.existUser() -> {
                        val user = DB.getUser()
                        var (result, railsUser, errorMsg) = server.signIn(user)
                        when{
                            result -> {
                                startForegroundService(advertiseIntent)
                                setContentView(R.layout.activity_user)

                                val userName = findViewById<TextView>(R.id.userName)
                                val bleToken = findViewById<TextView>(R.id.bleToken)

                                userName.text = railsUser.name
                                bleToken.text = railsUser.ble_token
                            }
                            else -> { }
                        }
                    }
                    else -> {
                        val emailForm = findViewById<EditText>(R.id.emailForm)
                        val passwordForm = findViewById<EditText>(R.id.passwordForm)
                        val nameForm = findViewById<EditText>(R.id.nameForm)
                        val btn = findViewById<Button>(R.id.btn)

                        btn.setOnClickListener {
                            val user: UserClass = UserClass(
                                emailForm.text.toString(),
                                passwordForm.text.toString(),
                                nameForm.text.toString()
                            )
                            var (result, railsUser, errorMsg) = server.signUp(user, DB)
                            when{
                                result -> {
                                    startForegroundService(advertiseIntent)
                                    setContentView(R.layout.activity_user)

                                    val userName = findViewById<TextView>(R.id.userName)
                                    val bleToken = findViewById<TextView>(R.id.bleToken)

                                    userName.text = railsUser.name
                                    bleToken.text = railsUser.ble_token
                                }
                                else -> { }
                            }
                        }
                    }
                }
            }
        }
    }
}

class RailsServer(){
    // private val ROUTE_URL = "http://202.13.162.197:3000/"
    private val ROUTE_URL = "https://klab-api-server.herokuapp.com/"
    private val SIGN_IN_URL: String = "auth/sign_in"
    private val SIGN_UP_URL: String = "auth"
    private val LOG_CREATE_URL: String = "log"
    private val HTTP_CONFRICT = 409

    private fun postJson(URL: String, body: String, head: HashMap<String, String> = hashMapOf("Content-Type" to "application/json")): ResponseResultOf<ByteArray> {
        return runBlocking(Dispatchers.Default) {
            (ROUTE_URL+URL).httpPost()
                .header(head)
                .body(body)
                .response()
        }
    }

    fun signIn(user: UserClass): Triple<Boolean, UserClass, HashMap<String?, String?>>{
        val body = Gson().toJson(
            hashMapOf(
                "session" to
                        hashMapOf(
                            "email" to user.email,
                            "password" to user.password
                        )
            )
        )
        val (_, response, result) = this.postJson(
            SIGN_IN_URL,
            body
        )
        return when(result){
            is Result.Success -> {
                val headerClass = HeaderClass(response)
                val hashData = Gson().fromJson(
                    response.data.toString(Charsets.UTF_8),
                    HashMap::class.java
                )
                val serverData = Gson().fromJson(
                    Gson().toJson(hashData["data"]),
                    HashMap::class.java
                )

                val userClass = UserClass(
                    user.email!!,
                    user.password!!,
                    serverData["name"].toString(),
                    serverData["ble_token"].toString()
                )
                commonHeaderClass = headerClass
                Triple(true, userClass, hashMapOf("" to ""))
            }
            is Result.Failure -> {
                Triple(false, user, hashMapOf("" to ""))
            }
        }
    }

    fun signUp(user: UserClass, DB: DatabaseHelper): Triple<Boolean, UserClass, HashMap<String?, String?>>{
        val body = Gson().toJson(
            hashMapOf(
                "registration" to user
            )
        )
        val (_, response, result) = this.postJson(
            SIGN_UP_URL,
            body
        )
        val headerClass = HeaderClass(response)
        when(result){
            is Result.Success -> {
                DB.saveUser(user)
                commonHeaderClass = headerClass
                return Triple(true, user, hashMapOf("" to ""))
            }
            is Result.Failure -> {
                when(response.statusCode){
                    HTTP_CONFRICT -> {
                        val (_result, _user) = signIn(user)
                        return when(_result){
                            true -> {
                                DB.saveUser(_user)
                                Triple(_result, _user, hashMapOf("" to ""))
                            }
                            false -> {
                                // TODO: エラー文追加
                                Triple(false, user, hashMapOf("" to ""))
                            }
                        }
                    }
                    else -> {
                        // TODO: エラー文追加
                        return Triple(false, user, hashMapOf("" to ""))
                    }
                }
            }
        }
    }

    fun createLog(bleToken: String, RSSI: Int, txPower: Int){
        val head: HashMap<String, String> = hashMapOf(
            "Content-Type" to "application/json",
            "uid" to commonHeaderClass.uid!!,
            "access-token" to commonHeaderClass.token!!,
            "client" to commonHeaderClass.client!!
        )
        val body = Gson().toJson(
            hashMapOf(
                "log" to hashMapOf(
                    "detected_ble_token" to bleToken,
                    "rssi" to RSSI,
                    "txpower" to txPower
                )
            )
        )
        val (_, response, result) = this.postJson(
            LOG_CREATE_URL,
            body,
            head
        )
        commonHeaderClass = HeaderClass(response)
    }
}

class BluetoothClass(private val context: Context){
    private val SERVICE_UUID = ParcelUuid(UUID.fromString("895D1816-CC8A-40E3-B5FC-42D8ED441E50"))
    private val DATA_UUID    = ParcelUuid(UUID.fromString("00004376-0000-1000-8000-00805F9B34FB"))
    private val DB = DatabaseHelper(context)
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
        val user = DB.getUser()
        val advertiseSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setConnectable(false)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM).build()
        val advertiseData: AdvertiseData = AdvertiseData.Builder()
            .addServiceUuid(SERVICE_UUID)
            .addServiceData(DATA_UUID, user.ble_token!!.toByteArray(Charsets.UTF_8))
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

class UserClass(email: String, password: String, name: String, ble_token: String = String.format("%09d", (0..0xFFFFFFF).random())){
    val email: String? = email
    val password: String? = password
    val name: String? = name
    val ble_token: String? = ble_token
}

class HeaderClass(response: Response){
    var uid: String? = response["uid"].toString().removePrefix("[").removeSuffix("]")
    var token: String? = response["access-token"].toString().removePrefix("[").removeSuffix("]")
    var client: String? = response["client"].toString().removePrefix("[").removeSuffix("]")
}

class BleScanReceiver : BroadcastReceiver() {
    private val DATA_UUID = ParcelUuid(UUID.fromString("00004376-0000-1000-8000-00805F9B34FB"))
    private val server = RailsServer()

    override fun onReceive(context: Context?, intent: Intent?) {
        val error = intent!!.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, -1)
        if (error != -1) {
            Log.d("error", "BLE Scan error : $error")
            return
        }
        val scanResults: ArrayList<ScanResult> = intent!!.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT)!!
        for (scanResult in scanResults) {
            scanResult.let {
                var bleTokenData = scanResult.scanRecord!!.serviceData[DATA_UUID]!!.toString(Charsets.UTF_8)
                var rssi = scanResult.rssi
                var txPower = scanResult.txPower
                server.createLog(bleTokenData, rssi, txPower)
                Toast.makeText(context, "Detected : $bleTokenData \n" +
                        "RSSI : $rssi\n" +
                        "TxPower: $txPower", Toast.LENGTH_SHORT).show()
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

    override fun onDestroy() {
        super.onDestroy()
        stopSelf()
    }
}

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    companion object {
        private val DB_VERSION = 1
        private val DB_NAME = "myapp_dev.db"

        private val TABLE_NAME       = "cafeUser"
        private val COLUMN_ID        = "id"
        private val COLUMN_NAME      = "name"
        private val COLUMN_EMAIL     = "email"
        private val COLUMN_PASSWORD  = "password"
        private val COLUMN_BLE_TOKEN = "ble_token"

        private val SQL_CREATE_TABLE = String.format(
            "CREATE TABLE %s (%s INTEGER PRIMARY KEY AUTOINCREMENT,%s TEXT NOT NULL,%s TEXT NOT NULL, %s TEXT NOT NULL, %s TEXT NOT NULL);",
            TABLE_NAME,
            COLUMN_ID,
            COLUMN_NAME,
            COLUMN_EMAIL,
            COLUMN_PASSWORD,
            COLUMN_BLE_TOKEN
        )

        private val SQL_DROP_TABLE = String.format("DROP TABLE IF EXISTS %s;", TABLE_NAME)
    }
    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL(SQL_CREATE_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL(SQL_DROP_TABLE)
        onCreate(db)
    }

    fun saveUser(user: UserClass) {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(COLUMN_EMAIL, user.email)
        values.put(COLUMN_NAME, user.name)
        values.put(COLUMN_PASSWORD, user.password) //TODO: 暗号化する
        values.put(COLUMN_BLE_TOKEN, user.ble_token)
        db.insert(TABLE_NAME, null, values)
        db.close()
    }

    fun existUser(): Boolean {
        val db = this.writableDatabase
        val userSQL = "SELECT count(*) as cnt from $TABLE_NAME;"
        val cursor: Cursor = db.rawQuery(userSQL, null)
        cursor.moveToFirst()
        val count: Int = cursor.getInt(cursor.getColumnIndex("cnt"))
        cursor.close()
        return (count > 0)
    }

    fun getUser(): UserClass {
        val db = this.writableDatabase
        val userSQL = "SELECT * from $TABLE_NAME WHERE ID = (SELECT MAX(ID)  FROM $TABLE_NAME);"
        val cursor: Cursor = db.rawQuery(userSQL, null)
        var email: String = ""
        var password: String = ""
        var name: String = ""
        var ble: String = ""
        lateinit var user: UserClass
        try{
            when {
                cursor.moveToNext() -> {
                    email = cursor.getString(cursor.getColumnIndex(COLUMN_EMAIL))
                    name = cursor.getString(cursor.getColumnIndex(COLUMN_NAME))
                    password = cursor.getString(cursor.getColumnIndex(COLUMN_PASSWORD))
                    ble  = cursor.getString(cursor.getColumnIndex(COLUMN_BLE_TOKEN))
                }
            }
        } finally {
            user = UserClass(email, password, name, ble)
            cursor.close()
        }
        return user
    }

    fun existDB(){
        val db: SQLiteDatabase? = this.writableDatabase
        // this.onUpgrade(db, 1, 1)
        when (db) {
            null -> {
                this.onCreate(db)
            }
        }
    }
}