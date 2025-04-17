package com.example.gymtrack.ui.dieta

import User
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// Importa tu DTO y User desde sus paquetes correctos
import com.google.ai.client.generativeai.GenerativeModel // Importar GenerativeModel
import com.google.ai.client.generativeai.type.InvalidStateException // Para errores de seguridad
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException
import kotlin.math.roundToInt

// Data class para Metas UI (sin cambios)
data class MacroTargetsUi(
    val calories: Int = 0,
    val protein: Int = 0,
    val carbs: Int = 0
)

// Data class para Consumo UI (sin cambios)
data class DailyIntakeUi(
    var calories: Int = 0,
    var protein: Int = 0,
    var carbs: Int = 0
)

class DietaViewModel : ViewModel() {

    private val db: FirebaseFirestore = Firebase.firestore
    private val auth: FirebaseAuth = Firebase.auth
    private val TAG = "DietaViewModel"

    // LiveData
    private val _macroTargets = MutableLiveData<MacroTargetsUi?>()
    val macroTargets: LiveData<MacroTargetsUi?> = _macroTargets

    private val _dailyIntake = MutableLiveData<DailyIntakeUi>(DailyIntakeUi()) // Inicia en 0
    val dailyIntake: LiveData<DailyIntakeUi> = _dailyIntake

    private val _isLoadingTargets = MutableLiveData<Boolean>() // Loading para metas
    val isLoadingTargets: LiveData<Boolean> = _isLoadingTargets

    private val _isLoadingMacros = MutableLiveData<Boolean>() // Loading para IA
    val isLoadingMacros: LiveData<Boolean> = _isLoadingMacros

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _mealsToday = MutableLiveData<List<MealData>>(emptyList())
    val mealsToday: LiveData<List<MealData>> = _mealsToday
    // ----------------------------------------------------

    // --- NUEVO: LiveData para pasar la estimación de IA al diálogo ---
    private val _estimatedMacros = MutableLiveData<MacroEstimate?>()
    val estimatedMacros: LiveData<MacroEstimate?> = _estimatedMacros

    // Parser JSON (Configurado para ser permisivo)
    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

    init {
        // TODO: Cargar el consumo de hoy desde Firestore al iniciar el ViewModel
        // loadTodayIntakeFromFirestore()
    }

    init {
        loadUserMacroTargets() // Cargar metas al iniciar
        loadTodayIntake()      // Cargar comidas y consumo de hoy
    }

    // --- Carga de Metas ---
    fun loadUserMacroTargets() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            _errorMessage.value = "Usuario no identificado."
            return
        }
        _isLoadingTargets.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val document = db.collection("users").document(userId).get().await()
                if (document.exists()) {
                    val user = document.toObject<User>()
                    if (user != null) {
                        val caloriesTarget = user.caloriasDiarias ?: 0
                        val proteinTarget = user.proteinasDiarias ?: 0
                        val carbsTarget = user.carboDiarios ?: 0
                        // Calcular grasas si no están guardadas

                        if (caloriesTarget > 0) {
                            _macroTargets.postValue(MacroTargetsUi(caloriesTarget, proteinTarget, carbsTarget))
                        } else {
                            _macroTargets.postValue(null)
                            _errorMessage.postValue("Completa tu perfil para ver tus metas.")
                        }
                    } else {
                        _errorMessage.postValue("Error al leer datos del perfil.")
                        _macroTargets.postValue(null)
                    }
                } else {
                    _errorMessage.postValue("Perfil de usuario no encontrado.")
                    _macroTargets.postValue(null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al cargar metas", e)
                _errorMessage.postValue("Error al cargar metas: ${e.message}")
                _macroTargets.postValue(null)
            } finally {
                _isLoadingTargets.postValue(false)
            }
        }
    }

    // --- Llamada a Gemini y Procesamiento ---
    fun getMacrosFromText(userInput: String, generativeModel: GenerativeModel) {
        if (userInput.isBlank()) {
            _errorMessage.postValue("Introduce una descripción de la comida.")
            return
        }
        _isLoadingMacros.value = true // Usar loading específico para IA
        _errorMessage.value = null

        // Prompt pidiendo JSON y especificando claves
        val prompt = """
            Estima los macronutrientes (calorías, proteínas en gramos, carbohidratos en gramos) para la siguiente descripción de comida, asumiendo porciones estándar si no se especifican.
            Responde ÚNICAMENTE con un objeto JSON válido que contenga las claves "calories" (Int), "proteinGrams" (Int) y "carbGrams" (Int). No incluyas ninguna otra explicación ni texto fuera del JSON.

            Comida: "$userInput"
        """.trimIndent()

        viewModelScope.launch {
            try {
                Log.d(TAG, "Enviando prompt a Gemini...")
                val response = generativeModel.generateContent(prompt) // Llamada a la API
                val jsonResponse = response.text // Obtener texto de la respuesta
                Log.d(TAG, "Respuesta de Gemini: $jsonResponse")

                if (jsonResponse != null) {
                    parseAndAddMacros(jsonResponse, userInput) // Pasar descripción original
                } else {
                    Log.e(TAG, "La respuesta de Gemini no contiene texto.")
                    _errorMessage.postValue("La IA no devolvió una respuesta válida.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al generar contenido de Gemini", e)
                // Manejo específico de errores de seguridad
                if (e is InvalidStateException && e.message?.contains("SAFETY", ignoreCase = true) == true) {
                    _errorMessage.postValue("Descripción bloqueada por filtros de seguridad.")
                } else {
                    _errorMessage.postValue("Error al contactar la IA: ${e.message}")
                }
            } finally {
                _isLoadingMacros.postValue(false) // Ocultar loading de IA
            }
        }
    }

    fun loadTodayIntake() {
        val userId = auth.currentUser?.uid ?: return
        val todayId = getTodayDocumentId() // Formato YYYYMMDD

        _isLoadingMacros.value = true // Reutilizar loading o crear uno nuevo
        viewModelScope.launch {
            try {
                val mealsSnapshot = db.collection("users").document(userId)
                    .collection("dailyMeals").document(todayId)
                    .collection("meals")
                    .orderBy("timestamp", Query.Direction.ASCENDING) // Ordenar por hora
                    .get().await()

                val meals = mealsSnapshot.toObjects(MealData::class.java)
                _mealsToday.postValue(meals) // Actualizar lista de comidas

                // Calcular totales iniciales
                var totalCals = 0
                var totalProt = 0
                var totalCarb = 0
                meals.forEach { meal ->
                    totalCals += meal.calories ?: 0
                    totalProt += meal.proteinGrams ?: 0
                    totalCarb += meal.carbGrams ?: 0
                }
                _dailyIntake.postValue(DailyIntakeUi(totalCals, totalProt, totalCarb))
                Log.d(TAG, "Consumo inicial cargado para hoy: C:$totalCals P:$totalProt C:$totalCarb")

            } catch (e: Exception) {
                Log.e(TAG, "Error cargando consumo diario", e)
                _errorMessage.postValue("Error al cargar comidas de hoy.")
                // Resetear a cero si falla la carga
                _mealsToday.postValue(emptyList())
                _dailyIntake.postValue(DailyIntakeUi())
            } finally {
                _isLoadingMacros.postValue(false)
            }
        }
    }

    fun addMealAndUpdateIntake(meal: MealData) {
        val userId = auth.currentUser?.uid ?: return
        val todayId = getTodayDocumentId()

        _isLoadingMacros.value = true // Indicar carga
        viewModelScope.launch {
            try {
                // 1. Guardar la comida en Firestore
                db.collection("users").document(userId)
                    .collection("dailyMeals").document(todayId)
                    .collection("meals")
                    .add(meal) // Firestore añadirá timestamp si MealData lo tiene anotado
                    .await()
                Log.d(TAG, "Comida guardada en Firestore: ${meal.title}")

                // 2. Actualizar LiveData de consumo local (más eficiente que recargar todo)
                addToIntake(
                    meal.calories ?: 0,
                    meal.proteinGrams ?: 0,
                    meal.carbGrams ?: 0
                )

                // 3. Actualizar la lista local de comidas (opcional, si la muestras)
                val currentMeals = _mealsToday.value?.toMutableList() ?: mutableListOf()
                currentMeals.add(meal) // Añadir la nueva comida (sin el ID de Firestore aquí)
                _mealsToday.postValue(currentMeals)


            } catch (e: Exception) {
                Log.e(TAG, "Error al guardar comida o actualizar intake", e)
                _errorMessage.postValue("Error al guardar la comida.")
            } finally {
                _isLoadingMacros.postValue(false)
            }
        }
    }

    private fun getTodayDocumentId(): String {
        // Importa java.text.SimpleDateFormat y java.util.Locale si no están ya
        val sdf = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
        // Importa java.util.Date si no está ya
        return sdf.format(java.util.Date())
    }

    private fun parseAndPostEstimation(jsonString: String) {
        try {
            val cleanJson = jsonString.trim().removeSurrounding("```json\n", "\n```").removeSurrounding("```", "```")
            val estimate = jsonParser.decodeFromString<MacroEstimate>(cleanJson)
            Log.d(TAG, "Macros parseados: $estimate")
            _estimatedMacros.postValue(estimate) // <-- Postear resultado
        } catch (e: Exception) {
            Log.e(TAG, "Error parseando estimación", e)
            _errorMessage.postValue("Error al procesar respuesta IA.")
            _estimatedMacros.postValue(null) // Postear null en error
        }
    }

    // Parsea la respuesta JSON y actualiza el consumo
    private fun parseAndAddMacros(jsonString: String, foodDescription: String) {
        try {
            // Limpiar posible formato markdown
            val cleanJson = jsonString.trim().removeSurrounding("```json\n", "\n```").removeSurrounding("```", "```")
            // Parsear usando la data class DTO
            val estimate = jsonParser.decodeFromString<MacroEstimate>(cleanJson)
            Log.d(TAG, "Macros parseados: $estimate")

            // Obtener valores (usar 0 si son null)
            val cals = estimate.calories ?: 0
            val prot = estimate.proteinGrams ?: 0
            val carb = estimate.carbGrams ?: 0

            // Validar si los valores parseados son razonables (opcional)
            if (cals < 0 || prot < 0 || carb < 0) {
                Log.w(TAG, "Valores de macros parseados negativos: $estimate")
                _errorMessage.postValue("La IA devolvió valores inválidos.")
                return
            }

            // Actualizar el LiveData de consumo
            addToIntake(cals, prot, carb)

            // TODO: Guardar la comida en Firestore
            // saveMealToFirestore(cals, prot, carb, fat, foodDescription)

        } catch (e: SerializationException) {
            Log.e(TAG, "Error al parsear JSON de Gemini: ${e.message}")
            Log.e(TAG, "JSON recibido: $jsonString")
            _errorMessage.postValue("Error al procesar la respuesta de la IA.")
        } catch (e: Exception) {
            Log.e(TAG, "Error inesperado al parsear/añadir: ${e.message}")
            _errorMessage.postValue("Error al procesar macros.")
        }
    }

    // Actualiza el LiveData _dailyIntake sumando los nuevos valores
    private fun addToIntake(calories: Int, protein: Int, carbs: Int) { // Añade fats si lo necesitas
        val currentIntake = _dailyIntake.value ?: DailyIntakeUi()
        // Crear un NUEVO objeto
        val newIntake = DailyIntakeUi(
            calories = currentIntake.calories + calories,
            protein = currentIntake.protein + protein,
            carbs = currentIntake.carbs + carbs
        )
        Log.d(TAG, "addToIntake: Calculado newIntake = $newIntake")

        // --- Usar setValue directamente ---
        _dailyIntake.setValue(newIntake)
        // ---------------------------------

        Log.d(TAG, "addToIntake: _dailyIntake actualizado con setValue.")
    }

    // --- Funciones Pendientes ---
    // private fun loadTodayIntakeFromFirestore() { /* ... */ }
    // private fun saveMealToFirestore(c: Int, p: Int, ca: Int, f: Int, description: String) { /* ... */ }

    // Helper para calcular grasas (sin cambios)

    fun clearTodayMeals() {
        val userId = auth.currentUser?.uid ?: return // Salir si no hay usuario
        val todayId = getTodayDocumentId() // Obtener YYYYMMDD

        _isLoadingMacros.value = true // Indicar carga
        _errorMessage.value = null

        viewModelScope.launch {
            val mealsCollectionRef = db.collection("users").document(userId)
                .collection("dailyMeals").document(todayId)
                .collection("meals")

            try {
                // 1. Obtener todos los documentos de comida de hoy
                val mealsSnapshot = mealsCollectionRef.get().await()

                if (!mealsSnapshot.isEmpty) {
                    // 2. Crear un WriteBatch para borrarlos todos
                    val batch = db.batch()
                    mealsSnapshot.documents.forEach { doc ->
                        batch.delete(doc.reference)
                    }
                    // 3. Ejecutar el borrado en batch
                    batch.commit().await()
                    Log.d(TAG, "Todas las comidas eliminadas para $todayId")
                } else {
                    Log.d(TAG, "No había comidas que eliminar para $todayId")
                }

                // 4. Actualizar LiveData locales a estado vacío/cero
                _mealsToday.postValue(emptyList())
                _dailyIntake.postValue(DailyIntakeUi()) // Resetear consumo a 0

            } catch (e: Exception) {
                Log.e(TAG, "Error al limpiar comidas de hoy", e)
                _errorMessage.postValue("Error al limpiar comidas: ${e.message}")
            } finally {
                _isLoadingMacros.postValue(false) // Ocultar loading
            }
        }
    }

    // Limpiar mensaje de error
    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}