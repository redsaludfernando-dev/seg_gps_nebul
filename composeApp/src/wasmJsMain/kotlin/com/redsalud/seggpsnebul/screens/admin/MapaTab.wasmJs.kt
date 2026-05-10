@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.redsalud.seggpsnebul.screens.admin

import androidx.compose.runtime.*
import com.redsalud.seggpsnebul.data.remote.*
import kotlinx.coroutines.delay
import kotlin.time.Clock

actual @Composable fun MapaTab(vm: AdminViewModel, onBack: () -> Unit) {
    val zonas        by vm.zonas.collectAsState()
    val sessions     by vm.sessions.collectAsState()
    val selectedId   by vm.selectedSessionId.collectAsState()
    val positions    by vm.livePositions.collectAsState()
    val tracks       by vm.trackSegments.collectAsState()
    val sessionStats by vm.sessionStats.collectAsState()
    val activeAlerts by vm.activeAlerts.collectAsState()
    val users        by vm.users.collectAsState()

    // Crear geovisor HTML al entrar; destruirlo y mostrar Compose al salir
    DisposableEffect(Unit) {
        createGeovisor()
        onDispose { destroyGeovisor() }
    }

    // Selección automática de sesión activa
    LaunchedEffect(sessions) {
        if (selectedId == null && sessions.isNotEmpty()) {
            val active = sessions.firstOrNull { it.is_active } ?: sessions.first()
            vm.selectSession(active.id)
        }
        updateSidebarSessions(sessionsJson(sessions), selectedId)
    }

    LaunchedEffect(selectedId) { updateSidebarSessions(sessionsJson(sessions), selectedId) }

    LaunchedEffect(positions, sessionStats, selectedId) {
        val stats = sessionStats[selectedId]
        updateSidebarStats(statsJson(stats, positions.size))
        updateSidebarWorkers(workersJson(positions))
        updateMapPositions(positionsGeoJson(positions))
    }

    LaunchedEffect(tracks) { updateMapTracks(tracksGeoJson(tracks)) }

    LaunchedEffect(zonas) { delay(400); updateMapZonas(zonasGeoJson(zonas)) }

    LaunchedEffect(activeAlerts, users) { updateMapAlerts(alertsGeoJson(activeAlerts, users)) }

    LaunchedEffect(selectedId) {
        selectedId?.let { vm.loadSessionStats(it) }
    }

    // Refrescar alertas cada 8s
    LaunchedEffect(Unit) {
        while (true) {
            vm.loadActiveAlerts()
            delay(8_000)
        }
    }

    // Polling: detectar eventos HTML → Kotlin
    LaunchedEffect(Unit) {
        while (true) {
            // Botón "← Panel Admin" o tecla Esc
            if (checkGeoBack()) { clearGeoBack(); onBack(); return@LaunchedEffect }

            // Cambio de sesión en el dropdown
            val newSession = consumeSessionChange()
            if (newSession != null && newSession != selectedId) vm.selectSession(newSession)

            // Botón "Actualizar"
            if (checkGeoRefresh()) { clearGeoRefresh(); vm.selectSession(selectedId); vm.loadActiveAlerts() }

            // Toggles de capas
            val layers = consumeLayerChange()
            if (layers != null) {
                setMapLayerGroup("zonas-fill,zonas-line,zonas-label", layers.zonas)
                setMapLayerGroup("tracks-line", layers.tracks)
                setMapLayerGroup("pos-circle,pos-label", layers.positions)
            }

            // Acciones del popup de alerta
            val action = consumeAlertAction()
            if (action != null) {
                when (action.type) {
                    "on_way"   -> vm.adminAlertOnWay(action.alertId)
                    "attended" -> vm.adminAlertAttended(action.alertId)
                }
            }
            delay(120)
        }
    }
}

// ─── JSON helpers ─────────────────────────────────────────────────────────────

private data class LayerState(val zonas: Boolean, val positions: Boolean, val tracks: Boolean)

private fun sessionsJson(sessions: List<SessionAdminDto>): String =
    "[${sessions.joinToString(",") {
        val name = it.name.replace("\\","\\\\").replace("\"","\\\"")
        """{"id":"${it.id}","name":"$name","active":${it.is_active}}"""
    }}]"

private fun statsJson(stats: SessionStats?, workerCount: Int): String =
    """{"workers":$workerCount,"gps":${stats?.trackCount ?: 0},"alerts":${stats?.alertCount ?: 0},"blocks":${stats?.blockCount ?: 0},"loaded":${stats != null}}"""

private fun workersJson(positions: List<WorkerPositionDto>): String =
    "[${positions.joinToString(",") {
        val name = it.fullName.replace("\"","\\\"")
        val ago  = timeAgo(it.capturedAt)
        val role = roleLabel(it.role)
        """{"name":"$name","role":"$role","color":"${roleColor(it.role)}","ago":"$ago"}"""
    }}]"

private fun zonasGeoJson(zonas: List<ZonaDto>): String {
    val features = zonas.joinToString(",") { z ->
        val nombre = z.nombre.replace("\\","\\\\").replace("\"","\\\"")
        """{"type":"Feature","geometry":${z.geojson},"properties":{"nombre":"$nombre","color":"${z.color}"}}"""
    }
    return """{"type":"FeatureCollection","features":[$features]}"""
}

private fun positionsGeoJson(positions: List<WorkerPositionDto>): String {
    val features = positions.joinToString(",") { p ->
        val name     = p.fullName.replace("\"","\\\"")
        val initials = p.fullName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
        val ago      = timeAgo(p.capturedAt)
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[${p.longitude},${p.latitude}]},"properties":{"userId":"${p.userId}","fullName":"$name","role":"${roleLabel(p.role)}","color":"${roleColor(p.role)}","initials":"$initials","timeAgo":"$ago"}}"""
    }
    return """{"type":"FeatureCollection","features":[$features]}"""
}

private fun tracksGeoJson(tracks: List<TrackSegment>): String {
    val features = tracks.joinToString(",") { seg ->
        val coords = seg.points.joinToString(",") { "[${it.longitude},${it.latitude}]" }
        """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coords]},"properties":{"color":"${roleColor(seg.role)}"}}"""
    }
    return """{"type":"FeatureCollection","features":[$features]}"""
}

private fun alertsGeoJson(alerts: List<AlertAdminDto>, users: List<UserAdminDto>): String {
    val byId = users.associateBy { it.id }
    val features = alerts.mapNotNull { a ->
        val lat = a.latitude ?: return@mapNotNull null
        val lon = a.longitude ?: return@mapNotNull null
        val type   = a.alert_type
        val status = a.response_status ?: "pending"
        val color  = if (status == "on_way") "#f39c12" else "#e74c3c"
        val msg    = (a.message ?: "").replace("\\","\\\\").replace("\"","\\\"")
        val label  = alertTypeLabel(type)
        val sender = (byId[a.sender_id]?.full_name ?: "—").replace("\\","\\\\").replace("\"","\\\"")
        val responder = a.response_by?.let { (byId[it]?.full_name ?: "—").replace("\\","\\\\").replace("\"","\\\"") }
        val responderJson = responder?.let { "\"$it\"" } ?: "null"
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[$lon,$lat]},"properties":{"alertId":"${a.id}","status":"$status","color":"$color","label":"$label","message":"$msg","sender":"$sender","responder":$responderJson}}"""
    }.joinToString(",")
    return """{"type":"FeatureCollection","features":[$features]}"""
}

private fun alertTypeLabel(type: String) = when (type) {
    "agua"               -> "Agua mineral"
    "gasolina"           -> "Gasolina"
    "insumo_quimico"     -> "Insumo químico"
    "averia_maquina"     -> "Avería de máquina"
    "trabajo_finalizado" -> "Trabajo finalizado"
    "broadcast_text"     -> "Mensaje a brigada"
    else                 -> type
}

data class AlertAction(val alertId: String, val type: String)

private fun roleColor(role: String) = when (role) {
    "nebulizador"  -> "#27ae60"
    "jefe_brigada" -> "#2980b9"
    "anotador"     -> "#8e44ad"
    "chofer"       -> "#e67e22"
    else           -> "#e74c3c"
}

private fun roleLabel(role: String) = when (role) {
    "nebulizador"  -> "Nebulizador"
    "jefe_brigada" -> "Jefe de Brigada"
    "anotador"     -> "Anotador"
    "chofer"       -> "Chofer"
    else           -> role
}

private fun timeAgo(capturedAt: Long): String {
    val diff = Clock.System.now().toEpochMilliseconds() - capturedAt
    return when {
        diff < 60_000    -> "hace ${diff / 1_000}s"
        diff < 3_600_000 -> "hace ${diff / 60_000}min"
        else             -> "hace ${diff / 3_600_000}h"
    }
}

// ─── JS interop ───────────────────────────────────────────────────────────────

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun createGeovisor(): Unit = js("""
(function() {
  // Ocultar canvas de Compose
  var cv = document.getElementById('ComposeTarget');
  if (cv) cv.style.display = 'none';

  // Sidebar
  var sb = document.createElement('div');
  sb.id = 'geo-sb';
  sb.style.cssText = 'position:fixed;left:0;top:0;bottom:0;width:290px;background:#fff;z-index:10;overflow-y:auto;box-shadow:2px 0 12px rgba(0,0,0,.15);font-family:-apple-system,BlinkMacSystemFont,Segoe UI,sans-serif;font-size:13px;';
  sb.innerHTML = `
    <div style="padding:14px 16px 10px;border-bottom:1px solid #eee;display:flex;align-items:center;gap:10px">
      <button id="geo-back" style="background:none;border:1px solid #ddd;border-radius:6px;padding:5px 10px;cursor:pointer;font-size:12px;color:#555">&#8592; Panel Admin</button>
      <div>
        <div style="font-weight:700;font-size:15px;color:#1a1a2e">Geovisor</div>
        <div style="font-size:11px;color:#888">Brigadas GPS &middot; Rioja</div>
      </div>
    </div>

    <div style="padding:12px 16px;border-bottom:1px solid #eee">
      <div style="font-size:10px;font-weight:700;color:#999;letter-spacing:.6px;margin-bottom:6px">JORNADA</div>
      <select id="geo-session" style="width:100%;padding:7px 10px;border:1px solid #ddd;border-radius:6px;font-size:12px;background:#fafafa;cursor:pointer">
        <option value="">Cargando...</option>
      </select>
    </div>

    <div id="geo-stats" style="padding:12px 16px;border-bottom:1px solid #eee;background:#f8faff">
      <div style="font-size:10px;font-weight:700;color:#999;letter-spacing:.6px;margin-bottom:8px">ESTADÍSTICAS</div>
      <div style="color:#888;font-size:12px">Selecciona una jornada</div>
    </div>

    <div id="geo-workers" style="padding:12px 16px;border-bottom:1px solid #eee;min-height:40px">
      <div style="font-size:10px;font-weight:700;color:#999;letter-spacing:.6px;margin-bottom:8px">TRABAJADORES EN CAMPO</div>
      <div style="color:#888;font-size:12px">Sin datos</div>
    </div>

    <div style="padding:12px 16px;border-bottom:1px solid #eee">
      <div style="font-size:10px;font-weight:700;color:#999;letter-spacing:.6px;margin-bottom:8px">CAPAS</div>
      <label style="display:flex;align-items:center;gap:8px;margin-bottom:6px;cursor:pointer">
        <input type="checkbox" id="geo-l-zonas" checked style="width:15px;height:15px;accent-color:#e74c3c">
        <span>Manzanas nebulizar</span>
      </label>
      <label style="display:flex;align-items:center;gap:8px;margin-bottom:6px;cursor:pointer">
        <input type="checkbox" id="geo-l-pos" checked style="width:15px;height:15px;accent-color:#3498db">
        <span>Posiciones actuales</span>
      </label>
      <label style="display:flex;align-items:center;gap:8px;cursor:pointer">
        <input type="checkbox" id="geo-l-tracks" checked style="width:15px;height:15px;accent-color:#9b59b6">
        <span>Rutas históricas</span>
      </label>
    </div>

    <div style="padding:12px 16px">
      <button id="geo-refresh" style="width:100%;padding:8px;background:#6750a4;color:#fff;border:none;border-radius:8px;cursor:pointer;font-size:13px;font-weight:500">
        &#8635; Actualizar datos
      </button>
    </div>
  `;
  document.body.appendChild(sb);

  // Div del mapa (a la derecha del sidebar)
  var md = document.createElement('div');
  md.id = 'geo-map';
  md.style.cssText = 'position:fixed;left:290px;top:0;right:0;bottom:0;z-index:5;';
  document.body.appendChild(md);

  // Inicializar MapLibre
  var map = new maplibregl.Map({
    container: 'geo-map',
    style: {
      version: 8,
      glyphs:  'https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf',
      sources: { osm: { type:'raster', tiles:['https://tile.openstreetmap.org/{z}/{x}/{y}.png'], tileSize:256, attribution:'&copy; OpenStreetMap' }},
      layers:  [{ id:'osm', type:'raster', source:'osm' }]
    },
    center: [-77.160, -6.058], zoom: 14
  });

  window._gMap = map;
  window._gReady = false;
  window._gBack = false;
  window._gRefresh = false;
  window._gSessionChange = null;
  window._gLayerChange = null;

  map.addControl(new maplibregl.NavigationControl(), 'bottom-right');
  map.addControl(new maplibregl.ScaleControl({ unit:'metric' }), 'bottom-right');

  map.on('load', function() {
    window._gReady = true;

    map.addSource('z-src', { type:'geojson', data:{ type:'FeatureCollection', features:[] }});
    map.addSource('t-src', { type:'geojson', data:{ type:'FeatureCollection', features:[] }});
    map.addSource('p-src', { type:'geojson', data:{ type:'FeatureCollection', features:[] }});
    map.addSource('a-src', { type:'geojson', data:{ type:'FeatureCollection', features:[] }});

    map.addLayer({ id:'zonas-fill',  type:'fill',   source:'z-src', paint:{ 'fill-color':['get','color'], 'fill-opacity':0.18 }});
    map.addLayer({ id:'zonas-line',  type:'line',   source:'z-src', paint:{ 'line-color':['get','color'], 'line-width':2.2 }});
    map.addLayer({ id:'zonas-label', type:'symbol', source:'z-src',
      layout:{ 'text-field':['get','nombre'], 'text-size':11, 'text-anchor':'center', 'text-font':['Open Sans Regular','Arial Unicode MS Regular'] },
      paint:{ 'text-color':'#222', 'text-halo-color':'#fff', 'text-halo-width':1.5 }});

    map.addLayer({ id:'tracks-line', type:'line', source:'t-src',
      paint:{ 'line-color':['get','color'], 'line-width':2, 'line-opacity':0.7, 'line-dasharray':[2,1] }});

    map.addLayer({ id:'pos-circle', type:'circle', source:'p-src',
      paint:{ 'circle-radius':10, 'circle-color':['get','color'], 'circle-stroke-color':'#fff', 'circle-stroke-width':2.5 }});
    map.addLayer({ id:'pos-label', type:'symbol', source:'p-src',
      layout:{ 'text-field':['get','initials'], 'text-size':9, 'text-anchor':'center', 'text-font':['Open Sans Semibold','Noto Sans Regular'] },
      paint:{ 'text-color':'#fff' }});

    // Alertas activas
    map.addLayer({ id:'alerts-circle', type:'circle', source:'a-src',
      paint:{ 'circle-radius':14, 'circle-color':['get','color'], 'circle-stroke-color':'#fff', 'circle-stroke-width':3, 'circle-opacity':0.92 }});
    map.addLayer({ id:'alerts-label', type:'symbol', source:'a-src',
      layout:{ 'text-field':'⚠', 'text-size':14, 'text-anchor':'center', 'text-allow-overlap':true },
      paint:{ 'text-color':'#fff' }});
    map.addLayer({ id:'alerts-name-label', type:'symbol', source:'a-src',
      layout:{
        'text-field':['get','sender'],
        'text-size':11,
        'text-anchor':'top',
        'text-offset':[0, 1.2],
        'text-allow-overlap':true,
        'text-font':['Open Sans Semibold','Noto Sans Regular']
      },
      paint:{ 'text-color':'#1a1a1a', 'text-halo-color':'#fff', 'text-halo-width':1.6 }});

    map.on('click','alerts-circle', function(e) {
      var p = e.features[0].properties;
      var aid = p.alertId;
      var html = '<div style="font-family:sans-serif;padding:4px 6px;min-width:200px">' +
        '<div style="font-weight:700;color:#c0392b;margin-bottom:4px">⚠ ' + p.label + '</div>' +
        '<div style="font-size:12px;margin-bottom:4px"><b>De:</b> ' + p.sender + '</div>' +
        (p.message ? '<div style="font-size:12px;margin-bottom:6px;color:#444">' + p.message + '</div>' : '') +
        (p.status === 'on_way' ? '<div style="font-size:11px;color:#e67e22;margin-bottom:6px">🟠 En camino: ' + (p.responder || '—') + '</div>' : '') +
        '<div style="display:flex;gap:6px;margin-top:6px">' +
        (p.status !== 'on_way' ? '<button data-act="on_way" data-id="'+aid+'" style="flex:1;padding:6px 8px;border:1px solid #ddd;background:#fff;border-radius:6px;cursor:pointer;font-size:11px">Ya voy</button>' : '') +
        '<button data-act="attended" data-id="'+aid+'" style="flex:1;padding:6px 8px;background:#27ae60;color:#fff;border:none;border-radius:6px;cursor:pointer;font-size:11px">Atendida</button>' +
        '</div></div>';
      var popup = new maplibregl.Popup({ offset:14 }).setLngLat(e.lngLat).setHTML(html).addTo(map);
      // Bind buttons
      setTimeout(function() {
        var pe = popup.getElement();
        if (!pe) return;
        pe.querySelectorAll('button[data-act]').forEach(function(btn) {
          btn.onclick = function() {
            window._gAlertAction = { type: btn.getAttribute('data-act'), alertId: btn.getAttribute('data-id') };
            popup.remove();
          };
        });
      }, 0);
    });
    map.on('mouseenter','alerts-circle', function() { map.getCanvas().style.cursor='pointer'; });
    map.on('mouseleave','alerts-circle', function() { map.getCanvas().style.cursor=''; });

    map.on('click','pos-circle', function(e) {
      var p = e.features[0].properties;
      new maplibregl.Popup({ offset:14 })
        .setLngLat(e.lngLat)
        .setHTML('<div style="font-family:sans-serif;padding:2px 4px"><b>'+p.fullName+'</b><br><span style="color:#555;font-size:12px">'+p.role+'</span><br><span style="color:#888;font-size:11px">'+p.timeAgo+'</span></div>')
        .addTo(map);
    });
    map.on('mouseenter','pos-circle', function() { map.getCanvas().style.cursor='pointer'; });
    map.on('mouseleave','pos-circle', function() { map.getCanvas().style.cursor=''; });

    // Aplicar datos pendientes
    if(window._pZonas)   { map.getSource('z-src').setData(window._pZonas);  window._pZonas=null; }
    if(window._pTracks)  { map.getSource('t-src').setData(window._pTracks); window._pTracks=null; }
    if(window._pPos)     { map.getSource('p-src').setData(window._pPos);    window._pPos=null; }
    if(window._pAlerts)  { map.getSource('a-src').setData(window._pAlerts); window._pAlerts=null; }
  });

  // Eventos del sidebar
  document.getElementById('geo-back').onclick = function() { window._gBack = true; };
  document.getElementById('geo-refresh').onclick = function() { window._gRefresh = true; };
  document.getElementById('geo-session').onchange = function(e) { window._gSessionChange = e.target.value; };

  function updateLayers() {
    window._gLayerChange = {
      zonas: document.getElementById('geo-l-zonas').checked,
      positions: document.getElementById('geo-l-pos').checked,
      tracks: document.getElementById('geo-l-tracks').checked
    };
  }
  document.getElementById('geo-l-zonas').onchange  = updateLayers;
  document.getElementById('geo-l-pos').onchange    = updateLayers;
  document.getElementById('geo-l-tracks').onchange = updateLayers;

  // Teclado: Esc para volver
  window._gEscHandler = function(e) { if(e.key==='Escape') window._gBack = true; };
  document.addEventListener('keydown', window._gEscHandler);
})()
""")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun destroyGeovisor(): Unit = js("""
(function() {
  window._gReady = false;
  if(window._gMap) { window._gMap.remove(); window._gMap = null; }
  var sb = document.getElementById('geo-sb');   if(sb) sb.parentNode.removeChild(sb);
  var md = document.getElementById('geo-map');  if(md) md.parentNode.removeChild(md);
  if(window._gEscHandler) document.removeEventListener('keydown', window._gEscHandler);
  window._pZonas=null; window._pTracks=null; window._pPos=null; window._pAlerts=null;
  window._gAlertAction=null;
  var cv = document.getElementById('ComposeTarget');
  if(cv) cv.style.display='';
})()
""")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun updateSidebarSessions(sessionsJson: String, selectedId: String?): Unit = js("""
(function(sJson, selId) {
  var sel = document.getElementById('geo-session'); if(!sel) return;
  var sessions = JSON.parse(sJson);
  sel.innerHTML = sessions.map(function(s) {
    var opt = '<option value="' + s.id + '"' + (s.id===selId?' selected':'') + '>';
    opt += (s.active?'🟢 ':'⚪ ') + s.name + '</option>';
    return opt;
  }).join('');
})(sessionsJson, selectedId)
""")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun updateSidebarStats(statsJson: String): Unit = js("""
(function(sJson) {
  var el = document.getElementById('geo-stats'); if(!el) return;
  var s = JSON.parse(sJson);
  function fmt(v) { return s.loaded ? v : '—'; }
  el.innerHTML = '<div style="font-size:10px;font-weight:700;color:#999;letter-spacing:.6px;margin-bottom:8px">ESTADÍSTICAS</div>' +
    '<div style="display:grid;grid-template-columns:1fr 1fr;gap:6px">' +
    statCard('👷', 'En campo', s.workers) +
    statCard('📍', 'Puntos GPS', fmt(s.gps)) +
    statCard('⚠️', 'Alertas', fmt(s.alerts)) +
    statCard('🏘️', 'Manzanas', fmt(s.blocks)) +
    '</div>';
  function statCard(icon, label, val) {
    return '<div style="background:#f0f4ff;border-radius:8px;padding:8px 10px;text-align:center">' +
      '<div style="font-size:18px">' + icon + '</div>' +
      '<div style="font-size:18px;font-weight:700;color:#1a1a2e">' + val + '</div>' +
      '<div style="font-size:10px;color:#888">' + label + '</div></div>';
  }
})(statsJson)
""")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun updateSidebarWorkers(workersJson: String): Unit = js("""
(function(wJson) {
  var el = document.getElementById('geo-workers'); if(!el) return;
  var workers = JSON.parse(wJson);
  var html = '<div style="font-size:10px;font-weight:700;color:#999;letter-spacing:.6px;margin-bottom:8px">TRABAJADORES EN CAMPO</div>';
  if(!workers.length) { html += '<div style="color:#aaa;font-size:12px">Sin posiciones registradas</div>'; }
  else workers.forEach(function(w) {
    html += '<div style="display:flex;align-items:center;gap:8px;margin-bottom:6px;padding:6px 8px;background:#fafafa;border-radius:6px">' +
      '<div style="width:10px;height:10px;border-radius:50%;background:' + w.color + ';flex-shrink:0"></div>' +
      '<div style="flex:1;min-width:0"><div style="font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">' + w.name + '</div>' +
      '<div style="font-size:11px;color:#888">' + w.role + '</div></div>' +
      '<div style="font-size:11px;color:#aaa;white-space:nowrap">' + w.ago + '</div></div>';
  });
  el.innerHTML = html;
})(workersJson)
""")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun updateMapZonas(geojson: String): Unit = js("""
(function(g){var d=JSON.parse(g);if(window._gReady)window._gMap.getSource('z-src').setData(d);else window._pZonas=d;})(geojson)
""")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun updateMapPositions(geojson: String): Unit = js("""
(function(g){var d=JSON.parse(g);if(window._gReady)window._gMap.getSource('p-src').setData(d);else window._pPos=d;})(geojson)
""")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun updateMapTracks(geojson: String): Unit = js("""
(function(g){var d=JSON.parse(g);if(window._gReady)window._gMap.getSource('t-src').setData(d);else window._pTracks=d;})(geojson)
""")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun updateMapAlerts(geojson: String): Unit = js("""
(function(g){var d=JSON.parse(g);if(window._gReady)window._gMap.getSource('a-src').setData(d);else window._pAlerts=d;})(geojson)
""")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun consumeAlertActionJs(): String? = js("""
(function(){ var v=window._gAlertAction; window._gAlertAction=null; return v?JSON.stringify(v):null; })()
""")

private fun consumeAlertAction(): AlertAction? {
    val raw = consumeAlertActionJs() ?: return null
    return try {
        val type = Regex("\"type\":\"([^\"]+)\"").find(raw)?.groupValues?.get(1) ?: return null
        val id   = Regex("\"alertId\":\"([^\"]+)\"").find(raw)?.groupValues?.get(1) ?: return null
        AlertAction(id, type)
    } catch (_: Exception) { null }
}

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun setMapLayerGroup(layerIds: String, visible: Boolean): Unit = js("""
(function(ids, vis){
  if(!window._gReady) return;
  ids.split(',').forEach(function(id){ window._gMap.setLayoutProperty(id,'visibility',vis?'visible':'none'); });
})(layerIds, visible)
""")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun checkGeoBack(): Boolean = js("!!(window._gBack)")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun clearGeoBack(): Unit = js("window._gBack = false")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun checkGeoRefresh(): Boolean = js("!!(window._gRefresh)")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun clearGeoRefresh(): Unit = js("window._gRefresh = false")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun consumeSessionChange(): String? = js("""
(function(){ var v=window._gSessionChange; window._gSessionChange=null; return v||null; })()
""")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun consumeLayerChange(): LayerState? {
    val raw = consumeLayerChangeJs() ?: return null
    return try {
        val zonas     = raw.contains("\"zonas\":true")
        val positions = raw.contains("\"positions\":true")
        val tracks    = raw.contains("\"tracks\":true")
        LayerState(zonas, positions, tracks)
    } catch (_: Exception) { null }
}

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun consumeLayerChangeJs(): String? = js("""
(function(){ var v=window._gLayerChange; window._gLayerChange=null; return v?JSON.stringify(v):null; })()
""")
