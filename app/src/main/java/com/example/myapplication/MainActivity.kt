package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val openStepCounterButton = findViewById<Button>(R.id.openStepCounterButton)
        openStepCounterButton.setOnClickListener { v: View? ->
            // Открытие Activity для шагомера
            val intent = Intent(
                this@MainActivity,
                StepCounterActivity::class.java
            )
            startActivity(intent)
        }

        // Кнопка для открытия игры
        val openGameButton = findViewById<Button>(R.id.openGameButton)
        openGameButton.setOnClickListener { v: View? ->
            // Открытие Activity для игры
            val intent = Intent(
                this@MainActivity,
                GameActivity::class.java
            )
            startActivity(intent)
        }
    }
}
