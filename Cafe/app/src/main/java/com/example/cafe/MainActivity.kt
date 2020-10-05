package com.example.cafe

import android.animation.ObjectAnimator
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import android.util.Log
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    private val REQUEST_ENABLEBLUETOOTH: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        super.onResume()

        requestBluetoothFeature()

        var DB = DatabaseHelper(this)
        var db: SQLiteDatabase? = DB.writableDatabase

        if (db == null) {
            DB.onCreate(db)
        }

        if (DB.findUser()) {
            setContentView(R.layout.activity_main2)
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
            var percent = 0

            SignUpbtn.setOnClickListener {
                val CurrentUser = UserModel(
                    EmailForm.text.toString(),
                    PasswordForm.text.toString(),
                    NameForm.text.toString(),
                    BLEAddress
                )

                val header: HashMap<String, String> =
                    hashMapOf("Content-Type" to "application/json")
                val body = Gson().toJson(CurrentUser)
                var flag = false

                // TODO: Coroutine勉強してリファクタリングする
                GlobalScope.launch(Dispatchers.Main) {
                    async(Dispatchers.Default) {
                        "http://202.13.162.197:3000/auth".httpPost().header(header).body(body)
                            .response()
                    }.await().let {
                        when (it.third) {
                            is Result.Success -> {
                                percent = 100
                                flag = true
                                DB.saveUser(CurrentUser)
                            }
                            is Result.Failure -> {
                                percent = 20
                                flag = false
                            }
                        }
                        ProgressChanged(PBar, percent)
                        if (flag) {
                            setContentView(R.layout.activity_main2)
                        }
                    }
                }
            }
        }
    }

    fun requestBluetoothFeature() {
        val manager: BluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter: BluetoothAdapter? = manager.adapter

        if(adapter == null){
            Toast.makeText(this, R.string.bluetooth_is_not_supported, Toast.LENGTH_LONG).show()
            finish()
        } else {
            if (adapter.isEnabled()) {
            } else {
                var enableBtIntent: Intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLEBLUETOOTH)
            }
        }
    }


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
            values.put(COLUMN_PASSWORD, user.password) //TODO: 暗号化
            values.put(COLUMN_BLE_ADDRESS, user.ble_address)
            db.insert(TABLE_NAME, null, values)
            db.close()
        }

        fun findUser(): Boolean {
            val db = this.writableDatabase
            val userSQL = "SELECT count(*) as cnt from $TABLE_NAME;"

            val cursor: Cursor = db.rawQuery(userSQL, null)
            cursor.moveToFirst()
            val count: Int = cursor.getInt(cursor.getColumnIndex("cnt"))
            val flg: Boolean = (count > 0)
            cursor.close()
            return flg
        }
    }
}

class UserModel(email: String, password: String, name: String, ble_address: String){
    val email = email
    val password = password
    val name = name
    val ble_address = ble_address
}

private fun ProgressChanged(PBar: ProgressBar, percentage: Int) {
    val animation = ObjectAnimator.ofInt(PBar, "progress", percentage)
    animation.duration = 1000
    animation.interpolator = DecelerateInterpolator()
    animation.start()
}

