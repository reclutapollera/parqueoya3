package com.example.parqueoya.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch

// Extensión para verificar permisos de ubicación
fun Context.hasLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

// Función para obtener la ubicación actual
fun getCurrentLocation(
    fusedLocationClient: FusedLocationProviderClient,
    context: Context,
    onLocationResult: (Location?) -> Unit
) {
    try {
        // Verificar permisos primero
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            onLocationResult(null)
            return
        }

        // Obtener la ubicación
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                onLocationResult(location)
            }
            .addOnFailureListener { exception ->
                Toast.makeText(context, "Error al obtener ubicación: ${exception.message}", Toast.LENGTH_SHORT).show()
                onLocationResult(null)
            }
    } catch (e: SecurityException) {
        Toast.makeText(context, "Error de seguridad al obtener ubicación", Toast.LENGTH_SHORT).show()
        onLocationResult(null)
    }
}

@Composable
fun MapScreen() {
    val context = LocalContext.current
    var locationPermissionsGranted by remember { 
        mutableStateOf(context.hasLocationPermission()) 
    }
    var isLoading by remember { mutableStateOf(true) }
    var isMapLoaded by remember { mutableStateOf(false) }
    var lastKnownLocation by remember { mutableStateOf<Location?>(null) }
    
    // Ubicación por defecto (Santiago, Chile)
    val defaultLocation = LatLng(-33.4489, -70.6693)
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, 12f)
    }
    
    // Cliente de ubicación
    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context) 
    }
    
    val scope = rememberCoroutineScope()
    
    // Función para centrar el mapa en una ubicación
    fun centerMapOnLocation(location: LatLng) {
        scope.launch {
            try {
                cameraPositionState.animate(
                    update = CameraUpdateFactory.newCameraPosition(
                        CameraPosition(location, 15f, 0f, 0f)
                    ),
                    durationMs = 1000
                )
            } catch (e: Exception) {
                // Manejar error de animación
                e.printStackTrace()
            }
        }
    }
    
    // Lanzador de permisos
    val locationPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationPermissionsGranted = 
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        
        if (locationPermissionsGranted) {
            // Obtener ubicación si los permisos fueron concedidos
            getCurrentLocation(fusedLocationClient, context) { location ->
                lastKnownLocation = location
                if (location == null) {
                    Toast.makeText(
                        context, 
                        "No se pudo obtener la ubicación actual", 
                        Toast.LENGTH_SHORT
                    ).show()
                }
                isLoading = false
            }
        } else {
            // Usar ubicación por defecto si no hay permisos
            Toast.makeText(
                context, 
                "Permisos de ubicación denegados", 
                Toast.LENGTH_LONG
            ).show()
            isLoading = false
        }
    }
    
    // Efecto para manejar la carga inicial y permisos
    LaunchedEffect(Unit) {
        if (locationPermissionsGranted) {
            // Obtener ubicación si ya se tienen permisos
            getCurrentLocation(fusedLocationClient, context) { location: Location? ->
                location?.let { loc ->
                    lastKnownLocation = loc
                    centerMapOnLocation(
                        LatLng(
                            loc.latitude,
                            loc.longitude
                        )
                    )
                }
                isLoading = false
            }
        } else {
            // Solicitar permisos si no se tienen
            locationPermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
    
    // Efecto para centrar el mapa cuando tanto el mapa esté listo como tengamos ubicación
    LaunchedEffect(isMapLoaded, lastKnownLocation) {
        if (isMapLoaded && lastKnownLocation != null) {
            centerMapOnLocation(
                LatLng(
                    lastKnownLocation!!.latitude,
                    lastKnownLocation!!.longitude
                )
            )
            // Opcional: Descomenta la siguiente línea si solo quieres centrar una vez
            // lastKnownLocation = null
        }
    }
    
    // Mostrar el mapa o el indicador de carga
    Box(modifier = Modifier.fillMaxSize()) {
        // Mapa de Google
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = locationPermissionsGranted,
                minZoomPreference = 10f,
                maxZoomPreference = 20f
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = true,
                myLocationButtonEnabled = locationPermissionsGranted,
                mapToolbarEnabled = true
            ),
            onMapLoaded = {
                isMapLoaded = true
            }
        ) {
            // Aquí podrías añadir marcadores u otros elementos del mapa
        }

        // Mostrar indicador de carga si es necesario
        if (isLoading) {
            // Indicador de carga centrado
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
            )
        }
        
        // Botón de ubicación personalizado
        if (locationPermissionsGranted) {
            FloatingActionButton(
                onClick = {
                    lastKnownLocation?.let { location ->
                        centerMapOnLocation(
                            LatLng(location.latitude, location.longitude)
                        )
                    }
                    // Si no hay ubicación conocida, intentar obtener una nueva
                    getCurrentLocation(fusedLocationClient, context) { newLocation: Location? ->
                        if (newLocation != null) {
                            lastKnownLocation = newLocation
                            centerMapOnLocation(
                                LatLng(
                                    newLocation.latitude,
                                    newLocation.longitude
                                )
                            )
                        } else {
                            Toast.makeText(
                                context,
                                "No se pudo obtener la ubicación.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.BottomEnd)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Mi ubicación"
                )
            }
        }
    }
}

