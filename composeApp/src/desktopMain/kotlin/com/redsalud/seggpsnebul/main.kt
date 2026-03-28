package com.redsalud.seggpsnebul

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "GPS Nebulización — Panel Admin"
    ) {
        App()
    }
}
