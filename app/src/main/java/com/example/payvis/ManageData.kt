package com.example.payvis

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class ManageData: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.manage_data)

        // Connect elements ============
        val backButton = findViewById<Button>(R.id.back_button)
        val exportDataButton = findViewById<Button>(R.id.export_data_button)
        val loadDataButton = findViewById<Button>(R.id.load_data_button)

        // Create listeners ============
        // Back
        backButton.setOnClickListener {
            onBackPressed()
        }


        // Export Data
        exportDataButton.setOnClickListener {

        }


        // Load Data
        loadDataButton.setOnClickListener {

        }





    }

}