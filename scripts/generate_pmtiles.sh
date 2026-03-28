#!/usr/bin/env bash
# generate_pmtiles.sh
# Descarga OSM de la Provincia de Rioja y genera PMTiles para uso offline
# Requiere: tippecanoe, pmtiles CLI, osmconvert, osmfilter

set -e

REGION="rioja_san_martin"
BBOX="-77.6,-6.8,-76.6,-5.8"   # bbox aproximado Provincia de Rioja
ZOOM_MIN=10
ZOOM_MAX=17
OUTPUT="rioja_${ZOOM_MIN}_${ZOOM_MAX}.pmtiles"
BUCKET="map-tiles"
SUPABASE_PROJECT="mltudqhjsqmnospewgxa"

echo "==> Descargando OSM de Rioja..."
curl -L "https://overpass-api.de/api/map?bbox=${BBOX}" -o "${REGION}.osm"

echo "==> Convirtiendo a GeoJSON..."
# Requiere osmtogeojson: npm install -g osmtogeojson
osmtogeojson "${REGION}.osm" > "${REGION}.geojson"

echo "==> Generando PMTiles con tippecanoe..."
tippecanoe \
  --output="${OUTPUT}" \
  --minimum-zoom=${ZOOM_MIN} \
  --maximum-zoom=${ZOOM_MAX} \
  --drop-densest-as-needed \
  --layer=roads \
  "${REGION}.geojson"

echo "==> Subiendo a Supabase Storage..."
supabase storage cp "${OUTPUT}" "ss://${BUCKET}/${OUTPUT}" \
  --project-ref "${SUPABASE_PROJECT}"

echo "==> Listo: ${OUTPUT} subido a bucket '${BUCKET}'"
echo "    URL de descarga en-app: https://${SUPABASE_PROJECT}.supabase.co/storage/v1/object/public/${BUCKET}/${OUTPUT}"

# Limpieza
rm -f "${REGION}.osm" "${REGION}.geojson"
