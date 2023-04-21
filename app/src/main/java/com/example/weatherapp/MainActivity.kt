package com.example.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.icu.text.SimpleDateFormat
import android.icu.util.TimeZone
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.example.weatherapp.databinding.ActivityMainBinding
import com.example.weatherapp.models.WeatherResponse
import com.example.weatherapp.network.WeatherService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.util.*
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private var binding: ActivityMainBinding? = null
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private var mLatitude: Double = 0.0
    private var mLongitude: Double = 0.0
    private var mProgressDialog: Dialog? = null

    private lateinit var mSharedPreferences: SharedPreferences


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)
        setUpUI()

        if (!isLocationEnabled()){
            Toast.makeText(
                this,
                "Location is not enabled. Please turn it on in Settings",
                Toast.LENGTH_LONG
            ).show()

            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
            Dexter.withContext(this).withPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ).withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if (report!!.areAllPermissionsGranted()) {
                        requestLocationData()
                    }
                    if (report.isAnyPermissionPermanentlyDenied) {
                        Toast.makeText(
                            this@MainActivity,
                            "Location has been disabled. Please enable it from your settings.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ){
                    showRationaleDialogForPermissions()
                }
            }).onSameThread().check()

        }

    }

    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {
        val mLocationRequest = com.google.android.gms.location.LocationRequest()
        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper())

    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(p0: LocationResult) {
            val mLastLocation: Location? = p0.lastLocation
            mLatitude = mLastLocation!!.latitude
            mLongitude = mLastLocation!!.longitude
            Log.i("Current latitude", "$mLatitude")
            Log.i("Current longitude", "$mLongitude")
            getLocationWeatherDetails()
        }
    }

    private fun getLocationWeatherDetails(){
        if (Constants.isNetworkAvailable(this)){
            val retrofit: Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL).addConverterFactory(GsonConverterFactory.create()).build()

            val service: WeatherService = retrofit.create(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(
                mLatitude, mLongitude, Constants.IMPERIAL_UNIT, Constants.APP_ID)

            showCustomProgressDialog()

            listCall.enqueue(object: Callback<WeatherResponse>{
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>?
                ) {
                    if (response!!.isSuccessful){

                        hideProgressDialog()
                        val weatherList: WeatherResponse? = response.body()

                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()

                        setUpUI()
                        Log.i("Response result:", "$weatherList")
                    }else{
                        when(response.code()){
                            400 -> {
                                Log.e("400 error", "Bad connection")
                            }
                            404 -> {
                                Log.e("404 error", "Not found")
                            }
                            else -> {
                                Log.e("Error:", "Unknown error")
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    hideProgressDialog()
                    Log.e("Error", t.message.toString())
                }

            })

        }else{
            Toast.makeText(this,
                "No interwebz, yo!",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showRationaleDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage("It looks like permissions required for this feature have been disabled. You can enable them under application settings.")
            .setPositiveButton("Go to Settings")
            { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    private fun showCustomProgressDialog(){
        mProgressDialog = Dialog(this)
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog!!.show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                requestLocationData()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun hideProgressDialog(){
        if (mProgressDialog != null){
            mProgressDialog!!.dismiss()
        }
    }

    private fun setUpUI(){

        val weatherResponseJsonString = mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "")

        if (!weatherResponseJsonString.isNullOrEmpty()){

            val weatherList = Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)

            for (i in weatherList!!.weather.indices){
                Log.i("Weather name", weatherList.weather.toString())
                binding?.tvMain?.text = weatherList.weather[i].main
                binding?.tvMainDescription?.text = weatherList.weather[i].description.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(
                        Locale.getDefault()
                    ) else it.toString()
                }
                binding?.tvTemp?.text = "${weatherList.main.temp.roundToInt()} ${setUnitsAccordingToCountryCode(weatherList.sys.country)}"
                binding?.tvSunriseTime?.text = unixTime(weatherList.sys.sunrise)
                binding?.tvSunsetTime?.text = unixTime(weatherList.sys.sunset)
                binding?.tvMax?.text = weatherList.main.temp_max.roundToInt().toString() + " max"
                binding?.tvMin?.text = weatherList.main.temp_min.roundToInt().toString() + " min"
                binding?.tvSpeed?.text = weatherList.wind.speed.roundToInt().toString()
                binding?.tvHumidity?.text = weatherList.main.humidity.toString() + "%"
                binding?.tvName?.text = weatherList.name
                binding?.tvCountry?.text = weatherList.sys.country

                when(weatherList.weather[i].icon){
                    "01d" -> binding?.ivMain?.setImageResource(R.drawable.sunny)
                    "02d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "03d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "04d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "04n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "09d" -> binding?.ivMain?.setImageResource(R.drawable.rain)
                    "10d" -> binding?.ivMain?.setImageResource(R.drawable.rain)
                    "11d" -> binding?.ivMain?.setImageResource(R.drawable.storm)
                    "13d" -> binding?.ivMain?.setImageResource(R.drawable.snowflake)
                    "01n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "02n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "03n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "10n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "11n" -> binding?.ivMain?.setImageResource(R.drawable.rain)
                    "13n" -> binding?.ivMain?.setImageResource(R.drawable.snowflake)
                    "50d" -> binding?.ivMain?.setImageResource(R.drawable.mist)
                    "50n" -> binding?.ivMain?.setImageResource(R.drawable.mist)

                }
            }
        }
    }

    private fun setUnitsAccordingToCountryCode(countryCode: String): String {
        // Fahrenheit if US, Liberia, or Myanmar. Centigrade for the rest of the world
        return if (countryCode == "US" || countryCode == "LR" || countryCode == "MM") {
            "°F"
        } else {
            "°C"
        }
    }

    private fun unixTime(timex: Long): String?{
        val date = Date(timex *1000L)
        val sdf = SimpleDateFormat( "HH:mm")
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

}