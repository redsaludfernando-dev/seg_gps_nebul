package com.redsalud.seggpsnebul

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    AppContainer.init()
    ComposeViewport(document.body!!) {
        App()
    }
}
