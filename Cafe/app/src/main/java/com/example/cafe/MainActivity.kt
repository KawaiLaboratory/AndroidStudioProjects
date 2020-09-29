package com.example.cafe

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val EmailForm    = findViewById<EditText>(R.id.EmailAddress) as EditText
        val PasswordForm = findViewById<EditText>(R.id.Password)     as EditText
        val NameForm     = findViewById<EditText>(R.id.Personname)   as EditText
        val SignUpbtn    = findViewById<Button>(R.id.SignUpBtn)      as Button

        SignUpbtn.setOnClickListener{
            Toast.makeText(this, EmailForm.text,  Toast.LENGTH_SHORT).show()
        }
    }
}
