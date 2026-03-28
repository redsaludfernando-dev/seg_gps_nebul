package com.redsalud.seggpsnebul

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
