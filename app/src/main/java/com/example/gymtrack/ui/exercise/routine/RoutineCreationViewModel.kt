package com.example.gymtrack.ui.exercise.routine

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.util.Log

data class DayData(val name: String, val order: Int)

data class ExerciseData(
    val name: String,
    val muscleGroup: String?,
    val series: Int?,
    val reps: String?, // String para flexibilidad "8-12"
    val rest: String?,
    val notes: String?,
    var order: Int = 0 // Orden dentro del día, se asignará al añadir
)
// ----------------------------------------------------

enum class SaveStatus { IDLE, LOADING, SUCCESS, ERROR }

class RoutineCreationViewModel : ViewModel() {

    private val _routineName = MutableLiveData<String?>()
    val routineName: LiveData<String?> = _routineName
    private val _routineDescription = MutableLiveData<String?>()
    val routineDescription: LiveData<String?> = _routineDescription

    private val _daysList = MutableLiveData<MutableList<DayData>>(mutableListOf())
    val daysList: LiveData<MutableList<DayData>> = _daysList

    private val _exercisesPerDay = MutableLiveData<MutableMap<Int, MutableList<ExerciseData>>>(mutableMapOf())
    val exercisesPerDay: LiveData<MutableMap<Int, MutableList<ExerciseData>>> = _exercisesPerDay
    // -------------------------------------------------

    private val _saveStatus = MutableLiveData<SaveStatus>(SaveStatus.IDLE)
    val saveStatus: LiveData<SaveStatus> = _saveStatus
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun setRoutineDetails(name: String?, description: String?) {
        _routineName.value = name
        _routineDescription.value = description
    }

    fun addDay(dayName: String) {
        if (dayName.isNotBlank()) {
            val currentDays = _daysList.value ?: mutableListOf()
            val nextOrder = currentDays.size + 1
            val newDay = DayData(dayName, nextOrder)
            currentDays.add(newDay)
            _daysList.value = currentDays // Notificar cambio

            // --- Inicializar lista vacía de ejercicios para este nuevo día ---
            val currentExercisesMap = _exercisesPerDay.value ?: mutableMapOf()
            if (!currentExercisesMap.containsKey(newDay.order)) {
                currentExercisesMap[newDay.order] = mutableListOf()
                _exercisesPerDay.value = currentExercisesMap // Notificar cambio
            }
            // -------------------------------------------------------------
        }
    }

    fun removeDay(dayData: DayData) {
        val currentDays = _daysList.value ?: mutableListOf()
        if (currentDays.remove(dayData)) {
            _daysList.value = currentDays // Notificar cambio

            // --- Eliminar ejercicios asociados a este día ---
            val currentExercisesMap = _exercisesPerDay.value ?: mutableMapOf()
            currentExercisesMap.remove(dayData.order)
            _exercisesPerDay.value = currentExercisesMap // Notificar cambio
        }
    }

    // --- NUEVA función para añadir ejercicio a un día específico ---
    fun addExerciseToDay(dayOrder: Int, exercise: ExerciseData) {
        val currentExercisesMap = _exercisesPerDay.value ?: mutableMapOf()
        val exercisesForDay = currentExercisesMap[dayOrder] ?: mutableListOf()

        // Asignar orden dentro del día
        exercise.order = exercisesForDay.size + 1
        exercisesForDay.add(exercise)

        currentExercisesMap[dayOrder] = exercisesForDay // Actualizar el mapa
        _exercisesPerDay.value = currentExercisesMap // Notificar cambio
    }
    // ---------------------------------------------------------

    // --- NUEVA función para eliminar ejercicio de un día específico ---
    fun removeExerciseFromDay(dayOrder: Int, exerciseToRemove: ExerciseData) {
        val currentExercisesMap = _exercisesPerDay.value ?: return
        val exercisesForDay = currentExercisesMap[dayOrder] ?: return

        if (exercisesForDay.remove(exerciseToRemove)) {
            // Opcional: Reordenar los ejercicios restantes en exercisesForDay
            exercisesForDay.forEachIndexed { index, exercise -> exercise.order = index + 1 }
            currentExercisesMap[dayOrder] = exercisesForDay // Actualizar mapa
            _exercisesPerDay.value = currentExercisesMap // Notificar cambio
        }
    }
    // ----------------------------------------------------------


    fun clearData() {
        _routineName.value = null
        _routineDescription.value = null
        _daysList.value = mutableListOf()
        _exercisesPerDay.value = mutableMapOf() // Limpiar ejercicios también
        _saveStatus.value = SaveStatus.IDLE
        _errorMessage.value = null
    }

    // --- MODIFICADA: Función para guardar todo en Firestore ---
    fun saveRoutineAndDaysToFirestore(userId: String, db: FirebaseFirestore) {
        val name = _routineName.value
        val days = _daysList.value
        val exercisesMap = _exercisesPerDay.value

        // Validación más completa
        if (userId.isBlank() || name.isNullOrBlank() || days.isNullOrEmpty()) {
            _errorMessage.value = "Faltan datos de rutina o no se añadieron días."
            _saveStatus.value = SaveStatus.ERROR
            return
        }
        // Opcional: Validar que cada día tenga al menos un ejercicio
        if (days.any { exercisesMap?.get(it.order).isNullOrEmpty() }) {
            _errorMessage.value = "Algunos días no tienen ejercicios añadidos."
            _saveStatus.value = SaveStatus.ERROR
            return
        }


        _saveStatus.value = SaveStatus.LOADING
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                // 1. Crear el documento de la rutina
                val userRoutinesCollection = db.collection("users").document(userId).collection("userRoutines")
                val newRoutineData = hashMapOf(
                    "nombre" to name,
                    "descripcion" to (_routineDescription.value ?: ""),
                    "createdAt" to FieldValue.serverTimestamp()
                )
                val routineDocRef = userRoutinesCollection.add(newRoutineData).await()
                val routineId = routineDocRef.id
                Log.d("ViewModelSave", "Rutina $routineId creada.")

                // 2. Iterar sobre los días guardados en el ViewModel
                for (dayData in days) {
                    // 2a. Crear el documento del día
                    val routineDaysCollection = routineDocRef.collection("routineDays")
                    val newDayData = hashMapOf(
                        "nombreDia" to dayData.name,
                        "orden" to dayData.order
                    )
                    val dayDocRef = routineDaysCollection.add(newDayData).await() // Esperar a que se cree el día
                    val dayId = dayDocRef.id
                    Log.d("ViewModelSave", "Día ${dayData.order} ($dayId) creado para rutina $routineId.")

                    // 2b. Obtener los ejercicios para este día del ViewModel
                    val exercisesForThisDay = exercisesMap?.get(dayData.order) ?: emptyList()

                    // 2c. Crear los documentos de los ejercicios en la subcolección del día
                    if (exercisesForThisDay.isNotEmpty()) {
                        val dayExercisesCollection = dayDocRef.collection("dayExercises")
                        for (exerciseData in exercisesForThisDay) {
                            val newExerciseData = hashMapOf(
                                "nombre" to exerciseData.name,
                                "grupoMuscular" to exerciseData.muscleGroup,
                                "series" to exerciseData.series,
                                "repeticiones" to exerciseData.reps,
                                "descanso" to exerciseData.rest,
                                "notas" to exerciseData.notes,
                                "orden" to exerciseData.order
                            )
                            dayExercisesCollection.add(newExerciseData).await() // Esperar a que se cree el ejercicio
                        }
                        Log.d("ViewModelSave", "Ejercicios para día ${dayData.order} guardados.")
                    }
                }

                // 3. Éxito
                _saveStatus.value = SaveStatus.SUCCESS
                clearData() // Limpiar ViewModel

            } catch (e: Exception) {
                // 4. Error
                Log.e("ViewModelSave", "Error al guardar rutina/días/ejercicios", e)
                _errorMessage.value = "Error al guardar: ${e.message}"
                _saveStatus.value = SaveStatus.ERROR
                // Considerar: ¿Borrar la rutina si fallan los días/ejercicios? (Transacciones)
            }
        }
    }
    // ------------------------------------------------------

    fun resetSaveStatus() {
        _saveStatus.value = SaveStatus.IDLE
        _errorMessage.value = null
    }
}