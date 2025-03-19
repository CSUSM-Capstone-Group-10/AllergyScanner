package com.example.allergyscanner

data class ScanHistoryItem(
    val dateTime: Long,        // or store as String
    val imageUri: String,      // path to the cropped image
    val recognizedText: String // or any other details
)
