package com.example.weatherapp.models

data class Main (
    val temp: Double,
    val pressure: Double,
    val humidity: Int,
    val temp_min: Double,
    val temp_max: Double
        ) : java.io.Serializable