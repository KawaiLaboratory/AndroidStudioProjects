package com.example.cafe

import android.animation.ObjectAnimator
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result
import com.google.gson.Gson
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val EmailForm   : EditText    = findViewById<EditText>(R.id.EmailAddress)
        val PasswordForm: EditText    = findViewById<EditText>(R.id.Password)
        val NameForm    : EditText    = findViewById<EditText>(R.id.Personname)
        val SignUpbtn   : Button      = findViewById<Button>(R.id.SignUpBtn)
        val PBar        : ProgressBar = findViewById<ProgressBar>(R.id.progressBar)
        var percent = 0

        SignUpbtn.setOnClickListener{
            val CurrentUser = UserModel(EmailForm.text.toString(), PasswordForm.text.toString(), NameForm.text.toString())
            val header: HashMap<String, String> = hashMapOf("Content-Type" to "application/json")
            val body = Gson().toJson(CurrentUser)

            // TODO: Coroutine勉強してリファクタリングする
            GlobalScope.launch(Dispatchers.Main) {
                async(Dispatchers.Default) {
                    "http://202.13.162.197:3000/auth".httpPost().header(header).body(body).response()
                }.await().let {
                    when (it.third) {
                        is Result.Success -> {
                            percent = 100
                            Toast.makeText(this@MainActivity, "success", Toast.LENGTH_LONG).show()
                        }
                        is Result.Failure -> {
                            percent = 20
                            Toast.makeText(this@MainActivity, "failed", Toast.LENGTH_LONG).show()
                        }
                    }
                    ProgressChanged(PBar, percent)
                }
            }
        }
    }
}

class UserModel(email: String, password: String, name: String){
    val email = email
    val password = password
    val name = name
    val ble_address = "AA-AA-AA-AA-AA-AA"
}

private fun ProgressChanged(PBar: ProgressBar, percentage: Int) {
    val animation = ObjectAnimator.ofInt(PBar, "progress", percentage)
    animation.duration = 1000
    animation.interpolator = DecelerateInterpolator()
    animation.start()
}
