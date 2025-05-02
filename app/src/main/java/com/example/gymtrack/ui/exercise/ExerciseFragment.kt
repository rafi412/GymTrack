package com.example.gymtrack.ui.exercise // <-- ¡TU PAQUETE!

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
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
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject // Para mapear documentos
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ListenerRegistration
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.StringBuilder

// Modelo intermedio para la lógica de compartir (contiene días con sus ExerciseData)
data class RoutineDayDetail(
    val dayId: String,
    val dayName: String?,
    val exercises: List<ExerciseData>? = null // Lista de tu clase ExerciseData
)

data class SharedRoutine(
    // Usa @SerializedName si quieres que el nombre en JSON sea diferente al de Kotlin
    @SerializedName("routine_name") val routineName: String?,
    @SerializedName("routine_description") val routineDescription: String?,
    @SerializedName("days") val days: List<SharedRoutineDay>?
)

// Representa un día dentro de la rutina compartida
data class SharedRoutineDay(
    @SerializedName("day_name") val dayName: String?,
    // No necesitamos el ID del día ni el orden del día para compartir/importar usualmente
    @SerializedName("exercises") val exercises: List<ExerciseData>? // Reutiliza tu ExerciseData
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
            onShareClick = { routineToShare -> shareRoutineAsJsonFile(routineToShare) } // Iniciar lógica de compartir
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
                val shareText = formatRoutineToJson(routineInfo, fullRoutineDetails)

                // 3. Lanzar el Intent de compartir en el hilo principal
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    launchShareIntent(toString())
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
        val userId = currentUser?.uid ?: run {
            Log.e(TAG, "fetchFullRoutineDetails: Error - currentUser es null.")
            return null // Salir si no hay usuario
        }
        val daysList = mutableListOf<RoutineDayDetail>()
        val routineDaysPath = "users/$userId/userRoutines/$routineId/routineDays" // Ruta base para días

        Log.d(TAG, "fetchFullRoutineDetails: Iniciando para rutina ID $routineId")
        Log.d(TAG, "fetchFullRoutineDetails: Intentando leer días desde ruta: $routineDaysPath")

        try {
            // --- Consulta para obtener los DÍAS ---
            val daysQuery = db.collection(routineDaysPath)
                // Ordenar por el campo 'nombre' del día (asegúrate que existe y es consistente)
                // Si prefieres ordenar por creación, usa 'createdAt' si lo tienes.
                .orderBy("orden", Query.Direction.ASCENDING)

            // Ejecutar la consulta para obtener los documentos de los días
            val daysSnapshot = daysQuery.get().await() // Esperar resultado

            Log.d(TAG, "fetchFullRoutineDetails: Consulta de días completada. Documentos encontrados: ${daysSnapshot.size()}")

            // Verificar si se encontraron documentos de días
            if (daysSnapshot.isEmpty) {
                Log.w(TAG, "fetchFullRoutineDetails: No se encontraron documentos en la subcolección '$routineDaysPath'. Verifica la ruta y los datos en Firestore.")
                // Devolver lista vacía en lugar de null si la rutina existe pero no tiene días
                return daysList
            }

            // --- Iterar sobre cada DÍA encontrado ---
            for (dayDoc in daysSnapshot.documents) {
                val dayId = dayDoc.id
                // Usar el campo "nombre" para el nombre del día. Proporcionar valor por defecto.
                val dayName = dayDoc.getString("nombreDia") ?: "Día (ID: $dayId)" // Usa 'nombre'
                val exercisesList = mutableListOf<ExerciseData>() // Lista para ejercicios de este día
                val dayExercisesPath = "$routineDaysPath/$dayId/dayExercises" // Ruta a ejercicios

                Log.d(TAG, "fetchFullRoutineDetails: Procesando día ID $dayId ('$dayName'). Obteniendo ejercicios desde: $dayExercisesPath")

                try {
                    // --- Consulta para obtener los EJERCICIOS de este día ---
                    val exercisesQuery = db.collection(dayExercisesPath)
                        // Ordenar por el campo 'orden' del ejercicio (asegúrate que existe)
                        .orderBy("orden", Query.Direction.ASCENDING)

                    // Ejecutar la consulta para obtener los documentos de los ejercicios
                    val exercisesSnapshot = exercisesQuery.get().await() // Esperar resultado

                    Log.d(TAG, "fetchFullRoutineDetails: Consulta de ejercicios para día $dayId completada. Documentos encontrados: ${exercisesSnapshot.size()}")

                    // --- Iterar sobre cada EJERCICIO encontrado ---
                    for (exDoc in exercisesSnapshot.documents) {
                        try {
                            // Leer cada campo manualmente usando los nombres EXACTOS de Firestore
                            val name = exDoc.getString("nombre") ?: "Ejercicio sin nombre"
                            val muscleGroup = exDoc.getString("grupoMuscular") // Como en Firestore
                            val series = exDoc.getLong("series")?.toInt() // Leer Long, convertir a Int?
                            val reps = exDoc.getString("repeticiones") // Como en Firestore
                            val rest = exDoc.getString("descanso") // Como en Firestore
                            val notes = exDoc.getString("notas") // Como en Firestore
                            val order = exDoc.getLong("orden")?.toInt() ?: 0 // Como en Firestore

                            // Crear el objeto ExerciseData manualmente
                            val exerciseData = ExerciseData(
                                name = name,
                                muscleGroup = muscleGroup,
                                series = series,
                                reps = reps,
                                rest = rest,
                                notes = notes,
                                order = order
                            )
                            exercisesList.add(exerciseData)
                            Log.v(TAG, "fetchFullRoutineDetails: Ejercicio añadido: ${exerciseData.name} (Orden: ${exerciseData.order})")

                        } catch (e: Exception) {
                            // Capturar error al leer/convertir campos de UN ejercicio
                            Log.e(TAG, "Error al procesar datos del ejercicio ID ${exDoc.id} en día $dayId", e)
                            // Continuar con el siguiente ejercicio si es posible
                        }
                    } // Fin del bucle de ejercicios

                } catch (e: FirebaseFirestoreException) {
                    // Capturar error específico al consultar EJERCICIOS (ej. permisos, índice)
                    Log.e(TAG, "Error de Firestore al obtener ejercicios para día $dayId (Path: $dayExercisesPath)", e)
                    Log.e(TAG, "Código de error: ${e.code}") // El código puede dar pistas
                    // Podrías decidir continuar sin los ejercicios de este día o fallar todo
                    // Por ahora, añadimos el día sin ejercicios si falla la subconsulta
                    exercisesList.clear() // Asegurar que está vacía si hubo error
                } catch (e: Exception) {
                    // Capturar otros errores inesperados al consultar ejercicios
                    Log.e(TAG, "Error inesperado al obtener ejercicios para día $dayId", e)
                    exercisesList.clear()
                }


                // Añadir el día (con su nombre y la lista de ejercicios obtenida) a la lista principal
                daysList.add(
                    RoutineDayDetail(
                        dayId = dayId,
                        dayName = dayName,
                        exercises = exercisesList // Añade la lista (puede estar vacía si hubo error)
                    )
                )
            } // Fin del bucle de días

            Log.d(TAG, "fetchFullRoutineDetails: Finalizado. Total días procesados con detalles: ${daysList.size}")
            return daysList // Devolver la lista completa

        } catch (e: FirebaseFirestoreException) {
            // Capturar error específico al consultar DÍAS (ej. permisos, índice)
            Log.e(TAG, "Error de Firestore en fetchFullRoutineDetails para rutina $routineId (Path: $routineDaysPath)", e)
            Log.e(TAG, "Código de error: ${e.code}") // Códigos: PERMISSION_DENIED, FAILED_PRECONDITION (índice), UNAVAILABLE, etc.
            Toast.makeText(context, "Error al cargar días: ${e.code}", Toast.LENGTH_LONG).show() // Mostrar código al usuario (opcional)
            return null // Devolver null si falla la consulta principal de días
        } catch (e: Exception) {
            // Capturar cualquier otro error inesperado
            Log.e(TAG, "Error inesperado en fetchFullRoutineDetails para rutina $routineId", e)
            return null // Devolver null en caso de error general
        }
    }

    private fun shareRoutineAsJsonFile(routineInfo: RoutineViewData) { // Renombrado para claridad
        Log.d(TAG, "Compartir rutina como ARCHIVO JSON solicitado: ${routineInfo.name} (ID: ${routineInfo.id})")
        if (currentUser == null || context == null) { // Añadir chequeo de context
            Toast.makeText(context ?: activity?.applicationContext, "Error: No se puede compartir ahora.", Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(true)
        CoroutineScope(Dispatchers.IO).launch { // Tareas de red y disco en IO
            var jsonFileUri: Uri? = null // Variable para guardar el Uri del archivo
            var errorMessage: String? = null // Para mensajes de error

            try {
                // 1. Obtener detalles completos
                val fullRoutineDetails: List<RoutineDayDetail>? = fetchFullRoutineDetails(routineInfo.id)

                if (fullRoutineDetails == null) {
                    errorMessage = "No se pudieron cargar los detalles para compartir."
                    return@launch // Salir de la coroutine interna
                }

                // 2. Formatear a JSON
                val jsonStringToShare: String? = formatRoutineToJson(routineInfo, fullRoutineDetails)

                if (jsonStringToShare == null) {
                    errorMessage = "Error al generar formato para compartir."
                    return@launch
                }

                // 3. Guardar JSON en archivo temporal
                jsonFileUri = saveJsonToTempFile(requireContext(), routineInfo.name, jsonStringToShare)

                if (jsonFileUri == null) {
                    errorMessage = "Error al guardar el archivo temporal para compartir."
                    return@launch
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error inesperado durante el proceso de compartir como archivo JSON", e)
                errorMessage = "Error al preparar el archivo para compartir."
            } finally {
                // Volver al hilo principal para actualizar UI (ocultar loading y lanzar Intent o mostrar error)
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    if (jsonFileUri != null) {
                        // 4. Lanzar Intent para compartir el ARCHIVO
                        launchShareFileIntent(jsonFileUri, routineInfo.name)
                    } else {
                        // Mostrar error si algo falló
                        Toast.makeText(context, errorMessage ?: "Error desconocido al compartir.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun saveJsonToTempFile(context: Context, routineName: String?, jsonContent: String): Uri? {
        try {
            // Crear un nombre de archivo seguro (reemplaza caracteres no válidos)
            val safeRoutineName = routineName?.replace(Regex("[^a-zA-Z0-9_]"), "_")?.take(50) ?: "shared_routine"
            val fileName = "${safeRoutineName}.json"

            // Obtener el directorio para archivos caché de rutinas (definido en provider_paths.xml)
            // Usamos cacheDir para archivos temporales que no necesitan persistir mucho tiempo
            val cacheDir = File(context.cacheDir, "routines")
            cacheDir.mkdirs() // Crear el directorio si no existe

            val tempFile = File(cacheDir, fileName)

            // Escribir el contenido JSON al archivo
            FileOutputStream(tempFile).use { fos ->
                fos.write(jsonContent.toByteArray())
            }
            Log.d(TAG, "Archivo JSON temporal guardado en: ${tempFile.absolutePath}")

            // Obtener el Uri para el archivo usando FileProvider
            // ¡¡ASEGÚRATE que la autoridad coincide con AndroidManifest.xml!!
            val authority = "${context.packageName}.fileprovider"
            val fileUri: Uri = FileProvider.getUriForFile(context, authority, tempFile)
            Log.d(TAG, "Uri generado por FileProvider: $fileUri")
            return fileUri

        } catch (e: IOException) {
            Log.e(TAG, "Error al guardar JSON en archivo temporal", e)
            return null
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Error al obtener Uri con FileProvider (¿autoridad mal configurada?)", e)
            return null
        }
    }

    /**
     * Crea y lanza el Intent ACTION_SEND para compartir un archivo usando su Uri.
     * @param fileUri El Uri del archivo a compartir (obtenido de FileProvider).
     * @param routineName El nombre de la rutina (usado para el asunto opcional).
     */
    private fun launchShareFileIntent(fileUri: Uri, routineName: String?) {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            // Poner el Uri del archivo en EXTRA_STREAM
            putExtra(Intent.EXTRA_STREAM, fileUri)
            // Especificar el tipo MIME del archivo JSON
            type = "application/json"
            // Añadir permisos para que la app receptora pueda leer el Uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            // Opcional: Añadir un asunto (útil para emails)
            val subject = "Rutina GymTrack: ${routineName ?: "Compartida"}"
            putExtra(Intent.EXTRA_SUBJECT, subject)

            // Opcional: Añadir un texto corto que acompañe al archivo
            // putExtra(Intent.EXTRA_TEXT, "Aquí tienes la rutina '$routineName' de GymTrack.")
        }

        val shareIntent = Intent.createChooser(sendIntent, "Compartir Archivo de Rutina Vía...")

        try {
            startActivity(shareIntent)
            Log.d(TAG, "Intent de compartir archivo lanzado con éxito para Uri: $fileUri")
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(requireContext(), "No se encontraron aplicaciones para compartir archivos.", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "No se encontró actividad para manejar ACTION_SEND con tipo application/json", e)
        }
    }

    /**
     * Crea un String formateado y legible a partir de la información básica de la rutina
     * y la lista detallada de días y ejercicios.
     * @param routineInfo Información básica de la rutina (nombre, descripción).
     * @param daysDetails Lista de días, cada uno con su lista de ejercicios (ExerciseData).
     * @return El texto formateado listo para compartir.
     */

    private fun formatRoutineToJson(routineInfo: RoutineViewData, daysDetails: List<RoutineDayDetail>): String? {
        Log.d(TAG, "formatRoutineToJson: Iniciando formato JSON para '${routineInfo.name}' con ${daysDetails.size} días.")
        try {
            // 1. Mapear RoutineDayDetail a SharedRoutineDay
            val sharedDays = daysDetails.map { dayDetail ->
                SharedRoutineDay(
                    dayName = dayDetail.dayName, // Usa el nombre del día leído
                    exercises = dayDetail.exercises // Pasa la lista de ExerciseData directamente
                )
            }

            // 2. Crear el objeto SharedRoutine principal
            val sharedRoutineData = SharedRoutine(
                routineName = routineInfo.name,
                routineDescription = routineInfo.description,
                days = sharedDays
            )

            // 3. Convertir el objeto a JSON usando Gson
            // val gson = Gson() // Gson básico, JSON compacto
            val gson = GsonBuilder().setPrettyPrinting().create() // Gson con formato legible (saltos de línea, indentación)

            val jsonString = gson.toJson(sharedRoutineData)
            Log.d(TAG, "formatRoutineToJson: JSON generado:\n$jsonString") // Loguear el JSON (puede ser largo)
            return jsonString

        } catch (e: Exception) {
            Log.e(TAG, "Error al formatear rutina a JSON", e)
            return null // Devolver null si falla la serialización
        }
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