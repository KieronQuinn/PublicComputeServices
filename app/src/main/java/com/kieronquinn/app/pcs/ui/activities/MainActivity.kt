package com.kieronquinn.app.pcs.ui.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.kieronquinn.app.pcs.ui.screens.container.ContainerScreen

class MainActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ContainerScreen()
        }
    }

}