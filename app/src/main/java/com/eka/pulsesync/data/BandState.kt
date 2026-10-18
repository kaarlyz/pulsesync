package com.eka.pulsesync.data

data class BandMetrics(
    // 1. Vitals & Autonomic Nervous System
    val heartRate: Int = 0,
    val spo2: Int = 0,
    val battery: Int = 0,
    val stressLevel: Int = 0,           // curPressure (1-100)
    val bodyEnergy: Int = 0,            // dumpEnergy (0-100)
    val restingHeartRate: Int = 0,
    val hrvSpike: Boolean = false,

    // 2. Motion & Pedometer
    val steps: Int = 0,
    val calories: Int = 0,              // activityCalories
    val totalCalories: Int = 0,         // Basal + Active
    val distanceMeters: Int = 0,

    // 3. 6-Axis Biomechanics (Running / Pebble Mode)
    val groundContactTimeMs: Int = 0,   // newContactTime (ms)
    val flightTimeMs: Int = 0,          // newFlightTime (ms)
    val impactForceG: Float = 0f,       // newImpactForce (G)
    val verticalOscillationCm: Float = 0f, // verticalAmplitude (cm)
    val cadenceSpm: Int = 0,            // cadence (SPM)
    val runningPowerWatt: Int = 0,      // runningPower (W)

    // 4. Hardware & Bridge Metadata
    val isBandConnected: Boolean = false,
    val isLinuxSyncing: Boolean = false,
    val deviceName: String = "Xiaomi Smart Band 10",
    val model: String = "miwear.watch.o66gl",
    val macAddress: String = "04:34:C3:D0:70:D3",
    val authKeyHex: String = "132e1feab2a726c499284af2204445d3",
    val linuxTarget: String = "192.168.1.15:8080",

    // 5. AI Biometric Insights & Inference
    val aiCognitiveStatus: String = "PRIMA (FOKUS AKTIF)",
    val aiCognitiveScore: Int = 88,
    val aiCaloricNetDeficit: Int = -1450,
    val aiSleepReadinessPct: Int = 92,
    val aiRecommendation: String = "Kondisi otonom stabil. Ritme kardio optimal untuk sprint belajar TKA atau drill koding."
)
