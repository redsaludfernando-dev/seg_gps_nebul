package com.redsalud.seggpsnebul.screens.admin

actual suspend fun saveCsvToPlatform(sessionId: String, csv: String, filename: String): String {
    triggerBrowserDownload(csv, filename)
    return "Descarga iniciada en navegador"
}

private fun triggerBrowserDownload(csv: String, filename: String) {
    js("""
        (function(csvData, fname) {
            var blob = new Blob([csvData], {type: 'text/csv;charset=utf-8;'});
            var url = URL.createObjectURL(blob);
            var link = document.createElement('a');
            link.setAttribute('href', url);
            link.setAttribute('download', fname);
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            URL.revokeObjectURL(url);
        })(csv, filename)
    """)
}
