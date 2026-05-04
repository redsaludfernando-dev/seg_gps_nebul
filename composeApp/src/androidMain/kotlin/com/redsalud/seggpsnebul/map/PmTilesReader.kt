package com.redsalud.seggpsnebul.map

import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

class PmTilesReader(private val file: File) : AutoCloseable {

    companion object {
        const val COMPRESSION_NONE = 1
        const val COMPRESSION_GZIP = 2
    }

    private data class Entry(
        val tileId: Long,
        val offset: Long,
        val length: Long,
        val runLength: Long
    )

    private lateinit var raf: RandomAccessFile
    private val rafLock = Any()
    private val leafCache = ConcurrentHashMap<Long, List<Entry>>()
    private var rootEntries: List<Entry> = emptyList()

    private var leafOffset: Long = 0
    private var tileDataOffset: Long = 0
    private var internalCompression: Int = COMPRESSION_GZIP
    var tileCompression: Int = COMPRESSION_GZIP
        private set
    var minZoom: Int = 0
        private set
    var maxZoom: Int = 14
        private set
    var minLonE7: Int = -1800000000
        private set
    var minLatE7: Int = -850000000
        private set
    var maxLonE7: Int = 1800000000
        private set
    var maxLatE7: Int = 850000000
        private set

    fun open() {
        raf = RandomAccessFile(file, "r")
        val header = ByteArray(127)
        raf.seek(0)
        raf.readFully(header)
        require(String(header, 0, 7, Charsets.US_ASCII) == "PMTiles") { "No es un archivo PMTiles" }
        require(header[7].toInt() == 3) { "Sólo se soporta PMTiles v3" }

        val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val rootOffset = bb.getLong(8)
        val rootLength = bb.getLong(16)
        leafOffset = bb.getLong(40)
        tileDataOffset = bb.getLong(56)
        internalCompression = header[97].toInt() and 0xFF
        tileCompression    = header[98].toInt() and 0xFF
        minZoom            = header[100].toInt() and 0xFF
        maxZoom            = header[101].toInt() and 0xFF
        minLonE7           = bb.getInt(102)
        minLatE7           = bb.getInt(106)
        maxLonE7           = bb.getInt(110)
        maxLatE7           = bb.getInt(114)

        rootEntries = readDirectory(rootOffset, rootLength)
    }

    override fun close() {
        leafCache.clear()
        if (::raf.isInitialized) raf.close()
    }

    fun getTile(z: Int, x: Int, y: Int): ByteArray? {
        if (z < minZoom || z > maxZoom) return null
        val tileId = zxyToTileId(z, x, y) ?: return null

        var entries = rootEntries
        repeat(4) {
            val entry = findEntry(entries, tileId) ?: return null
            if (entry.runLength > 0L) {
                return if (tileId < entry.tileId + entry.runLength) {
                    val raw = readBytes(tileDataOffset + entry.offset, entry.length.toInt())
                    if (tileCompression == COMPRESSION_GZIP) gunzip(raw) else raw
                } else null
            }
            entries = leafCache.getOrPut(entry.offset) {
                readDirectory(leafOffset + entry.offset, entry.length)
            }
        }
        return null
    }

    private fun findEntry(entries: List<Entry>, tileId: Long): Entry? {
        var lo = 0
        var hi = entries.size - 1
        var hit = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (entries[mid].tileId <= tileId) { hit = mid; lo = mid + 1 } else hi = mid - 1
        }
        return if (hit >= 0) entries[hit] else null
    }

    private fun readDirectory(offset: Long, length: Long): List<Entry> {
        val raw = readBytes(offset, length.toInt())
        val bytes = if (internalCompression == COMPRESSION_GZIP) gunzip(raw) else raw
        return parseDirectory(bytes)
    }

    private fun readBytes(offset: Long, length: Int): ByteArray {
        val buf = ByteArray(length)
        synchronized(rafLock) {
            raf.seek(offset)
            raf.readFully(buf)
        }
        return buf
    }

    private fun gunzip(bytes: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }

    private fun parseDirectory(bytes: ByteArray): List<Entry> {
        val r = VarintReader(bytes)
        val n = r.readVarint().toInt()
        if (n == 0) return emptyList()
        val ids = LongArray(n)
        val runs = LongArray(n)
        val lens = LongArray(n)
        val offs = LongArray(n)
        var lastId = 0L
        for (i in 0 until n) { lastId += r.readVarint(); ids[i] = lastId }
        for (i in 0 until n) runs[i] = r.readVarint()
        for (i in 0 until n) lens[i] = r.readVarint()
        for (i in 0 until n) {
            val v = r.readVarint()
            offs[i] = if (v == 0L && i > 0) offs[i - 1] + lens[i - 1] else v - 1
        }
        return List(n) { Entry(ids[it], offs[it], lens[it], runs[it]) }
    }

    private fun zxyToTileId(z: Int, x: Int, y: Int): Long? {
        if (z < 0 || z > 30) return null
        val n = 1 shl z
        if (x < 0 || y < 0 || x >= n || y >= n) return null

        var acc = 0L
        for (i in 0 until z) acc += (1L shl (i * 2))

        var xx = x; var yy = y
        var d = 0L
        var s = n / 2
        while (s > 0) {
            val rx = if ((xx and s) > 0) 1 else 0
            val ry = if ((yy and s) > 0) 1 else 0
            d += (s.toLong() * s.toLong()) * ((3L * rx) xor ry.toLong())
            if (ry == 0) {
                if (rx == 1) { xx = s - 1 - xx; yy = s - 1 - yy }
                val tmp = xx; xx = yy; yy = tmp
            }
            s = s shr 1
        }
        return acc + d
    }
}

private class VarintReader(private val bytes: ByteArray) {
    private var pos = 0
    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val b = bytes[pos++].toInt() and 0xFF
            result = result or ((b.toLong() and 0x7F) shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
    }
}
