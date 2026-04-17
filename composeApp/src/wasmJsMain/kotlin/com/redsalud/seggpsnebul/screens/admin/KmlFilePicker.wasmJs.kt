package com.redsalud.seggpsnebul.screens.admin

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
actual fun triggerKmlFilePicker() {
    js("""
        (function() {
            window._pendingKml = null;
            var input = document.createElement('input');
            input.type = 'file';
            input.accept = '.kml,.xml';
            input.style.display = 'none';
            input.onchange = function(e) {
                var file = e.target.files && e.target.files[0];
                if (!file) return;
                var reader = new FileReader();
                reader.onload = function(re) {
                    window._pendingKml = re.target.result;
                };
                reader.onerror = function() { window._pendingKml = '__ERROR__'; };
                reader.readAsText(file, 'UTF-8');
            };
            document.body.appendChild(input);
            input.click();
            setTimeout(function() { document.body.removeChild(input); }, 1000);
        })()
    """)
}

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
actual fun consumePendingKml(): String? = getAndClearPendingKml()
    ?.takeIf { it != "__ERROR__" }

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun getAndClearPendingKml(): String? = js("""
    (function() {
        var v = window._pendingKml;
        if (v !== null && v !== undefined) { window._pendingKml = null; return String(v); }
        return null;
    })()
""")
