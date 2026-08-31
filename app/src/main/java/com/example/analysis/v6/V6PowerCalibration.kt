package com.example.analysis.v6

data class V6PowerCalibration(
    val version: String = "Uncalibrated",
    val deviceProfile: String = "Default"
) {
    fun convertRatioToDiopters(ratio: Double): Double? {
        // DO NOT calculate power if calibration does not exist. Status UNCALIBRATED.
        return null
    }
}
