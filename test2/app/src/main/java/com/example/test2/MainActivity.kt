package com.example.test2

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.widget.Toast
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

/* 参考資料
https://developer.android.com/guide/topics/connectivity/bluetooth?hl=ja
https://www.hiramine.com/programming/bluetoothcommunicator/01_is_bluetooth_enabled.html
http://harumi.sakura.ne.jp/wordpress/2019/09/03/android%E3%81%AEbluetooth%E3%81%A7%E3%82%B9%E3%82%AD%E3%83%A3%E3%83%B3%E3%81%97%E3%81%A6%E3%81%BF%E3%82%8B/
*/

class MainActivity : AppCompatActivity() {
    val REQUEST_ENABLEBLUETOOTH: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        super.onResume()

        requestBluetoothFeature()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when(requestCode){
            REQUEST_ENABLEBLUETOOTH -> {
                if(Activity.RESULT_CANCELED == resultCode){
                    Toast.makeText(this, R.string.bluetooth_is_not_working, Toast.LENGTH_LONG).show()
                    finish()
                    return
                }
            }
        }
        super.onActivityResult( requestCode, resultCode, data )
    }

    fun requestBluetoothFeature() {
        val manager: BluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter: BluetoothAdapter? = manager.getAdapter()

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
}