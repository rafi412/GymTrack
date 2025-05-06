package com.example.gymtrack.ui.diet

import kotlinx.serialization.Serializable

@Serializable
data class MacroEstimate(
    val calories: Int? = null, // Nullable para parseo seguro
    val proteinGrams: Int? = null,
    val carbGrams: Int? = null
)