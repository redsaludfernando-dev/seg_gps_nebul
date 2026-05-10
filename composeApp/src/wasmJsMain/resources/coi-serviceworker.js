/* coi-serviceworker — basado en https://github.com/gzuidhof/coi-serviceworker
 * Agrega headers Cross-Origin-Opener-Policy y Cross-Origin-Embedder-Policy
 * para habilitar crossOriginIsolated en GitHub Pages (no soporta headers HTTP custom).
 *
 * Bugfix local: la version 0.1.7 upstream tenia un `.catch(() => fetch(event.request))`
 * que reintentaba la peticion original; pero si la primera llamada consumio el body
 * (PATCH/POST con JSON), el reintento fallaba con "Cannot construct a Request with a
 * Request object that has already been used", y la promesa se quedaba rejected — el
 * navegador entonces marcaba la peticion como ERR_FAILED. Eso rompia los UPDATE de
 * `zonas` desde el panel admin (PATCH /rest/v1/zonas?id=eq.X).
 *
 * Fix: si la primera fetch falla, propagar el error tal cual (igual que sin SW).
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
        fetch(event.request).then(function(response) {
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
    );
});
