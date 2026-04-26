package com.tactolm.haptics

/**
 * TactonLibrary — single source of truth for all haptic waveform definitions.
 *
 * Each Tacton specifies:
 *  - [timings]   LongArray — alternating ON/OFF durations in milliseconds.
 *                Odd indices are silence gaps. Even indices are vibration periods.
 *  - [amps]      IntArray  — amplitude per segment (0 = silence, 255 = max).
 *  - [repeatIdx] Int       — index to loop from (-1 = play once, 0 = loop forever).
 *
 * MIUI/HyperOS AMPLITUDE NOTE:
 * The Poco X7 Pro's HyperOS remaps amplitude non-linearly. All values use
 * exaggerated contrast (e.g. 60 vs 220) rather than linear scaling so the
 * perceptual difference survives the firmware's compression curve.
 */
data class Tacton(
    val id: String,
    val displayName: String,
    val description: String,
    val urgencyTier: UrgencyTier,
    val timings: LongArray,
    val amps: IntArray,
    val repeatIdx: Int = -1   // -1 = play once
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Tacton) return false
        return id == other.id
    }
    override fun hashCode() = id.hashCode()
}

enum class UrgencyTier { CRITICAL, HEALTH, INFORMATIONAL, SOCIAL, AMBIENT, SYSTEM }

object TactonLibrary {

    // ─────────────────────────────────────────────────────────────────────────
    // TACTON 1 — pulse_burst
    // Target: Pacinian corpuscles. Three hard, sharp pulses — unmistakably urgent.
    // 200ms trailing silence gives it a discrete-event feel, not background noise.
    // ─────────────────────────────────────────────────────────────────────────
    val PULSE_BURST = Tacton(
        id          = "pulse_burst",
        displayName = "Pulse Burst",
        description = "Three sharp hard pulses — CRITICAL urgency",
        urgencyTier = UrgencyTier.CRITICAL,
        timings     = longArrayOf(80, 40, 80, 40, 80, 200),
        amps        = intArrayOf( 255,  0,255,  0,255,   0)
    )

    // ─────────────────────────────────────────────────────────────────────────
    // TACTON 2 — health_ramp
    // Target: Meissner (ramp) + Pacinian (confirming double-tap).
    // Rising swell + double tap at end = "attend to health information".
    // ─────────────────────────────────────────────────────────────────────────
    val HEALTH_RAMP = Tacton(
        id          = "health_ramp",
        displayName = "Health Ramp",
        description = "Rising swell + double tap — HEALTH tier",
        urgencyTier = UrgencyTier.HEALTH,
        timings     = longArrayOf(50, 10, 50, 10, 50, 10, 50, 10, 100, 80, 60, 40, 60,   0),
        amps        = intArrayOf( 60,  0,100,  0,150,  0,200,  0,  220,  0,200,  0,200,  0)
    )

    // ─────────────────────────────────────────────────────────────────────────
    // TACTON 3 — grit_texture
    // Target: Meissner corpuscles. Irregular timing creates a texture percept,
    // NOT a pulse train. Do NOT regularize these timings.
    // ─────────────────────────────────────────────────────────────────────────
    val GRIT_TEXTURE = Tacton(
        id          = "grit_texture",
        displayName = "Grit Texture",
        description = "Irregular surface texture — SOCIAL / AMBIENT",
        urgencyTier = UrgencyTier.SOCIAL,
        timings     = longArrayOf(15, 20, 12, 25, 18, 15, 10, 30, 14, 20, 16, 18, 11, 25, 13, 20, 15, 22, 12,  0),
        amps        = intArrayOf( 70,  0, 80,  0, 65,  0, 75,  0, 85,  0, 70,  0, 80,  0, 65,  0, 75,  0, 70,  0)
    )

    // ─────────────────────────────────────────────────────────────────────────
    // TACTON 4 — slow_ramp
    // Target: Meissner corpuscles. Long, gentle build-up.
    // ─────────────────────────────────────────────────────────────────────────
    val SLOW_RAMP = Tacton(
        id          = "slow_ramp",
        displayName = "Slow Ramp",
        description = "Long gentle build-up — INFORMATIONAL tier",
        urgencyTier = UrgencyTier.INFORMATIONAL,
        timings     = longArrayOf(100, 20, 100, 20, 100, 20, 200, 0),
        amps        = intArrayOf( 40,  0,  80,  0, 120,  0, 160, 0)
    )

    // ─────────────────────────────────────────────────────────────────────────
    // TACTON 5 — confirm_tap
    // Two clean symmetric taps — system confirmation response.
    // ─────────────────────────────────────────────────────────────────────────
    val CONFIRM_TAP = Tacton(
        id          = "confirm_tap",
        displayName = "Confirm Tap",
        description = "Two clean taps — system confirmation",
        urgencyTier = UrgencyTier.SYSTEM,
        timings     = longArrayOf(50, 80, 50,  0),
        amps        = intArrayOf(200,  0,200,  0)
    )

    // ─────────────────────────────────────────────────────────────────────────
    // TACTON 5 — wait_hold
    // Two pulses with different amplitudes (100 vs 80) to signal the system is
    // processing, not frozen. A frozen app would produce identical pulses.
    // ─────────────────────────────────────────────────────────────────────────
    val WAIT_HOLD = Tacton(
        id          = "wait_hold",
        displayName = "Wait / Hold",
        description = "Two amplitude-varying pulses — system processing",
        urgencyTier = UrgencyTier.SYSTEM,
        timings     = longArrayOf(300, 50, 300,   0),
        amps        = intArrayOf( 100,  0,  80,   0)
    )

    // ─────────────────────────────────────────────────────────────────────────
    // TACTON 6 — nav_slide
    // Fast-attack, slow-decay temporal envelope. Encodes sequential/navigation
    // context. NOTE: do NOT describe as spatially directional — single-motor
    // devices cannot produce spatial directionality. Call it
    // "sequential enumeration signal" in the pitch.
    // ─────────────────────────────────────────────────────────────────────────
    val NAV_SLIDE = Tacton(
        id          = "nav_slide",
        displayName = "Nav Slide",
        description = "Fast-attack slow-decay — sequential enumeration signal",
        urgencyTier = UrgencyTier.INFORMATIONAL,
        timings     = longArrayOf(20,  5, 20,  5, 20,  5, 40, 10, 60, 10, 80, 10, 80,   0),
        amps        = intArrayOf(120,  0,160,  0,200,  0,220,  0,190,  0,140,  0, 80,   0)
    )

    // ─────────────────────────────────────────────────────────────────────────
    // TACTON 7 — doorbell_chime
    // Simulates a classic "ding-dong" doorbell with two notes.
    // High-amplitude short pulse (ding) followed by lower-amplitude longer pulse (dong).
    // Repeated twice for clarity.
    // ─────────────────────────────────────────────────────────────────────────
    val DOORBELL_CHIME = Tacton(
        id          = "doorbell_chime",
        displayName = "Doorbell Chime",
        description = "Classic ding-dong simulation — INFORMATIONAL tier",
        urgencyTier = UrgencyTier.INFORMATIONAL,
        timings     = longArrayOf(150, 100, 300, 400, 150, 100, 300,   0),
        amps        = intArrayOf(255,   0, 160,   0, 255,   0, 160,   0)
    )

    // ── Lookup helpers ────────────────────────────────────────────────────────

    val ALL: List<Tacton> = listOf(
        PULSE_BURST, HEALTH_RAMP, GRIT_TEXTURE, SLOW_RAMP, CONFIRM_TAP, WAIT_HOLD, NAV_SLIDE, DOORBELL_CHIME
    )

    private val byId: Map<String, Tacton> = ALL.associateBy { it.id }

    fun getById(id: String): Tacton? = byId[id]
}
