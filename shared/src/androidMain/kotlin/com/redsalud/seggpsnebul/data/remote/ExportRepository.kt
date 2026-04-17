package com.redsalud.seggpsnebul.data.remote

import com.redsalud.seggpsnebul.data.local.LocalDataSource
import com.redsalud.seggpsnebul.map.appFilesDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android-specific export: builds CSV via CsvExporter, writes to disk,
 * and marks the session as exported in the local DB.
 */
class ExportRepository(private val localDataSource: LocalDataSource) {

    suspend fun exportSession(sessionId: String): Result<String> =
        withContext(Dispatchers.IO) {
            CsvExporter().buildCsv(sessionId).mapCatching { (csv, filename) ->
                val dir = File("${appFilesDir()}/exports").apply { mkdirs() }
                val path = "$dir/$filename"
                File(path).writeText(csv, Charsets.UTF_8)
                localDataSource.markSessionExported(sessionId)
                path
            }
        }
}
