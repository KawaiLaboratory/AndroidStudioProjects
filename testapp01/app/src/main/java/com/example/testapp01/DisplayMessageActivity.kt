package com.example.testapp01

import android.os.Bundle
import android.support.wearable.activity.WearableActivity

class DisplayMessageActivity : WearableActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_display_message)

        // Enables Always-on
        val intent: Intent = getIntent()
        val message: String = intent.getStringExtra(MainActivity().EXTRA_MESSAGE)
        val textView: TextView = findViewById(R.id.textView)
        textView.setText(message)
    }
}