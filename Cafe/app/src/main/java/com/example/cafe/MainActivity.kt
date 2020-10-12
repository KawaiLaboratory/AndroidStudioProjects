package com.example.cafe

import android.app.Activity
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
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.core.ResponseResultOf
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.nio.charset.Charset


class MainActivity : AppCompatActivity() {
    val REQUEST_ENABLEBLUETOOTH: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        super.onResume()

        val DB = DatabaseHelper(this)
        val RailsServer = Server(this)
        val BLE = BleScanReceiver(
            this.getSystemService(Context.BLUETOOTH_SERVICE)
                    as BluetoothManager
        )

        when (BLE.request(this)) {
            BLE.BLE_NOT_SUPPORTED -> {
                finish()
            }
            BLE.BLE_NOT_WORKING -> {
                val enableBtIntent: Intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLEBLUETOOTH)
            }
            BLE.BLE_SUCCESS -> {
                DB.existDB()
                when (DB.existUser()) {
                    true -> {
                        val user: UserModel = DB.getUser()
                        var (result, railsUser, header) = RailsServer.SignIn(user)

                        when (result) {
                            true -> {
                                ChangeUserActiviry(railsUser)
                                BLE.startBleScan(this)
                                header = RailsServer.CreateLog(header)
                            }
                            false -> {
                                Toast.makeText(this, "error!", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    false -> {
                        val EmailForm: EditText = findViewById<EditText>(R.id.EmailAddress)
                        val PasswordForm: EditText = findViewById<EditText>(R.id.Password)
                        val NameForm: EditText = findViewById<EditText>(R.id.Personname)
                        val SignUpbtn: Button = findViewById<Button>(R.id.SignUpBtn)

                        val BLEAddress: String = BLE.getAddress()

                        SignUpbtn.setOnClickListener {
                            val user = UserModel(
                                EmailForm.text.toString(),
                                PasswordForm.text.toString(),
                                NameForm.text.toString(),
                                BLEAddress
                            )

                            var (result, railsUser, header) = RailsServer.SignUp(user, DB)

                            when (result) {
                                true -> {
                                    ChangeUserActiviry(railsUser)
                                    BLE.startBleScan(this)
                                }
                                false -> {
                                    Toast.makeText(this, "error!", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when(requestCode){
            REQUEST_ENABLEBLUETOOTH -> {
                when(resultCode){
                    Activity.RESULT_CANCELED -> {
                        Toast.makeText(
                            this,
                            R.string.bluetooth_is_not_working,
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}

/**
 * Bluetooth通信をまとめたクラス
 * https://qiita.com/KentaHarada/items/4a9072379113c311d27d 参照
 */
class BleScanReceiver(Bmanager: BluetoothManager) : BroadcastReceiver() {
    private val manager: BluetoothManager = Bmanager
    private val adapter: BluetoothAdapter? = manager.adapter

    val BLE_SUCCESS:       Int =  0
    val BLE_NOT_SUPPORTED: Int = -1
    val BLE_NOT_WORKING:   Int = -2

    override fun onReceive(context: Context?, intent: Intent?) {
        val error = intent?.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, -1)
        when {
            error != -1 -> {
                return
            }
            else -> {
                val callbackType = intent?.getIntExtra(BluetoothLeScanner.EXTRA_CALLBACK_TYPE, -1)

                val scanResults: ArrayList<ScanResult> = intent.getParcelableArrayListExtra(
                    BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT
                )!!
                for (scanResult in scanResults) {
                    scanResult.device.name?.let {
                        // TODO: サーバーに送る
                    }
                }
            }
        }
    }

    private fun createBleScanPendingIntent(context: Context): PendingIntent {
        val requestCode = 241
        val intent = Intent(context, BleScanReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun startBleScan(context: Context) {
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()

        val scanFilters = listOf(ScanFilter.Builder().build())
        val pendingIntent = createBleScanPendingIntent(context)

        // BLEスキャン開始
        adapter?.bluetoothLeScanner?.startScan(scanFilters, scanSettings, pendingIntent).let {
            when (it){
                0 -> {
                    Log.d("ble", "started")
                }
                else -> {
                    Log.d("ble", "error")
                }
            }
        }
    }

    private fun stopBleScan(context: Context?) {
        val pendingIntent = context?.let { createBleScanPendingIntent(it) }
        adapter?.bluetoothLeScanner?.stopScan(pendingIntent)
    }

    fun request(context: Context): Int {
        when(adapter){
            null -> {
                Toast.makeText(context, R.string.bluetooth_is_not_supported, Toast.LENGTH_LONG)
                    .show()
                return BLE_NOT_SUPPORTED
            }
            else -> {
                return when(adapter.isEnabled){
                    true -> {
                        BLE_SUCCESS
                    }
                    false -> {
                        BLE_NOT_WORKING
                    }
                }
            }
        }
    }

    fun getAddress(): String {
        return adapter!!.address.toString()
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
            when {
                cursor.moveToNext() -> {
                    email = cursor.getString(cursor.getColumnIndex(COLUMN_EMAIL))
                    name = cursor.getString(cursor.getColumnIndex(COLUMN_NAME))
                    password = cursor.getString(cursor.getColumnIndex(COLUMN_PASSWORD))
                    ble  = cursor.getString(cursor.getColumnIndex(COLUMN_BLE_ADDRESS))
                }
            }
        } finally {
            user = UserModel(email, password, name, ble)
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

/**
 * ユーザが入力したデータをJsonに変換するためのユーザークラス
 */
class UserModel(email: String, password: String, name: String, ble_address: String){
    val email: String? = email
    val password: String? = password
    val name: String? = name
    val ble_address: String? = ble_address
}

/**
 * サーバーレスポンスを整形したものの格納クラス
 */
class HeaderData(response: Response){
    var uid: String? = response["uid"].toString().removePrefix("[").removeSuffix("]")
    var token: String? = response["access-token"].toString().removePrefix("[").removeSuffix("]")
    var client: String? = response["client"].toString().removePrefix("[").removeSuffix("]")
}

/**
 * サーバーと通信するためのクラス
 */
class Server(context: Context){
    private val ROUTE_URL = "http://202.13.162.197:3000/"
    private val SIGN_IN_URL: String = "auth/sign_in"
    private val SIGN_UP_URL: String = "auth"
    private val LOG_CREATE_URL: String = "log"
    private val HTTP_CONFRICT = 409

    fun getUserResponse(user: UserModel, response: Response): Pair<UserModel, HeaderData>{
        val resHeader = HeaderData(response)
        val Data = Gson().fromJson(
            response.data.toString(Charsets.UTF_8),
            HashMap::class.java
        )
        val RailsData = Gson().fromJson(
            Gson().toJson(Data["data"]),
            HashMap::class.java
        )

        val user: UserModel = UserModel(
            user.email!!,
            user.password!!,
            RailsData["name"].toString(),
            RailsData["ble_address"].toString()
        )
        return Pair(user, resHeader)
    }

    fun SignIn(user: UserModel): Triple<Boolean, UserModel, HeaderData>{
        val (request, response, result) = this.PostJSON(
            SIGN_IN_URL,
            "{\"session\": {\"email\":\"${user.email}\", \"password\":\"${user.password}\"}}"
        )
        return when(result){
            is Result.Success -> {
                val (RailsUser, resHeader) = this.getUserResponse(user, response)
                Triple(true, RailsUser, resHeader)
            }
            is Result.Failure -> {
                val resHeader = HeaderData(response)
                Triple(false, user, resHeader)
            }
        }
    }

    fun SignUp(user: UserModel, DB: DatabaseHelper): Triple<Boolean, UserModel, HeaderData> {
        val (request, response, result) = this.PostJSON(
            SIGN_UP_URL,
            "{\"registration\":" + Gson().toJson(user) + "}"
        )
        val resHeader = HeaderData(response)
        when(result){
            is Result.Success -> {
                DB.saveUser(user)
                return Triple(true, user, resHeader)
            }
            is Result.Failure -> {
                when(response.statusCode){
                    HTTP_CONFRICT -> {
                        val (_result, _user, _header) = SignIn(user)
                        return when(_result){
                            true -> {
                                DB.saveUser(_user)
                                Triple(_result, _user, _header)
                            }
                            false -> {
                                Triple(false, user, _header)
                            }
                        }
                    }
                    else -> {
                        return Triple(false, user, resHeader)
                    }
                }
            }
        }
    }

    fun CreateLog(header: HeaderData): HeaderData{
        val head: HashMap<String, String> = hashMapOf(
            "Content-Type" to "application/json",
            "uid" to header.uid!!,
            "access-token" to header.token!!,
            "client" to header.client!!
        )
        val (request, response, result) = this.PostJSON(
            LOG_CREATE_URL,
            "{\"log\": {\"detected_ble_address\": \"AA:AA:AA:AA:AA:AA\", \"rssi\": -40}}",
            head
        )
        return HeaderData(response)
    }

    fun PostJSON(URL: String, body: String, head: HashMap<String, String> = hashMapOf("Content-Type" to "application/json")): ResponseResultOf<ByteArray> {
        return runBlocking(Dispatchers.Default) {
            (ROUTE_URL+URL).httpPost()
                .header(head)
                .body(body)
                .response()
        }
    }
}
