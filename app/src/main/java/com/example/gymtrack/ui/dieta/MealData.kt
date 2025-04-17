package com.example.gymtrack.ui.dieta

import com.google.firebase.firestore.ServerTimestamp // Para timestamp
import java.util.Date // Para timestamp

// Representa una comida guardada en Firestore
data class MealData(
    // No necesitamos el ID aquí si lo leemos por separado
    val title: String? = null, // "Desayuno", "Almuerzo", etc.
    val description: String? = null, // Lo que el usuario escribió
    @ServerTimestamp val timestamp: Date? = null, // Hora del registro
    val calories: Int? = null,
    val proteinGrams: Int? = null,
    val carbGrams: Int? = null,
    val fatGrams: Int? = null
) {
    // Constructor vacío para Firestore
    constructor() : this(null, null, null, null, null, null, null)
}