package com.example.gymtrack.ui.exercise // <-- ¡TU PAQUETE!

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.gymtrack.R // <-- ¡TU PAQUETE R!
import com.example.gymtrack.databinding.FragmentExerciseBinding // <-- ¡TU BINDING!
// --- Importa tus clases y las de rutina/ejercicio ---
import com.example.gymtrack.ui.exercise.routine.RoutineAdapter // Adapter para lista de rutinas
import com.example.gymtrack.ui.exercise.routine.RoutineCreationViewModel
import com.example.gymtrack.ui.exercise.routine.RoutineViewData // Para la lista
import com.example.gymtrack.ui.exercise.routine.ExerciseData
// ----------------------------------------------------
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject // Para mapear documentos
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.lang.StringBuilder

// Modelo intermedio para la lógica de compartir (contiene días con sus ExerciseData)
data class RoutineDayDetail(
    val dayId: String,
    val dayName: String?,
    val exercises: List<ExerciseData>? = null // Lista de tu clase ExerciseData
)

class ExerciseFragment : Fragment() {

    private var _binding: FragmentExerciseBinding? = null
    private val binding get() = _binding!!

    // Firebase
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var currentUser: FirebaseUser? = null

    // RecyclerView y Adapter para la LISTA DE RUTINAS
    private lateinit var routineAdapter: RoutineAdapter // Usa RoutineAdapter

    // Listener de Firestore para la lista de rutinas
    private var routinesListener: ListenerRegistration? = null

    private val TAG = "EserciseFragment" // Tag para logs

    // ViewModel (útil para limpiar datos si vienes de crear/editar)
    private val routineCreationViewModel: RoutineCreationViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExerciseBinding.inflate(inflater, container, false)
        db = Firebase.firestore
        auth = Firebase.auth
        currentUser = auth.currentUser

        // --- Inicializar RoutineAdapter (para la lista de rutinas) ---
        routineAdapter = RoutineAdapter(
            mutableListOf(), // Lista inicial vacía
            onItemClick = { clickedRoutine -> handleRoutineClick(clickedRoutine) }, // Navegar a detalles
            onDeleteClick = { routineToDelete -> showDeleteConfirmationDialog(routineToDelete) }, // Mostrar diálogo borrado
            onShareClick = { routineToShare -> shareRoutineAsText(routineToShare) } // Iniciar lógica de compartir
        )
        // -----------------------------------------------------------
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Verificar usuario logueado
        if (currentUser == null) {
            handleUserNotLoggedIn()
            return // No continuar si no hay usuario
        }
        // Limpiar datos de VM si aplica
        routineCreationViewModel.clearData()
        // Configurar UI
        setupRecyclerView()
        setupFab()
        // Iniciar carga de datos y escucha de cambios
        setupFirestoreListener()
    }

    /** Configura el RecyclerView principal para mostrar la lista de rutinas. */
    private fun setupRecyclerView() {
        binding.recyclerViewRoutines.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = routineAdapter // Asigna el adapter de rutinas
        }
    }

    /** Configura el FloatingActionButton para navegar a la pantalla de creación. */
    private fun setupFab() {
        binding.fabAddRoutine.setOnClickListener {
            // Navega a la pantalla donde se introducen los detalles iniciales de la rutina
            findNavController().navigate(R.id.action_navigation_dashboard_to_createRoutineDetailsFragment) // Ajusta ID acción
        }
    }

    /** Navega a la pantalla de detalle de la rutina al hacer clic en un item. */
    private fun handleRoutineClick(routine: RoutineViewData) {
        Log.d(TAG, "Rutina clickeada: ${routine.name} (ID: ${routine.id})")
        // Prepara el argumento (ID de la rutina) para pasar al fragmento de detalle
        val bundle = Bundle().apply {
            putString(
                "routineId",
                routine.id
            ) // La clave debe coincidir con la esperada por el destino
        }
        // Navega usando la acción definida en el nav_graph.xml
        findNavController().navigate(
            R.id.action_navigation_dashboard_to_routineDetailFragment,
            bundle
        ) // Ajusta ID acción
    }

    // --- INICIO: Lógica de Compartir Rutina como Texto ---

    /**
     * Orquesta el proceso de compartir: obtiene detalles completos y lanza el Intent.
     * Se llama cuando se pulsa el botón de compartir en un item de la lista.
     */
    private fun shareRoutineAsText(routineInfo: RoutineViewData) {
        Log.d(TAG, "Compartir rutina solicitado: ${routineInfo.name} (ID: ${routineInfo.id})")
        if (currentUser == null) {
            Toast.makeText(context, "Error: Usuario no válido", Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(true) // Mostrar indicador de progreso
        CoroutineScope(Dispatchers.IO).launch { // Tarea en hilo de fondo
            try {
                // 1. Obtener detalles completos (días y ejercicios)
                val fullRoutineDetails: List<RoutineDayDetail>? =
                    fetchFullRoutineDetails(routineInfo.id)

                // Verificar si la carga fue exitosa
                if (fullRoutineDetails == null) {
                    withContext(Dispatchers.Main) {
                        showLoading(false)
                        Toast.makeText(
                            context,
                            "No se pudieron cargar los detalles para compartir.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch // Salir si no hay detalles
                }

                // 2. Formatear el texto a compartir
                val shareText = formatRoutineForSharing(routineInfo, fullRoutineDetails)

                // 3. Lanzar el Intent de compartir en el hilo principal
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    launchShareIntent(shareText)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error inesperado durante el proceso de compartir", e)
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Toast.makeText(context, "Error al preparar para compartir.", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    /**
     * Obtiene de Firestore los días y la lista de ejercicios (ExerciseData) para una rutina específica.
     * Es una función suspendida, debe llamarse desde una Coroutine.
     * @param routineId El ID de la rutina cuyos detalles se quieren obtener.
     * @return Lista de RoutineDayDetail (días con sus ejercicios) o null si hay error.
     */
    private suspend fun fetchFullRoutineDetails(routineId: String): List<RoutineDayDetail>? {
        val userId = currentUser?.uid ?: return null
        val daysList = mutableListOf<RoutineDayDetail>()
        Log.d(TAG, "fetchFullRoutineDetails: Iniciando para rutina ID $routineId")

        try {
            // Obtener documentos de días ordenados
            val daysSnapshot = db.collection("users").document(userId)
                .collection("userRoutines").document(routineId)
                .collection("routineDays")
                .orderBy("order", Query.Direction.ASCENDING) // ¡Necesitas campo 'order'!
                .get().await()

            Log.d(TAG, "fetchFullRoutineDetails: ${daysSnapshot.size()} días encontrados.")

            // Iterar sobre cada día
            for (dayDoc in daysSnapshot.documents) {
                val dayId = dayDoc.id
                // Usa el campo "nombre" para el nombre del día
                val dayName = dayDoc.getString("nombre") ?: "Día ${dayDoc.getLong("order") ?: ""}"
                val exercisesList = mutableListOf<ExerciseData>()

                Log.d(
                    TAG,
                    "fetchFullRoutineDetails: Obteniendo ejercicios para día ID $dayId ($dayName)"
                )

                // Obtener documentos de ejercicios para este día, ordenados
                val exercisesSnapshot = dayDoc.reference.collection("dayExercises")
                    .orderBy("order", Query.Direction.ASCENDING) // ¡Necesitas campo 'order'!
                    .get().await()

                Log.d(
                    TAG,
                    "fetchFullRoutineDetails: ${exercisesSnapshot.size()} ejercicios encontrados para día $dayId."
                )

                // Mapear cada documento de ejercicio a ExerciseData
                for (exDoc in exercisesSnapshot.documents) {
                    // ¡IMPORTANTE! Asegúrate que los nombres de campo en Firestore
                    // coincidan con las propiedades de ExerciseData (name, muscleGroup, series, etc.)
                    val exerciseData = exDoc.toObject<ExerciseData>()
                    if (exerciseData != null) {
                        exercisesList.add(exerciseData)
                        Log.v(
                            TAG,
                            "fetchFullRoutineDetails: Ejercicio mapeado: ${exerciseData.name}"
                        )
                    } else {
                        Log.w(
                            TAG,
                            "fetchFullRoutineDetails: No se pudo mapear ejercicio ID ${exDoc.id} a ExerciseData."
                        )
                    }
                }
                // Añadir el día (con su nombre y lista de ExerciseData) a la lista resultado
                daysList.add(
                    RoutineDayDetail(
                        dayId = dayId,
                        dayName = dayName,
                        exercises = exercisesList
                    )
                )
            }
            Log.d(
                TAG,
                "fetchFullRoutineDetails: Finalizado. Total días con detalles: ${daysList.size}"
            )
            return daysList
        } catch (e: Exception) {
            Log.e(TAG, "Error en fetchFullRoutineDetails para rutina $routineId", e)
            return null // Devolver null en caso de error
        }
    }

    /**
     * Crea un String formateado y legible a partir de la información básica de la rutina
     * y la lista detallada de días y ejercicios.
     * @param routineInfo Información básica de la rutina (nombre, descripción).
     * @param daysDetails Lista de días, cada uno con su lista de ejercicios (ExerciseData).
     * @return El texto formateado listo para compartir.
     */
    private fun formatRoutineForSharing(
        routineInfo: RoutineViewData,
        daysDetails: List<RoutineDayDetail>
    ): String {
        val builder = StringBuilder()

        // Encabezado con nombre y descripción
        builder.append("¡Échale un vistazo a mi rutina '${routineInfo.name}' de GymTrack!\n\n")
        if (!routineInfo.description.isNullOrBlank()) { // Usar isNullOrBlank
            builder.append("Descripción: ${routineInfo.description}\n\n")
        }

        // Contenido: Días y Ejercicios
        if (daysDetails.isEmpty()) {
            builder.append("Esta rutina aún no tiene días ni ejercicios definidos.")
        } else {
            daysDetails.forEach { day -> // day es RoutineDayDetail
                builder.append("--- ${day.dayName ?: "Día"} ---\n") // Nombre del día
                if (day.exercises.isNullOrEmpty()) {
                    builder.append("(Sin ejercicios para este día)\n")
                } else {
                    day.exercises.forEachIndexed { index, exercise -> // exercise es ExerciseData
                        builder.append("${index + 1}. ${exercise.name}\n") // Nombre ejercicio
                        // Añadir detalles del ejercicio desde ExerciseData
                        val sets = exercise.series ?: "?"
                        val reps = exercise.reps ?: "?"
                        val rest = exercise.rest ?: "-"
                        builder.append("   - Series: $sets\n")
                        builder.append("   - Repeticiones: $reps\n")
                        if (rest != "-") builder.append("   - Descanso: $rest\n")
                        if (!exercise.notes.isNullOrBlank()) builder.append("   - Notas: ${exercise.notes}\n")
                    }
                }
                builder.append("\n") // Espacio entre días
            }
        }

        // Pie de página
        builder.append("\n¡Generado por GymTrack! 💪") // Tu firma :)

        return builder.toString()
    }

    /**
     * Crea y lanza el Intent estándar ACTION_SEND para compartir texto.
     * @param shareText El texto formateado de la rutina a compartir.
     */
    private fun launchShareIntent(shareText: String) {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Compartir Rutina Vía...")
        try {
            startActivity(shareIntent)
            Log.d(TAG, "Intent de compartir lanzado con éxito.")
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(
                requireContext(),
                "No se encontraron aplicaciones para compartir.",
                Toast.LENGTH_SHORT
            ).show()
            Log.e(TAG, "No se encontró actividad para manejar ACTION_SEND", e)
        }
    }

    // --- FIN: Lógica de Compartir Rutina como Texto ---


    // --- INICIO: Lógica de Borrado (con diálogo) ---

    /** Muestra un diálogo de confirmación antes de borrar una rutina. */
    private fun showDeleteConfirmationDialog(routineToDelete: RoutineViewData) {
        AlertDialog.Builder(requireContext())
            .setTitle("Confirmar Eliminación")
            .setMessage("¿Seguro que quieres eliminar la rutina '${routineToDelete.name}'? Se borrarán todos sus días y ejercicios.")
            .setIcon(R.drawable.baseline_warning_24) // Icono de advertencia
            .setPositiveButton("Eliminar") { dialog, _ ->
                deleteRoutineFromFirestore(routineToDelete) // Llama a la función de borrado
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null) // Simplemente cierra el diálogo
            .show()
    }

    /**
     * Elimina la rutina y todas sus subcolecciones (días y ejercicios) de Firestore.
     * Utiliza Coroutines y Write Batches para eficiencia.
     */
    private fun deleteRoutineFromFirestore(routineToDelete: RoutineViewData) {
        val userId = currentUser?.uid
        if (userId == null) {
            Toast.makeText(context, "Error: Usuario no válido", Toast.LENGTH_SHORT).show()
            return
        }
        showLoading(true) // Mostrar progreso durante el borrado
        Log.d(TAG, "Iniciando eliminación de rutina ID: ${routineToDelete.id}")

        // Referencia al documento principal de la rutina
        val routineDocRef = db.collection("users").document(userId)
            .collection("userRoutines").document(routineToDelete.id)

        // Usar Coroutine para operaciones de Firestore en segundo plano
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Borrar Ejercicios anidados (dentro de cada día)
                val daysSnapshot = routineDocRef.collection("routineDays").get().await()
                for (dayDoc in daysSnapshot.documents) {
                    val exercisesSnapshot =
                        dayDoc.reference.collection("dayExercises").get().await()
                    // Usar batch para borrar eficientemente (hasta 500 operaciones)
                    var exerciseBatch = db.batch()
                    var exerciseCountInBatch = 0
                    for (exerciseDoc in exercisesSnapshot.documents) {
                        exerciseBatch.delete(exerciseDoc.reference)
                        exerciseCountInBatch++
                        // Si el batch está casi lleno, ejecutarlo y crear uno nuevo
                        if (exerciseCountInBatch >= 499) {
                            exerciseBatch.commit().await()
                            exerciseBatch = db.batch()
                            exerciseCountInBatch = 0
                        }
                    }
                    // Ejecutar el último batch si contiene operaciones pendientes
                    if (exerciseCountInBatch > 0) exerciseBatch.commit().await()
                    Log.d(TAG, "Ejercicios borrados para día ID: ${dayDoc.id}")
                }

                // 2. Borrar Días (usando batch)
                var dayBatch = db.batch()
                var dayCountInBatch = 0
                // Reutilizar daysSnapshot para obtener las referencias de los días
                for (dayDoc in daysSnapshot.documents) {
                    dayBatch.delete(dayDoc.reference)
                    dayCountInBatch++
                    if (dayCountInBatch >= 499) {
                        dayBatch.commit().await()
                        dayBatch = db.batch()
                        dayCountInBatch = 0
                    }
                }
                // Ejecutar el último batch de días
                if (dayCountInBatch > 0) dayBatch.commit().await()
                Log.d(TAG, "Días borrados para rutina ID: ${routineToDelete.id}")

                // 3. Borrar el Documento Principal de la Rutina
                routineDocRef.delete().await()
                Log.d(TAG, "Documento de rutina borrado ID: ${routineToDelete.id}")

                // 4. Actualizar UI en hilo principal (solo mensaje, el listener hace el resto)
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Toast.makeText(
                        context,
                        "Rutina '${routineToDelete.name}' eliminada",
                        Toast.LENGTH_SHORT
                    ).show()
                    // No es necesario remover del adapter manualmente, el listener lo hará
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error durante la eliminación recursiva de la rutina", e)
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Toast.makeText(context, "Error al eliminar: ${e.message}", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }

    // --- FIN: Lógica de Borrado ---


    // --- Listener de Firestore para la lista de Rutinas ---
    private fun setupFirestoreListener() {
        val userId = currentUser?.uid
        if (userId == null) {
            Log.e(TAG, "No se puede iniciar listener: usuario null")
            handleUserNotLoggedIn()
            return
        }
        showLoading(true) // Mostrar progreso inicial
        Log.d(TAG, "Iniciando listener para rutinas de userId: $userId")

        // Consulta para obtener las rutinas del usuario, ordenadas
        val query = db.collection("users").document(userId)
            .collection("userRoutines")
            // Asegúrate de tener este campo o usa otro para ordenar (ej. "nombre")
            .orderBy("createdAt", Query.Direction.DESCENDING)

        // Remover listener anterior para evitar duplicados si onViewCreated se llama de nuevo
        routinesListener?.remove()

        // Adjuntar el listener que se actualiza en tiempo real
        routinesListener = query.addSnapshotListener { snapshots, e ->
            showLoading(false) // Ocultar progreso al recibir datos o error
            if (e != null) {
                // Manejar error del listener
                Log.e(TAG, "Error en listener de rutinas", e)
                binding.textNoRoutines.text = "Error al cargar rutinas."
                binding.textNoRoutines.isVisible = true
                binding.recyclerViewRoutines.isVisible = false
                Toast.makeText(
                    context,
                    "Error al escuchar rutinas: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                return@addSnapshotListener // Salir del listener
            }

            val updatedRoutinesList = mutableListOf<RoutineViewData>()
            // Procesar los documentos recibidos
            if (snapshots == null || snapshots.isEmpty) {
                Log.d(TAG, "Listener: No se encontraron rutinas.")
                binding.textNoRoutines.text =
                    "No tienes rutinas creadas.\n¡Pulsa '+' para añadir!" // Mensaje claro
                binding.textNoRoutines.isVisible = true
                binding.recyclerViewRoutines.isVisible = false
            } else {
                Log.d(TAG, "Listener: ${snapshots.size()} rutinas encontradas.")
                binding.textNoRoutines.isVisible = false
                binding.recyclerViewRoutines.isVisible = true
                // Mapear cada documento a RoutineViewData para la lista
                for (document in snapshots.documents) {
                    val routine = RoutineViewData(
                        id = document.id,
                        name = document.getString("nombre")
                            ?: "Sin Nombre", // Usa tu campo de nombre
                        description = document.getString("descripcion") // Usa tu campo de descripción
                    )
                    updatedRoutinesList.add(routine)
                }
            }
            // Actualizar el adapter con la nueva lista
            Log.d(TAG, "Actualizando adapter con ${updatedRoutinesList.size} rutinas.")
            routineAdapter.updateData(updatedRoutinesList)
        }
        Log.d(TAG, "SnapshotListener de rutinas adjuntado.")
    }
    // --- Fin Listener ---

    /** Maneja el caso donde el usuario no está logueado al entrar al fragmento. */
    private fun handleUserNotLoggedIn() {
        Toast.makeText(context, "Error: Debes iniciar sesión.", Toast.LENGTH_LONG).show()
        Log.e(TAG, "Current user es null en onViewCreated o llamada posterior.")
        // Aquí podrías navegar a la pantalla de login si es necesario
        // findNavController().navigate(R.id.action_global_to_loginFragment) // Ejemplo
    }

    /** Muestra u oculta el ProgressBar principal. */
    private fun showLoading(isLoading: Boolean) {
        binding.progressBarRoutinesList.isVisible = isLoading
        // Opcional: Ocultar otros elementos mientras carga
        if (isLoading) {
            binding.recyclerViewRoutines.visibility =
                View.INVISIBLE // Ocultar pero mantener espacio
            binding.textNoRoutines.visibility = View.GONE
        } else {
            binding.recyclerViewRoutines.visibility =
                View.VISIBLE // Asegurar visibilidad si no está vacío
        }
        // La visibilidad final de recyclerViewRoutines/textNoRoutines la decide el listener
    }

    /** Limpia recursos, especialmente el listener de Firestore. */
    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView: Removiendo listener de Firestore.")
        routinesListener?.remove() // ¡Muy importante para evitar fugas de memoria!
        routinesListener = null
        _binding = null // Liberar la referencia al binding
    }
}