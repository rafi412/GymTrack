package com.example.gymtrack.ui.exercise.routine // O tu paquete

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.Gravity // Importar Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button // Importar Button
import android.widget.ImageButton // Importar ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat // Para colores
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController // Para popBackStack
// Importa navArgs si finalmente decides usar SafeArgs
// import androidx.navigation.fragment.navArgs
import com.example.gymtrack.R // Importa tu R
import com.example.gymtrack.databinding.FragmentRoutineDetailBinding
import com.example.gymtrack.ui.AddOrEditDayDialogFragment
import com.example.gymtrack.ui.routine_detail.AddOrEditExerciseDialogFragment
import com.example.gymtrack.ui.routine_detail.ExerciseUpdateListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext


interface RoutineDetailsUpdateListener {
    fun onRoutineDetailsUpdated()
}

interface DayUpdateListener {
    fun onDayUpdated()
}

// ----------------------------------------------------

class RoutineDetailFragment : Fragment(), EditRoutineDetailsDialogFragment.RoutineDetailsUpdateListener,
    DayUpdateListener, ExerciseUpdateListener // Listener para editar/añadir ejercicio
{

    private var _binding: FragmentRoutineDetailBinding? = null
    private val binding get() = _binding!!

    // --- Recepción de Argumentos SIN Safe Args ---
    private var routineId: String? = null
    // -------------------------------------------

    // Firebase
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private val TAG = "RoutineDetailFragment"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { bundle ->
            routineId = bundle.getString("routineId")
        }
        if (routineId.isNullOrEmpty()) {
            Log.e(TAG, "Error crítico: routineId no recibido o vacío.")
            Toast.makeText(requireContext(), "Error al cargar la rutina.", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRoutineDetailBinding.inflate(inflater, container, false)
        db = Firebase.firestore
        auth = Firebase.auth
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        routineId?.let { id ->
            Log.d(TAG, "Cargando detalles para routineId: $id")
            loadRoutineDetails(id)
            setupEditListeners() // Configura botón editar rutina principal
        } ?: run {
            Log.e(TAG, "onViewCreated: routineId es null.")
            handleLoadError("No se pudo cargar la rutina (ID no válido).")
        }
    }

    // Configura SOLO el listener para editar los detalles de la RUTINA
    private fun setupEditListeners() {
        binding.buttonEditRoutineDetails.setOnClickListener {
            val currentName = binding.textRoutineDetailName.text.toString()
            if (currentName == "Error") {
                Toast.makeText(context, "No se pueden editar los detalles.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val currentDesc = binding.textRoutineDetailDesc.text.toString()
            routineId?.let { id ->
                val dialog = EditRoutineDetailsDialogFragment.newInstance(id, currentName, currentDesc)
                dialog.show(childFragmentManager, "EditRoutineDetailsDialog")
            } ?: Toast.makeText(context, "Error ID rutina", Toast.LENGTH_SHORT).show()
        }
        // Los listeners para editar días/ejercicios se añaden dinámicamente en updateUI
    }

    // --- Implementación de los listeners de los diálogos ---
    override fun onRoutineDetailsUpdated() {
        Log.d(TAG, "Detalles de rutina actualizados, recargando...")
        routineId?.let { loadRoutineDetails(it) }
    }

    override fun onDayUpdated() {
        Log.d(TAG, "Día actualizado o añadido, recargando...")
        routineId?.let { loadRoutineDetails(it) }
    }

    override fun onExerciseUpdated() {
        Log.d(TAG, "Ejercicio actualizado o añadido, recargando...")
        routineId?.let { loadRoutineDetails(it) }
    }
    // ----------------------------------------------------


    // Carga los datos de Firestore (sin cambios en la lógica de carga)
    private fun loadRoutineDetails(routineIdToLoad: String) {
        showLoading(true)
        val userId = auth.currentUser?.uid
        if (userId == null) { handleLoadError("Error: Usuario no identificado"); return }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val routineDocRef = db.collection("users").document(userId)
                    .collection("userRoutines").document(routineIdToLoad)
                val routineSnapshot = routineDocRef.get().await()
                if (!routineSnapshot.exists()) { /* ... handle error ... */ return@launch }

                val routineName = routineSnapshot.getString("nombre") ?: "Rutina sin nombre"
                val routineDesc = routineSnapshot.getString("descripcion")

                val daysSnapshot = routineDocRef.collection("routineDays")
                    .orderBy("orden", Query.Direction.ASCENDING).get().await()

                val daysWithExercisesData = mutableListOf<Pair<DocumentSnapshot, List<DocumentSnapshot>>>()
                for (dayDoc in daysSnapshot.documents) {
                    val exercisesSnapshot = dayDoc.reference.collection("dayExercises")
                        .orderBy("orden", Query.Direction.ASCENDING).get().await()
                    daysWithExercisesData.add(Pair(dayDoc, exercisesSnapshot.documents))
                }

                withContext(Dispatchers.Main) {
                    if (_binding != null) {
                        updateUI(routineName, routineDesc, daysWithExercisesData)
                        showLoading(false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al cargar detalles de rutina", e)
                withContext(Dispatchers.Main) {
                    if (_binding != null) { handleLoadError("Error al cargar datos: ${e.message}") }
                }
            }
        }
    }

    // --- updateUI ACTUALIZADA con botones ---
    // Dentro de RoutineDetailFragment.kt -> updateUI

    // --- updateUI REFACTORIZADA para mejor estructura ---
    private fun updateUI(
        name: String,
        description: String?,
        daysWithExercises: List<Pair<DocumentSnapshot, List<DocumentSnapshot>>>
    ) {
        binding.textRoutineDetailName.text = name
        binding.textRoutineDetailDesc.text = description ?: ""
        binding.textRoutineDetailDesc.isVisible = !description.isNullOrBlank()

        binding.layoutDaysContainer.removeAllViews() // Limpiar

        // --- Botón Añadir Día ---
        val addDayButton = Button(requireContext()).apply {
            text = "AÑADIR DÍA"
            setOnClickListener {
                Log.d(TAG, "Botón AÑADIR DÍA clickeado") // <-- LOG AQUÍ
                routineId?.let { rId ->
                    val dialog = AddOrEditDayDialogFragment.newInstance(rId, null, null)
                    dialog.setTargetFragment(this@RoutineDetailFragment, 0)
                    dialog.show(parentFragmentManager, "AddDayDialog")
                } ?: Log.e(TAG, "Error: routineId es null al intentar añadir día")
            }
        }
        binding.layoutDaysContainer.addView(addDayButton)
        // ------------------------

        if (daysWithExercises.isEmpty()) {
            val noDaysTextView = TextView(requireContext()).apply { /* ... */ }
            binding.layoutDaysContainer.addView(noDaysTextView)
        } else {
            daysWithExercises.forEachIndexed { dayIndex, (dayDoc, exerciseDocs) -> // Añadido dayIndex
                val dayId = dayDoc.id
                val dayOrder = dayDoc.getLong("orden") ?: (dayIndex + 1) // Usar índice si falta orden
                val dayName = dayDoc.getString("nombreDia") ?: "Día $dayOrder"

                // --- Contenedor Principal para UN DÍA (Título + Botones + Layout Ejercicios + Divisor) ---
                val dayContainerLayout = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    // Añadir padding o margen si se desea separar visualmente los días
                    // setPadding(0, 0, 0, dpToPx(8))
                }
                // ----------------------------------------------------------------------------------

                // --- Fila Horizontal para Título del Día y Botones de Día ---
                val dayHeaderRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    gravity = Gravity.CENTER_VERTICAL
                }

                val dayTitleTextView = TextView(requireContext()).apply {
                    text = dayName
                    setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Large)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f) // Peso 1
                }

                val editDayButton = ImageButton(requireContext()).apply {
                    setImageResource(R.drawable.baseline_mode_edit_24)
                    setOnClickListener {
                        Log.d(TAG, "Botón EDITAR DÍA clickeado para día ID: $dayId") // <-- LOG AQUÍ
                        routineId?.let { rId ->
                            val dialog = AddOrEditDayDialogFragment.newInstance(rId, dayId, dayName)
                            dialog.setTargetFragment(this@RoutineDetailFragment, 0)
                            dialog.show(parentFragmentManager, "EditDayDialog_$dayId")
                        } ?: Log.e(TAG, "Error: routineId es null al intentar editar día")
                    }
                }

                val deleteDayButton = ImageButton(requireContext()).apply {
                    setImageResource(R.drawable.baseline_delete_24)
                    setBackgroundResource(android.R.color.transparent)
                    contentDescription = "Eliminar día $dayName"
                    setOnClickListener { showDeleteConfirmationDialog("Día", dayName) { deleteRoutineDay(dayId, dayName) } }
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dpToPx(8) }
                }

                dayHeaderRow.addView(dayTitleTextView)
                dayHeaderRow.addView(editDayButton)
                dayHeaderRow.addView(deleteDayButton)
                dayContainerLayout.addView(dayHeaderRow) // Añadir fila de encabezado al contenedor del día
                // -----------------------------------------------------------

                // --- Layout Vertical para la Lista de Ejercicios + Botón Añadir Ejercicio ---
                val exercisesSectionLayout = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginStart = dpToPx(16) // Indentación para toda la sección de ejercicios
                        topMargin = dpToPx(4)
                        // bottomMargin = dpToPx(8) // Margen se controlará por el divisor
                    }
                }

                // Añadir ejercicios
                if (exerciseDocs.isEmpty()) {
                    val noExerciseTextView = TextView(requireContext()).apply {
                        text = "  • Sin ejercicios añadidos."
                        setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small)
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dpToPx(4)}
                    }
                    exercisesSectionLayout.addView(noExerciseTextView)
                } else {
                    exerciseDocs.forEach { exerciseDoc ->
                        val exerciseId = exerciseDoc.id
                        val exerciseDataMap = exerciseDoc.data ?: mapOf()
                        val exerciseName = exerciseDoc.getString("nombre") ?: "Ejercicio"
                        // ... (obtener series, reps, formatear detailString) ...
                        var detailString = ""
                        val series = exerciseDoc.getLong("series")
                        val reps = exerciseDoc.getString("repeticiones")
                        series?.let { detailString += "${it}x" }
                        reps?.takeIf { it.isNotBlank() }?.let { detailString += "$it" }


                        // Fila Horizontal para UN Ejercicio + Botones de Ejercicio
                        val exerciseRowLayout = LinearLayout(requireContext()).apply {
                            orientation = LinearLayout.HORIZONTAL
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                            gravity = Gravity.CENTER_VERTICAL
                        }

                        val exerciseTextView = TextView(requireContext()).apply {
                            text = "  • $exerciseName${if (detailString.isNotEmpty()) ": $detailString" else ""}"
                            setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small)
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f) // Con peso
                        }

                        val editExerciseButton = ImageButton(requireContext()).apply {
                            setImageResource(R.drawable.baseline_mode_edit_24)
                            setOnClickListener {
                                Log.d(
                                    TAG,
                                    "Botón EDITAR EJERCICIO clickeado para ejercicio ID: $exerciseId"
                                ) // <-- LOG AQUÍ
                                routineId?.let { rId ->
                                    val serializableData =
                                        if (exerciseDataMap.isNotEmpty()) HashMap(exerciseDataMap) else null
                                    val dialog = AddOrEditExerciseDialogFragment.newInstance(
                                        rId,
                                        dayId,
                                        exerciseId,
                                        serializableData
                                    )
                                    dialog.setTargetFragment(this@RoutineDetailFragment, 0)
                                    dialog.show(
                                        parentFragmentManager,
                                        "EditExerciseDialog_$exerciseId"
                                    )
                                } ?: Log.e(
                                    TAG,
                                    "Error: routineId es null al intentar editar ejercicio"
                                )
                            }
                        }

                        val deleteExerciseButton = ImageButton(requireContext()).apply {
                            setImageResource(R.drawable.baseline_delete_24)
                            setBackgroundResource(android.R.color.transparent)
                            contentDescription = "Eliminar $exerciseName"
                            setOnClickListener { showDeleteConfirmationDialog("Ejercicio", exerciseName) { deleteDayExercise(dayId, exerciseId, exerciseName) } }
                            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dpToPx(8) }
                        }

                        exerciseRowLayout.addView(exerciseTextView)
                        exerciseRowLayout.addView(editExerciseButton)
                        exerciseRowLayout.addView(deleteExerciseButton)
                        exercisesSectionLayout.addView(exerciseRowLayout) // Añadir fila de ejercicio
                    }
                }

                // Botón para AÑADIR Ejercicio a ESTE DÍA
                val addExerciseButton = Button(requireContext()).apply {
                    text = "AÑADIR EJERCICIO"
                    setOnClickListener {
                        Log.d(
                            TAG,
                            "Botón AÑADIR EJERCICIO clickeado para día ID: $dayId"
                        ) // <-- LOG AQUÍ
                        routineId?.let { rId ->
                            val dialog =
                                AddOrEditExerciseDialogFragment.newInstance(rId, dayId, null, null)
                            dialog.setTargetFragment(this@RoutineDetailFragment, 0)
                            dialog.show(parentFragmentManager, "AddExerciseDialog_$dayId")
                        } ?: Log.e(TAG, "Error: routineId es null al intentar añadir ejercicio")
                    }
                }
                exercisesSectionLayout.addView(addExerciseButton) // Añadir botón al final de los ejercicios

                dayContainerLayout.addView(exercisesSectionLayout) // Añadir sección de ejercicios al contenedor del día
                // --------------------------------------------------------------------------

                // --- Añadir el Contenedor del Día completo al Layout Principal ---
                binding.layoutDaysContainer.addView(dayContainerLayout)
                // -------------------------------------------------------------

                // --- Divisor entre días (SOLO si no es el último día) ---
                if (dayIndex < daysWithExercises.size - 1) {
                    val divider = View(requireContext()).apply {
                        setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.purple_700)) // Usa un color visible
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1)
                        ).apply { topMargin = dpToPx(12); bottomMargin = dpToPx(12) } // Más margen para el divisor
                    }
                    binding.layoutDaysContainer.addView(divider)
                }
                // -------------------------------------------------------

            } // Fin forEach daysWithExercises
        } // Fin else (daysWithExercises no está vacío)
    }
    // ------------------------------------------------------
    // ------------------------------------------------------

    // --- Función para Eliminar un DÍA y sus EJERCICIOS ---
    private fun deleteRoutineDay(dayId: String, dayName: String) {
        val userId = auth.currentUser?.uid
        if (userId == null || routineId.isNullOrEmpty()) {
            Toast.makeText(context, "Error al eliminar día (datos inválidos)", Toast.LENGTH_SHORT).show()
            return
        }
        showLoading(true)
        Log.d(TAG, "Iniciando eliminación de día ID: $dayId (Nombre: $dayName)")

        val dayDocRef = db.collection("users").document(userId)
            .collection("userRoutines").document(routineId!!)
            .collection("routineDays").document(dayId)

        // Usar Coroutine para borrar subcolección y luego el documento
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Borrar Ejercicios de la subcolección dayExercises
                val exercisesSnapshot = dayDocRef.collection("dayExercises").get().await()
                if (!exercisesSnapshot.isEmpty) {
                    val batch = db.batch()
                    exercisesSnapshot.documents.forEach { batch.delete(it.reference) }
                    batch.commit().await() // Borrar todos los ejercicios en un batch
                    Log.d(TAG, "Ejercicios borrados para día ID: $dayId")
                } else {
                    Log.d(TAG, "No había ejercicios que borrar para día ID: $dayId")
                }


                // 2. Borrar el Documento del Día
                dayDocRef.delete().await()
                Log.d(TAG, "Documento de día borrado ID: $dayId")

                // 3. Actualizar UI en hilo principal
                withContext(Dispatchers.Main) {
                    if (_binding != null) { // Comprobar si la vista aún existe
                        showLoading(false)
                        Toast.makeText(context, "Día '$dayName' eliminado", Toast.LENGTH_SHORT).show()
                        loadRoutineDetails(routineId!!) // Recargar toda la vista
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error durante la eliminación del día $dayId", e)
                withContext(Dispatchers.Main) {
                    if (_binding != null) {
                        showLoading(false)
                        Toast.makeText(context, "Error al eliminar día: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
    // ----------------------------------------------------

    // --- Función para Eliminar un EJERCICIO específico ---
    private fun deleteDayExercise(dayId: String, exerciseId: String, exerciseName: String) {
        val userId = auth.currentUser?.uid
        if (userId == null || routineId.isNullOrEmpty()) {
            Toast.makeText(context, "Error al eliminar ejercicio (datos inválidos)", Toast.LENGTH_SHORT).show()
            return
        }
        showLoading(true)
        Log.d(TAG, "Iniciando eliminación de ejercicio ID: $exerciseId (Nombre: $exerciseName) del día $dayId")

        val exerciseDocRef = db.collection("users").document(userId)
            .collection("userRoutines").document(routineId!!)
            .collection("routineDays").document(dayId)
            .collection("dayExercises").document(exerciseId)

        exerciseDocRef.delete()
            .addOnSuccessListener {
                showLoading(false)
                Log.d(TAG, "Ejercicio $exerciseId eliminado.")
                Toast.makeText(context, "Ejercicio '$exerciseName' eliminado", Toast.LENGTH_SHORT).show()
                loadRoutineDetails(routineId!!) // Recargar toda la vista para reflejar cambio
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e(TAG, "Error al eliminar ejercicio $exerciseId", e)
                Toast.makeText(context, "Error al eliminar ejercicio: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
    // ------------------------------------------------------



    private fun showDeleteConfirmationDialog(itemType: String, itemName: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(requireContext())
            .setTitle("Confirmar Eliminación")
            .setMessage("¿Estás seguro de que quieres eliminar $itemType '$itemName'? Esta acción no se puede deshacer.")
            .setIcon(R.drawable.baseline_warning_24) // Asegúrate de tener este icono
            .setPositiveButton("Eliminar") { dialog, _ ->
                onConfirm() // Ejecuta la acción de borrado pasada como lambda
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null) // null simplemente cierra el diálogo
            .show()
    }


    // Maneja errores de carga (sin cambios)
    private fun handleLoadError(message: String) { /* ... */ }

    // Muestra/oculta ProgressBar (sin cambios)
    private fun showLoading(isLoading: Boolean) { /* ... */ }

    // Helper dp a px (sin cambios)
    // Helper dp a px
    private fun dpToPx(dp: Int): Int { // Declara que devuelve Int
        val density = resources.displayMetrics.density
        return (dp * density).toInt() // <-- AÑADIDO return
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Limpiar binding
    }
}