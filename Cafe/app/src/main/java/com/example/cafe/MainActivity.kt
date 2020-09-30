package com.example.cafe

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result
import com.google.gson.Gson

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val EmailForm   : EditText = findViewById<EditText>(R.id.EmailAddress)
        val PasswordForm: EditText = findViewById<EditText>(R.id.Password)
        val NameForm    : EditText = findViewById<EditText>(R.id.Personname)
        val SignUpbtn   : Button   = findViewById<Button>(R.id.SignUpBtn)

        SignUpbtn.setOnClickListener{
            val CurrentUser = UserModel(EmailForm.text.toString(), PasswordForm.text.toString(), NameForm.text.toString())

            val header: HashMap<String, String> = hashMapOf("Content-Type" to "application/json")
            val body = Gson().toJson(CurrentUser)

            val URL = "http://202.13.162.197:3000/auth"
                .httpPost()
                .header(header)
                .body(body)
                .response {request, response, result ->
                    Log.d("signup", "data="+response)
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
