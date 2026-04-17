package com.redsalud.seggpsnebul

class WasmPlatform : Platform {
    override val name: String = "Web (Kotlin/Wasm)"
}

actual fun getPlatform(): Platform = WasmPlatform()
