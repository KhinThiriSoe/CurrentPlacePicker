package com.khinthirisoe.placepicker

import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.android.synthetic.main.activity_maps.*


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    // New variables for Current Place Picker
    private val TAG = "MapsActivity"
    private var mPlacesClient: PlacesClient? = null
    private var fusedLocationProviderClient: FusedLocationProviderClient? = null

    // The geographical location where the device is currently located. That is, the last-known
    // location retrieved by the Fused Location Provider.
    private var mLastKnownLocation: Location? = null

    // A default location (Sydney, Australia) and default zoom to use when location permission is
    // not granted.
    private val mDefaultLocation = LatLng(-33.8523341, 151.2106085)
    private val DEFAULT_ZOOM = 15f
    private val PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1
    private var mLocationPermissionGranted: Boolean = false

    // Used for selecting the current place.
    private val M_MAX_ENTRIES = 5
    private lateinit var mLikelyPlaceNames: Array<String?>
    private lateinit var mLikelyPlaceAddresses: Array<String?>
    private lateinit var mLikelyPlaceAttributions: Array<String?>
    private lateinit var mLikelyPlaceLatLngs: Array<LatLng?>

    private lateinit var map: GoogleMap
    private lateinit var listPlaces: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        listPlaces = findViewById(R.id.listPlaces)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        //
        // PASTE THE LINES BELOW THIS COMMENT
        //

        // Set up the action toolbar
        setSupportActionBar(toolbar)

        // Initialize the Places client
        val apiKey = getString(R.string.google_maps_key)
        Places.initialize(applicationContext, apiKey)
        mPlacesClient = Places.createClient(this)
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        // Add a marker in Sydney and move the camera
        val sydney = LatLng(-34.0, 151.0)
        map.addMarker(MarkerOptions().position(sydney).title("Marker in Sydney"))
        map.moveCamera(CameraUpdateFactory.newLatLng(sydney))

        // Enable the zoom controls for the map
        map.uiSettings.isZoomControlsEnabled = true

        // Prompt the user for permission.
        getLocationPermission()
    }

    private fun getCurrentPlaceLikelihoods() {
        // Use fields to define the data types to return.
        val placeFields = listOf(
            Place.Field.NAME, Place.Field.ADDRESS,
            Place.Field.LAT_LNG
        )

        // Get the likely places - that is, the businesses and other points of interest that
        // are the best match for the device's current location.
        val request = FindCurrentPlaceRequest.builder(placeFields).build()
        val placeResponse = mPlacesClient!!.findCurrentPlace(request)
        placeResponse.addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                val response = task.result
                // Set the count, handling cases where less than 5 entries are returned.
                val count: Int
                count = if (response!!.placeLikelihoods.size < M_MAX_ENTRIES) {
                    response.placeLikelihoods.size
                } else {
                    M_MAX_ENTRIES
                }

                var i = 0
                mLikelyPlaceNames = arrayOfNulls(count)
                mLikelyPlaceAddresses = arrayOfNulls(count)
                mLikelyPlaceAttributions = arrayOfNulls(count)
                mLikelyPlaceLatLngs = arrayOfNulls(count)

                for (placeLikelihood in response.placeLikelihoods) {
                    val currPlace = placeLikelihood.place
                    mLikelyPlaceNames[i] = currPlace.name
                    mLikelyPlaceAddresses[i] = currPlace.address
                    mLikelyPlaceAttributions[i] = if (currPlace.attributions == null)
                        null
                    else
                        currPlace.attributions!!.joinToString(" ")
                    mLikelyPlaceLatLngs[i] = currPlace.latLng

                    val currLatLng = if (mLikelyPlaceLatLngs[i] == null)
                        ""
                    else
                        mLikelyPlaceLatLngs[i].toString()

                    Log.i(
                        TAG, String.format(
                            "Place " + currPlace.name
                                    + " has likelihood: " + placeLikelihood.likelihood
                                    + " at " + currLatLng
                        )
                    )

                    i++
                    if (i > count - 1) {
                        break
                    }
                }


                // COMMENTED OUT UNTIL WE DEFINE THE METHOD
                // Populate the ListView
                 fillPlacesList()

            } else {
                val exception = task.exception
                if (exception is ApiException) {
                    val apiException = exception as ApiException?
                    Log.e(TAG, "Place not found: " + apiException!!.statusCode)
                }
            }
        }
    }

    private fun getDeviceLocation() {
        /*
         * Get the best and most recent location of the device, which may be null in rare
         * cases when a location is not available.
         */
        try {
            if (mLocationPermissionGranted) {
                val locationResult = fusedLocationProviderClient!!.lastLocation
                locationResult.addOnSuccessListener { location ->

                    if (location != null) {
                        mLastKnownLocation = location
                        Log.d(TAG, "Latitude: " + mLastKnownLocation!!.latitude)
                        Log.d(TAG, "Longitude: " + mLastKnownLocation!!.longitude)
                        map.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(
                                    mLastKnownLocation!!.latitude,
                                    mLastKnownLocation!!.longitude
                                ), DEFAULT_ZOOM
                            )
                        )
                    } else {
                        Log.d(TAG, "Current location is null. Using defaults.")
                        Log.e(TAG, "Exception: %s", location)
                        map.moveCamera(
                            CameraUpdateFactory
                                .newLatLngZoom(mDefaultLocation, DEFAULT_ZOOM)
                        )
                    }
                }

                getCurrentPlaceLikelihoods()

            }
        } catch (e: SecurityException) {
            Log.e("Exception: %s", e.message)
        }

    }

    private fun pickCurrentPlace() {
        if (map == null) {
            return
        }

        if (mLocationPermissionGranted) {
            getDeviceLocation()
        } else {
            // The user has not granted permission.
            Log.i(TAG, "The user did not grant location permission.")

            // Add a default marker, because the user hasn't selected a place.
            map.addMarker(
                MarkerOptions()
                    .title(getString(R.string.default_info_title))
                    .position(mDefaultLocation)
                    .snippet(getString(R.string.default_info_snippet))
            )

            // Prompt the user for permission.
            getLocationPermission()
        }
    }

    private fun getLocationPermission() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        mLocationPermissionGranted = false
        if (ContextCompat.checkSelfPermission(
                this.applicationContext,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mLocationPermissionGranted = true
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        mLocationPermissionGranted = false
        when (requestCode) {
            PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissionGranted = true
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_geolocate -> {
                pickCurrentPlace()
                true
            }

            else ->
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                super.onOptionsItemSelected(item)
        }
    }

    private val listClickedHandler = AdapterView.OnItemClickListener { parent, v, position, id ->
        // position will give us the index of which place was selected in the array
        val markerLatLng = mLikelyPlaceLatLngs[position]
        var markerSnippet = mLikelyPlaceAddresses[position]
        if (mLikelyPlaceAttributions[position] != null) {
            markerSnippet = markerSnippet + "\n" + mLikelyPlaceAttributions[position]
        }

        // Add a marker for the selected place, with an info window
        // showing information about that place.
        map.addMarker(
            MarkerOptions()
                .title(mLikelyPlaceNames[position])
                .position(markerLatLng!!)
                .snippet(markerSnippet)
        )

        // Position the map's camera at the location of the marker.
        map.moveCamera(CameraUpdateFactory.newLatLng(markerLatLng))
    }

    private fun fillPlacesList() {
        // Set up an ArrayAdapter to convert likely places into TextViews to populate the ListView
        val placesAdapter =
            ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mLikelyPlaceNames)
        listPlaces.adapter = placesAdapter
        listPlaces.onItemClickListener = listClickedHandler
    }
}
