package com.example.cafe

import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result
import com.google.gson.Gson
import kotlinx.coroutines.*


class MainActivity : AppCompatActivity() {
    // TODO: 本番環境のURLに変える
    private val SIGN_IN_URL: String = "auth/sign_in"
    private val SIGN_UP_URL: String = "auth"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        super.onResume()

        val DB = DatabaseHelper(this)
        val db: SQLiteDatabase? = DB.writableDatabase
        val RailsServer = Server(this)
        val BLE = BleScanReceiver(this.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
        var token: String = ""

        BLE.request(this, ::startActivityForResult )
        DB.existDB(db)

        if (DB.existUser()) {
            val user: UserModel = DB.getUser()
            val (request, response, result) = RailsServer.PostJSON(SIGN_IN_URL,
                "{\"session\": {\"email\":\"${user.email}\", \"password\":\"${user.password}\"}}"
            )
            when(result){
                is Result.Success -> {
                    token = response["access-token"].toString()
                    ChangeUserActiviry(user)
                    BLE.startBleScan(this)
                }
                is Result.Failure -> {
                    // TODO: エラーをなんか考えとく
                }
            }
        } else {
            val manager: BluetoothManager =
                getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter: BluetoothAdapter? = manager.adapter

            val EmailForm: EditText = findViewById<EditText>(R.id.EmailAddress)
            val PasswordForm: EditText = findViewById<EditText>(R.id.Password)
            val NameForm: EditText = findViewById<EditText>(R.id.Personname)
            val SignUpbtn: Button = findViewById<Button>(R.id.SignUpBtn)
            val PBar: ProgressBar = findViewById<ProgressBar>(R.id.progressBar)

            val BLEAddress: String = adapter!!.address.toString()

            SignUpbtn.setOnClickListener {
                val CurrentUser = UserModel(
                    EmailForm.text.toString(),
                    PasswordForm.text.toString(),
                    NameForm.text.toString(),
                    BLEAddress
                )

                val body = Gson().toJson(CurrentUser)
                var flag = false

                val (request, response, result) = RailsServer.PostJSON(SIGN_UP_URL, "{\"registration\":" + body + "}")
                when(result){
                    is Result.Success -> {
                        token = response["access-token"].toString()
                        DB.saveUser(CurrentUser)
                        ChangeUserActiviry(CurrentUser)
                    }
                    is Result.Failure -> {
                        // TODO: already existsの場合はsign_inに飛ばすとかする
                        PBar.setProgress(50, true)
                    }
                }
            }
        }
    }

    fun ChangeUserActiviry(user: UserModel){
        setContentView(R.layout.activity_main2)

        val NameCol: TextView = findViewById<TextView>(R.id.UserName)
        val BLECol: TextView = findViewById<TextView>(R.id.UserBLE)

        NameCol.text = user.name
        BLECol.text = user.ble_address
    }
}

/**
 * Bluetooth通信をまとめたクラス
 * https://qiita.com/KentaHarada/items/4a9072379113c311d27d 参照
 */
class BleScanReceiver(Bmanager: BluetoothManager) : BroadcastReceiver() {
    private val REQUEST_ENABLEBLUETOOTH: Int = 1
    private val manager: BluetoothManager = Bmanager
    private val adapter: BluetoothAdapter = manager.adapter

    override fun onReceive(context: Context?, intent: Intent?) {
        val error = intent?.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, -1)
        if (error != -1) {
            return
        }

        val callbackType = intent?.getIntExtra(BluetoothLeScanner.EXTRA_CALLBACK_TYPE, -1)

        val scanResults: ArrayList<ScanResult> = intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT)!!
        for (scanResult in scanResults) {
            scanResult.device.name?.let {
                // TODO: サーバーに送る
            }
        }
        // stopBleScan(context)
    }

    private fun createBleScanPendingIntent(context: Context): PendingIntent {
        val requestCode = 222
        val intent = Intent(context, BleScanReceiver::class.java)
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun startBleScan(context: Context) {
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()

        val scanFilters = listOf(ScanFilter.Builder().build())
        val pendingIntent = createBleScanPendingIntent(context)

        // BLEスキャン開始
        adapter?.bluetoothLeScanner?.startScan(scanFilters, scanSettings, pendingIntent).let {
            if(it != 0) {
                // BLEスキャン失敗
            }
        }
    }

    private fun stopBleScan(context: Context?) {
        val pendingIntent = context?.let { createBleScanPendingIntent(it) }
        adapter?.bluetoothLeScanner?.stopScan(pendingIntent)
    }

    fun request(context: Context, func: (Intent, Int) -> Unit ) {
        if(adapter == null){
            Toast.makeText(context, R.string.bluetooth_is_not_supported, Toast.LENGTH_LONG).show()
        } else {
            if (adapter.isEnabled()) {
            } else {
                var enableBtIntent: Intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                func(enableBtIntent, REQUEST_ENABLEBLUETOOTH)
            }
        }
    }
}

/**
 * データベースを操作するクラス
 * http://www.gigas-jp.com/appnews/archives/7584 参照
 */
class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    companion object {
        private val DB_VERSION = 1
        private val DB_NAME = "cafe_dev.db"

        private val TABLE_NAME         = "cafeUser"
        private val COLUMN_ID          = "id"
        private val COLUMN_NAME        = "name"
        private val COLUMN_EMAIL       = "email"
        private val COLUMN_PASSWORD    = "password"
        private val COLUMN_BLE_ADDRESS = "ble_address"

        private val SQL_CREATE_TABLE = String.format(
            "CREATE TABLE %s (%s INTEGER PRIMARY KEY AUTOINCREMENT,%s TEXT NOT NULL,%s TEXT NOT NULL, %s TEXT NOT NULL, %s TEXT NOT NULL);",
            TABLE_NAME,
            COLUMN_ID,
            COLUMN_NAME,
            COLUMN_EMAIL,
            COLUMN_PASSWORD,
            COLUMN_BLE_ADDRESS
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

    fun saveUser(user: UserModel) {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(COLUMN_EMAIL, user.email)
        values.put(COLUMN_NAME, user.name)
        values.put(COLUMN_PASSWORD, user.password) //TODO: 暗号化する？
        values.put(COLUMN_BLE_ADDRESS, user.ble_address)
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

    fun getUser(): UserModel {
        val db = this.writableDatabase
        val userSQL = "SELECT * from $TABLE_NAME WHERE ID = (SELECT MAX(ID)  FROM $TABLE_NAME);"
        val cursor: Cursor = db.rawQuery(userSQL, null)
        var email: String = ""
        var password: String = ""
        var name: String = ""
        var ble: String = ""
        lateinit var user: UserModel
        try{
            if(cursor.moveToNext()){
                email = cursor.getString(cursor.getColumnIndex(COLUMN_EMAIL))
                name = cursor.getString(cursor.getColumnIndex(COLUMN_NAME))
                password = cursor.getString(cursor.getColumnIndex(COLUMN_PASSWORD))
                ble  = cursor.getString(cursor.getColumnIndex(COLUMN_BLE_ADDRESS))
            }
        } finally {
            user = UserModel(email, password, name, ble)
            cursor.close()
        }
        return user
    }

    fun existDB(db: SQLiteDatabase?){
        // this.onUpgrade(db, 1, 1)
        if (db == null) {
            this.onCreate(db)
        }
    }
}

/**
 * ユーザが入力したデータをJsonに変換するためのユーザークラス
 */
class UserModel(email: String, password: String, name: String, ble_address: String){
    val email = email
    val password = password
    val name = name
    val ble_address = ble_address
}

/**
 * サーバーと通信するためのクラス
 */
class Server(context: Context){
    private val ROUTE_URL = "http://202.13.162.197:3000/"

    fun PostJSON(URL: String, body: String): Triple<Request, Response, Result<String, FuelError>>{
        val header: HashMap<String, String> = hashMapOf("Content-Type" to "application/json")
        return runBlocking(Dispatchers.Default) { (ROUTE_URL+URL).httpPost().header(header).body(body).awaitStringResponseResult() }
    }
}
