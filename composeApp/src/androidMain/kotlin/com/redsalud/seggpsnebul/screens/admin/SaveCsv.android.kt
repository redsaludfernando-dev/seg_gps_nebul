package com.redsalud.seggpsnebul.screens.admin

import com.redsalud.seggpsnebul.AppContainer
import com.redsalud.seggpsnebul.map.appFilesDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual suspend fun saveCsvToPlatform(sessionId: String, csv: String, filename: String): String =
    withContext(Dispatchers.IO) {
        val dir = File("${appFilesDir()}/exports").apply { mkdirs() }
        val path = "$dir/$filename"
        File(path).writeText(csv, Charsets.UTF_8)
        AppContainer.localDataSource.markSessionExported(sessionId)
        path
    }
