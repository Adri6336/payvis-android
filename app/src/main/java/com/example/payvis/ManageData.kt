package com.example.payvis

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

class ManageData: AppCompatActivity() {

    val vibrator = PhoneVibrator(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.manage_data)

        // Connect elements ============
        val backButton = findViewById<Button>(R.id.back_button)
        val exportDataButton = findViewById<Button>(R.id.export_data_button)
        val loadDataButton = findViewById<Button>(R.id.load_data_button)

        val notifyView = findViewById<TextView>(R.id.notify_view2)

        // Create listeners ============
        // Back
        backButton.setOnClickListener {
            vibrator.vibrate()
            onBackPressed()
        }


        // Export Data
        exportDataButton.setOnClickListener {
            vibrator.vibrate()
            var uri: Uri
            val intent = Intent(Intent.ACTION_SEND)

            try{
                // 1. Get uri
                if (File(applicationContext.filesDir, "workDB.json").exists()){
                    uri = FileProvider.getUriForFile(
                        this@ManageData,
                        "com.example.payvis.MainActivity",
                        File(applicationContext.filesDir, "workDB.json")
                    )

                    // 2. Share
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    intent.setType("*/*")
                    intent.putExtra(Intent.EXTRA_STREAM, uri)
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                }
            } catch (t: Throwable){
                notifyView.text = "Unable to export file: $t"
                println("[X] Failed to export file: $t")
            }

        }


        // Load Data
        loadDataButton.setOnClickListener {
            vibrator.vibrate()
        }
    }
}