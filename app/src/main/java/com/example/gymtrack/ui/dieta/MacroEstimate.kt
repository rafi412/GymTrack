package com.example.gymtrack.ui.dieta // O tu paquete preferido

import kotlinx.serialization.Serializable

@Serializable
data class MacroEstimate(
    val calories: Int? = null, // Nullable para parseo seguro
    val proteinGrams: Int? = null,
    val carbGrams: Int? = null
)