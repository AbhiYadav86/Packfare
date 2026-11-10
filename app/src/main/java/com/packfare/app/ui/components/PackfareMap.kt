package com.packfare.app.ui.components

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** A dot on the map. [number] draws a numbered stop; [label] shows text under it. */
data class MapPin(
    val id: String,
    val lat: Double,
    val lon: Double,
    val color: Color,
    val label: String? = null,
    val number: Int? = null,
    val radius: Float = 6f,
)

/**
 * OpenStreetMap vector map (OpenFreeMap tiles, free, no API key) via MapLibre.
 * Draws pins, an optional route line and the user's location. Tapping a pin calls [onPinClick].
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun PackfareMap(
    pins: List<MapPin>,
    modifier: Modifier = Modifier,
    route: List<Pair<Double, Double>> = emptyList(),
    myLocation: Pair<Double, Double>? = null,
    /** Pins to fit the camera to; defaults to all pins. */
    focus: List<Pair<Double, Double>>? = null,
    onPinClick: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val clickHandler by rememberUpdatedState(onPinClick)
    val pinsNow by rememberUpdatedState(pins)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            // Let the map pan inside scrolling screens instead of the page stealing the gesture.
            setOnTouchListener { v, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) v.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
            getMapAsync { m ->
                m.uiSettings.isRotateGesturesEnabled = false
                m.uiSettings.isTiltGesturesEnabled = false
                m.cameraPosition = CameraPosition.Builder().target(LatLng(22.5, 80.0)).zoom(3.6).build()
                m.setStyle(Style.Builder().fromUri(STYLE_URL)) { s ->
                    addLayers(s)
                    // Push the pins right away, not only from the effect below, so they show on first load.
                    runCatching {
                        (s.getSource(PINS_SOURCE) as? GeoJsonSource)?.setGeoJson(
                            FeatureCollection.fromFeatures(pinsNow.map { it.toFeature() }),
                        )
                    }
                    style = s
                }
                m.addOnMapClickListener { latLng ->
                    val point = m.projection.toScreenLocation(latLng)
                    val hit = m.queryRenderedFeatures(point, PINS_LAYER, NUMBERS_LAYER).firstOrNull()
                    val id = hit?.getStringProperty("id")
                    if (id != null) {
                        clickHandler(id)
                        true
                    } else {
                        false
                    }
                }
                map = m
            }
        }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    // Push data into the map whenever it (or the style) changes.
    LaunchedEffect(style, pins, route, myLocation) {
        val s = style ?: return@LaunchedEffect
        (s.getSource(PINS_SOURCE) as? GeoJsonSource)?.setGeoJson(
            FeatureCollection.fromFeatures(pins.map { it.toFeature() }),
        )
        (s.getSource(ROUTE_SOURCE) as? GeoJsonSource)?.setGeoJson(
            if (route.size >= 2) {
                FeatureCollection.fromFeature(
                    Feature.fromGeometry(LineString.fromLngLats(route.map { (lat, lon) -> Point.fromLngLat(lon, lat) })),
                )
            } else {
                FeatureCollection.fromFeatures(emptyList())
            },
        )
        (s.getSource(ME_SOURCE) as? GeoJsonSource)?.setGeoJson(
            myLocation?.let { (lat, lon) -> FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(lon, lat))) }
                ?: FeatureCollection.fromFeatures(emptyList()),
        )
    }

    // Fit the camera to what matters.
    LaunchedEffect(map, focus ?: pins.map { it.lat to it.lon }, myLocation) {
        val m = map ?: return@LaunchedEffect
        val points = (focus ?: pins.map { it.lat to it.lon }) + listOfNotNull(myLocation)
        when {
            points.size >= 2 -> {
                val bounds = LatLngBounds.Builder().apply { points.forEach { (lat, lon) -> include(LatLng(lat, lon)) } }.build()
                m.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80), 600)
            }
            points.size == 1 -> {
                val (lat, lon) = points.first()
                m.easeCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 9.0), 600)
            }
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun MapPin.toFeature(): Feature =
    Feature.fromGeometry(Point.fromLngLat(lon, lat)).apply {
        addStringProperty("id", id)
        addStringProperty("color", String.format("#%06X", 0xFFFFFF and color.toArgb()))
        addNumberProperty("radius", radius)
        label?.let { addStringProperty("label", it) }
        number?.let { addStringProperty("num", it.toString()) }
    }

private fun addLayers(style: Style) {
    style.addSource(GeoJsonSource(ROUTE_SOURCE))
    style.addSource(GeoJsonSource(PINS_SOURCE))
    style.addSource(GeoJsonSource(ME_SOURCE))

    style.addLayer(
        LineLayer(ROUTE_LAYER, ROUTE_SOURCE).withProperties(
            PropertyFactory.lineColor("#C2500F"),
            PropertyFactory.lineWidth(3.5f),
            PropertyFactory.lineDasharray(arrayOf(2f, 1.5f)),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        ),
    )
    style.addLayer(
        CircleLayer(PINS_LAYER, PINS_SOURCE).withProperties(
            PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.circleRadius(Expression.get("radius")),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(1.5f),
        ),
    )
    style.addLayer(
        SymbolLayer(NUMBERS_LAYER, PINS_SOURCE).withProperties(
            PropertyFactory.textField(Expression.coalesce(Expression.get("num"), Expression.literal(""))),
            PropertyFactory.textColor("#FFFFFF"),
            PropertyFactory.textSize(12f),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true),
        ),
    )
    style.addLayer(
        SymbolLayer(LABELS_LAYER, PINS_SOURCE).withProperties(
            PropertyFactory.textField(Expression.coalesce(Expression.get("label"), Expression.literal(""))),
            PropertyFactory.textColor("#3A1600"),
            PropertyFactory.textHaloColor("#FFFFFF"),
            PropertyFactory.textHaloWidth(1.5f),
            PropertyFactory.textSize(11f),
            PropertyFactory.textOffset(arrayOf(0f, 1.3f)),
            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
            PropertyFactory.textOptional(true),
        ),
    )
    style.addLayer(
        CircleLayer(ME_LAYER, ME_SOURCE).withProperties(
            PropertyFactory.circleColor("#1A73E8"),
            PropertyFactory.circleRadius(7f),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f),
        ),
    )
}

private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val PINS_SOURCE = "pf-pins"
private const val ROUTE_SOURCE = "pf-route"
private const val ME_SOURCE = "pf-me"
private const val PINS_LAYER = "pf-pins-circles"
private const val NUMBERS_LAYER = "pf-pins-numbers"
private const val LABELS_LAYER = "pf-pins-labels"
private const val ROUTE_LAYER = "pf-route-line"
private const val ME_LAYER = "pf-me-dot"

/** Pin colours by cost tier: green cheap, ochre mid, red earth pricey. */
object PinColors {
    val low = Color(0xFF2E8B57)   // leaf green
    val mid = Color(0xFFC98A2B)   // ochre
    val high = Color(0xFFB04A3A)  // red earth
    val stop = Color(0xFF1D3F6E)  // deep lake blue
    val faded = Color(0xFF9E9E9E)
}
