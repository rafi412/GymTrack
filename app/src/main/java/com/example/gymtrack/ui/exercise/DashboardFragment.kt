package com.example.gymtrack.ui.exercise

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels // Mantenemos ViewModel
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.gymtrack.R // Importar R
import com.example.gymtrack.databinding.FragmentDashboardBinding
import com.example.gymtrack.ui.exercise.routine.RoutineAdapter
import com.example.gymtrack.ui.exercise.routine.RoutineCreationViewModel
import com.example.gymtrack.ui.exercise.routine.RoutineViewData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

// Ya no implementa RoutineCreationListener si la lógica de creación se movió
class DashboardFragment : Fragment() { // Quitada la implementación de Listener si ya no se usa aquí

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    // Firebase
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var currentUser: FirebaseUser? = null

    // RecyclerView
    private lateinit var routineAdapter: RoutineAdapter
    // private val routinesList = mutableListOf<RoutineViewData>() // El listener llenará el adapter

    // Listener de Firestore
    private var routinesListener: ListenerRegistration? = null

    private val TAG = "DashboardFragmentList"

    // ViewModel (aún útil para limpiar datos de creación)
    private val routineCreationViewModel: RoutineCreationViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        db = Firebase.firestore
        auth = Firebase.auth
        currentUser = auth.currentUser
        // --- Inicializar Adapter aquí ---
        routineAdapter = RoutineAdapter(
            mutableListOf(), // Empezar vacío
            onItemClick = { clickedRoutine -> handleRoutineClick(clickedRoutine) },
            onDeleteClick = { routineToDelete -> showDeleteConfirmationDialog(routineToDelete) } // Pasar lambda de borrado
        )
        // ------------------------------
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (currentUser == null) { handleUserNotLoggedIn(); return }
        routineCreationViewModel.clearData()
        setupRecyclerView() // Asigna el adapter creado en onCreateView
        setupFab()
        setupFirestoreListener()
    }

    private fun setupRecyclerView() {
        binding.recyclerViewRoutines.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = routineAdapter
        }
    }

    private fun showDeleteConfirmationDialog(routineToDelete: RoutineViewData) {
        AlertDialog.Builder(requireContext())
            .setTitle("Confirmar Eliminación")
            .setMessage("¿Estás seguro de que quieres eliminar la rutina '${routineToDelete.name}'? Esta acción no se puede deshacer y borrará todos sus días y ejercicios.")
            .setIcon(R.drawable.baseline_warning_24) // Necesitas icono ic_warning (Vector Asset)
            .setPositiveButton("Eliminar") { dialog, _ ->
                deleteRoutineFromFirestore(routineToDelete)
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    // -----------------------------

    // --- Función para Eliminar Rutina y Subcolecciones ---
    private fun deleteRoutineFromFirestore(routineToDelete: RoutineViewData) {
        val userId = currentUser?.uid
        if (userId == null) {
            Toast.makeText(context, "Error: Usuario no válido", Toast.LENGTH_SHORT).show()
            return
        }
        showLoading(true) // Mostrar progreso
        Log.d(TAG, "Iniciando eliminación de rutina ID: ${routineToDelete.id}")

        val routineDocRef = db.collection("users").document(userId)
            .collection("userRoutines").document(routineToDelete.id)

        // Usar Coroutine para manejar borrado asíncrono de subcolecciones
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Borrar Ejercicios (Iterar sobre días)
                val daysSnapshot = routineDocRef.collection("routineDays").get().await()
                for (dayDoc in daysSnapshot.documents) {
                    val exercisesSnapshot = dayDoc.reference.collection("dayExercises").get().await()
                    // Usar batch para borrar ejercicios de un día (limitado a 500 operaciones por batch)
                    var exerciseBatch = db.batch()
                    var exerciseCountInBatch = 0
                    for (exerciseDoc in exercisesSnapshot.documents) {
                        exerciseBatch.delete(exerciseDoc.reference)
                        exerciseCountInBatch++
                        if (exerciseCountInBatch == 499) { // Límite cercano a 500
                            exerciseBatch.commit().await()
                            exerciseBatch = db.batch() // Nuevo batch
                            exerciseCountInBatch = 0
                        }
                    }
                    if (exerciseCountInBatch > 0) exerciseBatch.commit().await() // Commit del último batch de ejercicios
                    Log.d(TAG, "Ejercicios borrados para día ID: ${dayDoc.id}")
                }

                // 2. Borrar Días (Iterar y borrar, o usar batch si son pocos)
                // Usar batch para borrar días (limitado)
                var dayBatch = db.batch()
                var dayCountInBatch = 0
                for (dayDoc in daysSnapshot.documents) {
                    dayBatch.delete(dayDoc.reference)
                    dayCountInBatch++
                    if (dayCountInBatch == 499) {
                        dayBatch.commit().await()
                        dayBatch = db.batch()
                        dayCountInBatch = 0
                    }
                }
                if (dayCountInBatch > 0) dayBatch.commit().await() // Commit del último batch de días
                Log.d(TAG, "Días borrados para rutina ID: ${routineToDelete.id}")


                // 3. Borrar el Documento de la Rutina Principal
                routineDocRef.delete().await()
                Log.d(TAG, "Documento de rutina borrado ID: ${routineToDelete.id}")

                // 4. Actualizar UI en hilo principal (si NO usas listener)
                // Si usas listener, Firestore notificará el cambio y la UI se actualizará sola.
                // Si NO usas listener, necesitas actualizar la UI manualmente:
                /*
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Toast.makeText(context, "Rutina '${routineToDelete.name}' eliminada", Toast.LENGTH_SHORT).show()
                    // Remover visualmente del adapter (si no usas listener)
                    // routineAdapter.removeItem(routineToDelete)
                    // loadUserRoutines() // O recargar todo
                }
                 */
                // Con el listener, solo necesitamos ocultar el loading y mostrar mensaje
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Toast.makeText(context, "Rutina '${routineToDelete.name}' eliminada", Toast.LENGTH_SHORT).show()
                    // El listener se encargará de actualizar la lista
                }


            } catch (e: Exception) {
                Log.e(TAG, "Error durante la eliminación recursiva", e)
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Toast.makeText(context, "Error al eliminar: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupFab() {
        binding.fabAddRoutine.setOnClickListener {
            // Navegar usando el ID de la acción directamente
            findNavController().navigate(R.id.action_navigation_dashboard_to_createRoutineDetailsFragment)
        }
    }

    // --- Función para manejar el clic en una rutina (SIN Safe Args) ---
    private fun handleRoutineClick(routine: RoutineViewData) {
        Log.d(TAG, "Rutina clickeada: ${routine.name} (ID: ${routine.id})")

        // 1. Crear el Bundle manualmente
        val bundle = Bundle().apply {
            putString("routineId", routine.id) // Usar clave String "routineId"
        }

        // 2. Navegar usando el ID de la acción y el Bundle
        findNavController().navigate(R.id.action_navigation_dashboard_to_routineDetailFragment, bundle)
    }
    // ---------------------------------------------------------------

    private fun setupFirestoreListener() {
        val userId = currentUser?.uid
        if (userId == null) {
            Log.e(TAG, "No se puede iniciar listener: usuario null")
            handleUserNotLoggedIn()
            return
        }
        showLoading(true)
        Log.d(TAG, "Iniciando listener para userId: $userId")

        val query = db.collection("users").document(userId).collection("userRoutines")
            .orderBy("createdAt", Query.Direction.DESCENDING)

        routinesListener?.remove() // Detener listener anterior

        routinesListener = query.addSnapshotListener { snapshots, e ->
            showLoading(false)
            if (e != null) {
                Log.e(TAG, "Error en listener de rutinas", e)
                binding.textNoRoutines.text = "Error al cargar rutinas."
                binding.textNoRoutines.isVisible = true
                binding.recyclerViewRoutines.isVisible = false
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                return@addSnapshotListener
            }

            val updatedRoutinesList = mutableListOf<RoutineViewData>()
            if (snapshots == null || snapshots.isEmpty) {
                Log.d(TAG, "Listener: No se encontraron rutinas.")
                binding.textNoRoutines.text = "No tienes rutinas creadas."
                binding.textNoRoutines.isVisible = true
                binding.recyclerViewRoutines.isVisible = false
            } else {
                Log.d(TAG, "Listener: ${snapshots.size()} rutinas encontradas.")
                binding.textNoRoutines.isVisible = false
                binding.recyclerViewRoutines.isVisible = true
                for (document in snapshots.documents) {
                    val routine = RoutineViewData(
                        id = document.id,
                        name = document.getString("nombre") ?: "Sin Nombre",
                        description = document.getString("descripcion")
                    )
                    updatedRoutinesList.add(routine)
                }
            }
            Log.d(TAG, "Actualizando adapter con ${updatedRoutinesList.size} rutinas.")
            routineAdapter.updateData(updatedRoutinesList)
        }
        Log.d(TAG, "SnapshotListener adjuntado.")
    }

    // Ya no necesita implementar onRoutineCreatedOrUpdated si la lógica está en los otros fragmentos
    // override fun onRoutineCreatedOrUpdated(routineId: String) { ... }

    private fun handleUserNotLoggedIn() {
        Toast.makeText(context, "Error: Usuario no identificado.", Toast.LENGTH_LONG).show()
        Log.e(TAG, "Current user es null")
        // Manejar apropiadamente
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBarRoutinesList.isVisible = isLoading
        if (isLoading) {
            binding.recyclerViewRoutines.isVisible = false
            binding.textNoRoutines.isVisible = false
        }
        // La visibilidad final se maneja en el listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView: Removiendo listener de Firestore.")
        routinesListener?.remove()
        routinesListener = null
        _binding = null
    }
}