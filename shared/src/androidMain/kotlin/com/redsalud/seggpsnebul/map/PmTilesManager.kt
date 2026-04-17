package com.redsalud.seggpsnebul.map

import com.redsalud.seggpsnebul.data.remote.supabaseClient
import io.github.jan.supabase.storage.storage
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val BUCKET      = "map-tiles"
private const val REMOTE_PATH = "rioja.pmtiles"

object PmTilesManager {

    fun localPath(): String = "${appFilesDir()}/maps/rioja.pmtiles"

    fun isDownloaded(): Boolean = File(localPath()).exists()

    /**
     * Downloads the Rioja PMTiles file from Supabase Storage, streaming directly
     * to disk to avoid loading ~50–150 MB into memory.
     *
     * [onProgress] receives values in [0f, 1f]; called only when the server
     * provides a Content-Length header.  Otherwise the UI shows a spinner.
     */
    suspend fun download(onProgress: (Float) -> Unit = {}): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File("${appFilesDir()}/maps")
                if (!dir.exists()) dir.mkdirs()

                val url = supabaseClient.storage[BUCKET].publicUrl(REMOTE_PATH)
                val tmp = File("${localPath()}.tmp")

                HttpClient().use { http ->
                    http.prepareGet(url).execute { response ->
                        val contentLength = response.contentLength() ?: 0L
                        val channel: ByteReadChannel = response.bodyAsChannel()
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var received = 0L

                        tmp.outputStream().use { output ->
                            while (!channel.isClosedForRead) {
                                val n = channel.readAvailable(buffer)
                                if (n < 0) break
                                output.write(buffer, 0, n)
                                received += n
                                if (contentLength > 0L) {
                                    onProgress(received.toFloat() / contentLength.toFloat())
                                }
                            }
                        }
                    }
                }

                // Atomic replace: only swap if download completed without exception
                if (!tmp.renameTo(File(localPath()))) {
                    throw Exception("No se pudo mover el archivo descargado a su destino final")
                }
            }.also { result ->
                // Clean up partial file on failure
                if (result.isFailure) File("${localPath()}.tmp").delete()
            }
        }
}
