package com.redsalud.seggpsnebul.map

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

class LocalTileServer(private val pmtilesPath: String) {
    private var server: EmbeddedServer<*, *>? = null
    private var reader: PmTilesReader? = null

    var port: Int = 0
        private set
    var minZoom: Int = 0
        private set
    var maxZoom: Int = 14
        private set

    fun start() {
        val r = PmTilesReader(File(pmtilesPath))
        r.open()
        reader = r
        minZoom = r.minZoom
        maxZoom = r.maxZoom

        val freePort = java.net.ServerSocket(0).use { it.localPort }
        val engine = embeddedServer(CIO, port = freePort) {
            install(io.ktor.server.plugins.cors.routing.CORS) { anyHost() }
            routing { setupRoutes(r) }
        }
        engine.start(wait = false)
        port = freePort
        server = engine
    }

    fun stop() {
        server?.stop(0, 0)
        server = null
        reader?.close()
        reader = null
    }
}

private fun Routing.setupRoutes(reader: PmTilesReader) {
    get("/tiles/{z}/{x}/{y}.pbf") {
        val z = call.parameters["z"]?.toIntOrNull()
        val x = call.parameters["x"]?.toIntOrNull()
        val y = call.parameters["y"]?.toIntOrNull()
        if (z == null || x == null || y == null) {
            call.respond(HttpStatusCode.BadRequest); return@get
        }
        val tile = reader.getTile(z, x, y)
        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
        if (tile == null) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.respondBytes(tile, ContentType("application", "x-protobuf"))
        }
    }
}
