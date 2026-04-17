/* coi-serviceworker v0.1.7 — https://github.com/gzuidhof/coi-serviceworker
 * Agrega headers Cross-Origin-Opener-Policy y Cross-Origin-Embedder-Policy
 * para habilitar crossOriginIsolated en GitHub Pages (no soporta headers HTTP custom).
 */

self.addEventListener("install", function() { self.skipWaiting(); });

self.addEventListener("activate", function(event) {
    event.waitUntil(self.clients.claim());
});

self.addEventListener("fetch", function(event) {
    // No interceptar peticiones "only-if-cached" de cross-origin
    if (event.request.cache === "only-if-cached" &&
        event.request.mode !== "same-origin") return;

    event.respondWith(
        fetch(event.request)
            .then(function(response) {
                // No modificar respuestas opacas o de error de red
                if (response.status === 0) return response;

                var headers = new Headers(response.headers);
                headers.set("Cross-Origin-Opener-Policy",   "same-origin");
                headers.set("Cross-Origin-Embedder-Policy", "require-corp");

                return new Response(response.body, {
                    status:     response.status,
                    statusText: response.statusText,
                    headers:    headers
                });
            })
            .catch(function() { return fetch(event.request); })
    );
});
