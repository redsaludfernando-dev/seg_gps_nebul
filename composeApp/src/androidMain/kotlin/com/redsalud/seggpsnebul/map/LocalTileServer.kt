package com.redsalud.seggpsnebul.map

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import java.io.RandomAccessFile

class LocalTileServer(private val pmtilesPath: String) {
    private var server: EmbeddedServer<*, *>? = null
    var port: Int = 0
        private set

    fun start() {
        val freePort = java.net.ServerSocket(0).use { it.localPort }
        val engine = embeddedServer(CIO, port = freePort) {
            install(io.ktor.server.plugins.cors.routing.CORS) {
                anyHost()
            }
            routing { setupRoutes(pmtilesPath) }
        }
        engine.start(wait = false)
        port = freePort
        server = engine
    }

    fun stop() {
        server?.stop(0, 0)
        server = null
    }
}

private fun Routing.setupRoutes(pmtilesPath: String) {
    get("/tiles.pmtiles") {
        val file = File(pmtilesPath)
        if (!file.exists()) { call.respond(HttpStatusCode.NotFound); return@get }

        val rangeHeader = call.request.headers[HttpHeaders.Range]
        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
        call.response.header(HttpHeaders.AcceptRanges, "bytes")

        if (rangeHeader != null) {
            val fileLen = file.length()
            val range = parseByteRange(rangeHeader, fileLen)
            val len = (range.last - range.first + 1).toInt()
            val bytes = ByteArray(len)
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(range.first)
                raf.readFully(bytes)
            }
            call.response.header(HttpHeaders.ContentRange, "bytes ${range.first}-${range.last}/$fileLen")
            call.respondBytes(bytes, ContentType.Application.OctetStream, HttpStatusCode.PartialContent)
        } else {
            call.respondFile(file)
        }
    }
}

private fun parseByteRange(header: String, fileLen: Long): LongRange {
    val spec = header.removePrefix("bytes=")
    val parts = spec.split("-")
    val start = parts[0].toLongOrNull() ?: 0L
    val end = parts.getOrNull(1)?.toLongOrNull() ?: (fileLen - 1)
    return start..minOf(end, fileLen - 1)
}
