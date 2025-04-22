package com.example.gymtrack.ui.user // O tu paquete preferido

import android.util.Log
import kotlin.math.roundToInt

object MacronutrientCalculator {

    private const val TAG = "MacroCalc"

    // --- Constantes Configurables ---

    // Factores de Actividad (Multiplicadores TDEE)
    // Es crucial que las claves coincidan con lo que guardas/seleccionas en la UI
    private val activityFactors = mapOf(
        "SEDENTARIO" to 1.2,
        "LIGERO" to 1.375,      // Ejercicio ligero 1-3 días/sem
        "MODERADO" to 1.55,     // Ejercicio moderado 3-5 días/sem
        "ACTIVO" to 1.725,      // Ejercicio intenso 6-7 días/sem
        "MUY_ACTIVO" to 1.9     // Ejercicio muy intenso + trabajo físico
    )

    // Ajuste Calórico por Objetivo (kcal/día)
    private val goalAdjustments = mapOf(
        "PERDER_PESO" to -500,  // Déficit moderado
        "PERDER_PESO_LENTO" to -250, // Déficit ligero
        "MANTENER" to 0,
        "GANAR_MASA" to 300,    // Superávit ligero/moderado
        "GANAR_MASA_RAPIDO" to 500 // Superávit mayor
    )

    // Distribución de Macros (Ejemplo estándar, podrías hacerlo configurable)
    // Proteínas: Gramos por kg de peso corporal
    private const val PROTEIN_GRAMS_PER_KG = 1.8
    // Grasas: Porcentaje de las calorías totales
    private const val FAT_PERCENTAGE = 0.25 // 25%

    // Calorías por gramo de cada macro
    private const val KCAL_PER_GRAM_PROTEIN = 4
    private const val KCAL_PER_GRAM_CARB = 4
    private const val KCAL_PER_GRAM_FAT = 9

    // --- Data Class para los Resultados ---
    data class MacroGoals(
        val calories: Int,
        val proteinGrams: Int,
        val carbGrams: Int
    )

    // --- Función Principal de Cálculo ---
    fun calculateGoals(
        age: Int?,
        sex: String?,
        weightKg: Double?,
        heightCm: Double?,
        activityLevelKey: String?, // Clave como "SEDENTARIO", "LIGERO", etc.
        goalKey: String?          // Clave como "PERDER_PESO", "MANTENER", etc.
    ): MacroGoals? { // Devuelve nullable por si faltan datos

        // Validación de entradas básicas
        if (age == null || age <= 0 || sex.isNullOrBlank() || weightKg == null || weightKg <= 0 || heightCm == null || heightCm <= 0 || activityLevelKey.isNullOrBlank() || goalKey.isNullOrBlank()) {
            Log.e(TAG, "Datos de entrada inválidos o incompletos.")
            return null
        }

        // 1. Calcular BMR (Tasa Metabólica Basal) - Fórmula Mifflin-St Jeor
        val bmr = calculateBMR(age, sex, weightKg, heightCm)
        if (bmr == null) return null // Error en el cálculo de BMR
        Log.d(TAG, "BMR Calculado: $bmr kcal")

        // 2. Calcular TDEE (Gasto Energético Diario Total)
        val tdee = calculateTDEE(bmr, activityLevelKey)
        if (tdee == null) return null // Error en el cálculo de TDEE
        Log.d(TAG, "TDEE Calculado: $tdee kcal")

        // 3. Ajustar Calorías según Objetivo
        val targetCalories = adjustCaloriesForGoal(tdee, goalKey)
        if (targetCalories == null) return null // Error en el ajuste por objetivo
        Log.d(TAG, "Calorías Objetivo: $targetCalories kcal")


        // 4. Calcular Macros
        try {
            // Proteínas (basado en g/kg)
            val proteinGrams = (weightKg * PROTEIN_GRAMS_PER_KG).roundToInt()
            val proteinCalories = proteinGrams * KCAL_PER_GRAM_PROTEIN

            // Grasas (basado en % de calorías totales)
            val fatGrams = ((targetCalories * FAT_PERCENTAGE) / KCAL_PER_GRAM_FAT).roundToInt()
            val fatCalories = fatGrams * KCAL_PER_GRAM_FAT

            // Carbohidratos (calorías restantes)
            val carbCalories = targetCalories - proteinCalories - fatCalories
            // Asegurar que los carbos no sean negativos
            if (carbCalories < 0) {
                Log.w(TAG, "Calorías de carbohidratos negativas ($carbCalories). El objetivo calórico ($targetCalories) podría ser demasiado bajo para la proteína ($proteinCalories kcal) y grasa ($fatCalories kcal) calculadas.")
                // Podrías lanzar un error, devolver null, o ajustar otros macros (más complejo)
                // Por ahora, devolveremos 0g de carbos y ajustaremos calorías si es necesario.
                val adjustedCalories = proteinCalories + fatCalories // Recalcular calorías si carbos son 0
                Log.w(TAG, "Ajustando calorías objetivo a $adjustedCalories kcal debido a carbos negativos.")
                return MacroGoals(
                    calories = adjustedCalories,
                    proteinGrams = proteinGrams,
                    carbGrams = carbCalories, // Poner a 0
                )

            }
            val carbGrams = (carbCalories.toDouble() / KCAL_PER_GRAM_CARB).roundToInt()


            Log.d(TAG, "Macros: P:${proteinGrams}g (${proteinCalories}kcal), C:${carbGrams}g (${carbCalories}kcal), F:${fatGrams}g (${fatCalories}kcal)")

            return MacroGoals(
                calories = targetCalories,
                proteinGrams = proteinGrams,
                carbGrams = carbGrams
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error durante el cálculo de macros", e)
            return null
        }
    }

    // --- Funciones Helper ---

    private fun calculateBMR(age: Int, sex: String, weightKg: Double, heightCm: Double): Double? {
        return try {
            if (sex.equals("Masculino", ignoreCase = true)) {
                (10.0 * weightKg) + (6.25 * heightCm) - (5.0 * age) + 5.0
            } else if (sex.equals("Femenino", ignoreCase = true)) {
                (10.0 * weightKg) + (6.25 * heightCm) - (5.0 * age) - 161.0
            } else {
                Log.e(TAG, "Sexo no reconocido para BMR: '$sex'")
                null // O podrías usar un promedio o lanzar error
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculando BMR", e)
            null
        }
    }

    private fun calculateTDEE(bmr: Double, activityLevelKey: String): Double? {
        val factor = activityFactors[activityLevelKey.uppercase()]
        return if (factor != null) {
            bmr * factor
        } else {
            Log.e(TAG, "Factor de actividad no encontrado para la clave: '$activityLevelKey'")
            null
        }
    }

    private fun adjustCaloriesForGoal(tdee: Double, goalKey: String): Int? {
        val adjustment = goalAdjustments[goalKey.uppercase()]
        return if (adjustment != null) {
            (tdee + adjustment).roundToInt() // Redondear al entero más cercano
        } else {
            Log.e(TAG, "Ajuste de objetivo no encontrado para la clave: '$goalKey'")
            null
        }
    }
}