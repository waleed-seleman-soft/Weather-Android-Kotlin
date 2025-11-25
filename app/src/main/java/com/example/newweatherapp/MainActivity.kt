package com.example.newweatherapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.example.newweatherapp.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val apiKey = "1c4b1991dad84adc96d121101252511"
    private val client = OkHttpClient()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val locationPermissionCode = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.editCity.setOnEditorActionListener { v, actionId, event ->
            val city = binding.editCity.text.toString().trim()
            if (city.isNotEmpty()) {
                fetchWeather(city)
            }
            true  // prevent newline
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        getUserLocation()

        binding.btnSearch.setOnClickListener {
            val city = binding.editCity.text.toString().trim()
            if (city.isEmpty()) {
                Toast.makeText(this, "Enter a city name", Toast.LENGTH_SHORT).show()
            } else {
                fetchWeather(city)
            }
        }

        fetchWeather("Riyadh")
    }

    private fun getUserLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                locationPermissionCode
            )
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val lat = location.latitude
                val lon = location.longitude

                fetchWeather("$lat,$lon")   // WeatherAPI supports "lat,lon" directly
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == locationPermissionCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getUserLocation()
            }
        }
    }

    private fun fetchWeather(city: String) {
        binding.progress.visibility = View.VISIBLE

        lifecycleScope.launch {
            val json = withContext(Dispatchers.IO) { callWeatherApi(city) }

            if (json == null) {
                binding.progress.visibility = View.GONE
                Toast.makeText(this@MainActivity, "Failed to load weather", Toast.LENGTH_SHORT).show()
                return@launch
            }

            try {
                val root = JSONObject(json)
                val location = root.getJSONObject("location")
                val current = root.getJSONObject("current")

                val cityName = "${location.getString("name")}, ${location.getString("country")}"
                val temp = current.getDouble("temp_c")
                val condition = current.getJSONObject("condition").getString("text")
                val isDay = current.getInt("is_day")
                val humidity = current.getInt("humidity")
                val pressure = current.getDouble("pressure_mb")
                val wind = current.getDouble("wind_kph")
                val updatedEpoch = current.getLong("last_updated_epoch")

                val updatedTime = "Updated " +
                        SimpleDateFormat("hh:mm a", Locale.getDefault())
                            .format(Date(updatedEpoch * 1000))

                val iconRes = getIconRes(condition, isDay)

                binding.apply {
                    tvCity.text = cityName
                    tvUpdated.text = updatedTime
                    tvTemp.text = "${temp.toInt()}°C"
                    tvCondition.text = condition
                    tvMin.text = "Min ${temp.toInt()}°C"
                    tvMax.text = "Max ${temp.toInt()}°C"
                    tvWind.text = "${wind.toInt()} km/h"
                    tvPressure.text = "${pressure.toInt()} mb"
                    tvHumidity.text = "$humidity%"
                    imgIcon.setImageResource(iconRes)
                }

            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Invalid server response", Toast.LENGTH_SHORT).show()
            }

            binding.progress.visibility = View.GONE
        }
    }

    private fun callWeatherApi(city: String): String? {
        return try {
            val encodedCity = URLEncoder.encode(city, "UTF-8")
            val url =
                "https://api.weatherapi.com/v1/current.json?key=$apiKey&q=$encodedCity&aqi=no"

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) return null

            response.body?.string()

        } catch (e: Exception) {
            null
        }
    }

    private fun getIconRes(condition: String, isDay: Int): Int {
        val c = condition.lowercase(Locale.US)
        return when {
            "thunder" in c -> R.drawable.thunderstorm
            "drizzle" in c -> R.drawable.drizzle
            "snow" in c -> R.drawable.snow
            "rain" in c -> R.drawable.rain
            "fog" in c || "mist" in c || "haze" in c -> R.drawable.broken_clouds
            "overcast" in c -> R.drawable.broken_clouds
            "cloud" in c ->
                if (isDay == 1) R.drawable.few_clouds_day else R.drawable.few_clouds_night
            else ->
                if (isDay == 1) R.drawable.clear_day else R.drawable.clear_night
        }
    }
}
