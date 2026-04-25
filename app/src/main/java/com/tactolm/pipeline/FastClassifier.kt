package com.tactolm.pipeline

import com.tactolm.haptics.Tacton
import com.tactolm.haptics.TactonLibrary

/**
 * FastClassifier — pure Kotlin, no I/O, no coroutines.
 * Classifies notification text to a Tacton in ~1–5ms using ranked keyword rules.
 *
 * Rule priority (highest → lowest):
 *   CRITICAL > HEALTH > TRANSIT > NAVIGATION > SYSTEM > PROCESSING > default(NAV_SLIDE)
 */
object FastClassifier {

    data class Classification(
        val tacton: Tacton,
        val tier: String,
        val confidence: String   // "FAST" — on-device only
    )

    // ── Keyword sets (lowercase) ──────────────────────────────────────────────

    private val CRITICAL_KEYWORDS = setOf(
        "emergency", "ambulance", "fire", "evacuate", "evacuation",
        "shutdown", "gas leak", "flood", "earthquake", "alert",
        "sos", "danger", "critical", "urgent", "immediately",
        "bwssb", "kaveri", "power cut", "outage"
    )

    private val HEALTH_KEYWORDS = setOf(
        "medicine", "medication", "pill", "dose", "tablet",
        "health", "doctor", "appointment", "prescription",
        "hospital", "clinic", "symptom", "fever", "blood pressure",
        "diabetes", "insulin", "aarogya", "dengue", "bbmp health",
        "fumigation", "outbreak", "vaccination", "vaccine"
    )

    private val TRANSIT_KEYWORDS = setOf(
        "bus", "metro", "route", "arriving", "arrival",
        "platform", "station", "train", "namma metro", "bmtc",
        "stop", "departs", "departure", "track", "coach",
        "seat", "ticket", "boarding", "ksrtc"
    )

    private val NAVIGATION_KEYWORDS = setOf(
        "turn right", "turn left", "turn", "rerouting", "reroute",
        "navigation", "next stop", "exit", "u-turn", "straight",
        "destination", "arrived", "maps", "route updated",
        "traffic", "detour"
    )

    private val SYSTEM_KEYWORDS = setOf(
        "otp", "one-time password", "verification code", "confirm",
        "verified", "2fa", "two-factor", "authenticate",
        "login attempt", "password", "sign in"
    )

    private val PROCESSING_KEYWORDS = setOf(
        "processing", "loading", "please wait", "syncing",
        "downloading", "uploading", "in progress", "pending",
        "analyzing", "generating", "connecting"
    )

    // ── Classify entry point ──────────────────────────────────────────────────

    fun classify(title: String, body: String, appName: String): Classification {
        val text = "$appName $title $body".lowercase()

        return when {
            matchesAny(text, CRITICAL_KEYWORDS)    -> Classification(TactonLibrary.PULSE_BURST,  "EMERGENCY",   "FAST")
            matchesAny(text, HEALTH_KEYWORDS)      -> Classification(TactonLibrary.HEALTH_RAMP,  "HEALTH",      "FAST")
            matchesAny(text, TRANSIT_KEYWORDS)     -> Classification(TactonLibrary.SLOW_RAMP,    "TRANSIT",     "FAST")
            matchesAny(text, NAVIGATION_KEYWORDS)  -> Classification(TactonLibrary.NAV_SLIDE,    "NAVIGATION",  "FAST")
            matchesAny(text, SYSTEM_KEYWORDS)      -> Classification(TactonLibrary.CONFIRM_TAP,  "SYSTEM",      "FAST")
            matchesAny(text, PROCESSING_KEYWORDS)  -> Classification(TactonLibrary.WAIT_HOLD,    "PROCESSING",  "FAST")
            else                                   -> Classification(TactonLibrary.CONFIRM_TAP,  "GENERAL",     "FAST")
        }
    }

    private fun matchesAny(text: String, keywords: Set<String>): Boolean =
        keywords.any { text.contains(it) }
}
