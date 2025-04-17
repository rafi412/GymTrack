package com.example.gymtrack.ui.exercise // O tu paquete

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
import com.example.gymtrack.R
import com.example.gymtrack.databinding.FragmentConfigureExercisesBinding
import com.example.gymtrack.ui.exercise.routine.DayData
import com.example.gymtrack.ui.exercise.routine.ExerciseData
import com.example.gymtrack.ui.exercise.routine.RoutineCreationViewModel
import com.example.gymtrack.ui.exercise.routine.SaveStatus
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

// Implementa la interfaz del diálogo de añadir ejercicio
class ConfigureExercisesFragment : Fragment(), AddExerciseDialogFragment.AddExerciseListener {

    private var _binding: FragmentConfigureExercisesBinding? = null
    private val binding get() = _binding!!

    private val routineViewModel: RoutineCreationViewModel by activityViewModels()
    private lateinit var exerciseAdapter: ExerciseAdapter
    private var currentSelectedDayOrder: Int = 1 // Empezar con el día 1

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private val TAG = "ConfigureExercisesFrag"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConfigureExercisesBinding.inflate(inflater, container, false)
        db = Firebase.firestore
        auth = Firebase.auth
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        setupTabLayout() // Configurar tabs después de que los días estén en el ViewModel
    }

    private fun setupRecyclerView() {
        exerciseAdapter = ExerciseAdapter(
            mutableListOf(),
            onDeleteClick = { exerciseToDelete ->
                // Llamar al ViewModel para eliminar
                routineViewModel.removeExerciseFromDay(currentSelectedDayOrder, exerciseToDelete)
            }
        )
        binding.recyclerViewExercises.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = exerciseAdapter
        }
    }

    private fun setupObservers() {
        // Observar la lista de días para crear las Tabs
        routineViewModel.daysList.observe(viewLifecycleOwner) { days ->
            updateTabLayout(days)
            // Seleccionar la tab correcta si volvemos a este fragmento
            if (days.isNotEmpty() && currentSelectedDayOrder > days.size) {
                currentSelectedDayOrder = 1 // Resetear si el día ya no existe
            }
            selectTab(currentSelectedDayOrder)
            loadExercisesForSelectedDay() // Cargar ejercicios para el día inicial o actual
        }

        // Observar el mapa de ejercicios
        routineViewModel.exercisesPerDay.observe(viewLifecycleOwner) { exercisesMap ->
            // Actualizar la lista del RecyclerView SOLO si el día actualmente seleccionado ha cambiado
            val exercisesForCurrentDay = exercisesMap[currentSelectedDayOrder] ?: emptyList()
            Log.d(TAG, "Actualizando lista para Día $currentSelectedDayOrder: ${exercisesForCurrentDay.size} ejercicios")
            exerciseAdapter.updateData(exercisesForCurrentDay)
            binding.textNoExercises.isVisible = exercisesForCurrentDay.isEmpty()
            binding.recyclerViewExercises.isVisible = exercisesForCurrentDay.isNotEmpty()
        }

        // Observar título de la rutina
        routineViewModel.routineName.observe(viewLifecycleOwner) { name ->
            binding.textRoutineTitleConfigure.text = "Configurar Ejercicios para: ${name ?: "Rutina"}"
        }

        // Observar estado del guardado final
        routineViewModel.saveStatus.observe(viewLifecycleOwner) { status ->
            showLoading(status == SaveStatus.LOADING)
            when (status) {
                SaveStatus.SUCCESS -> {
                    Toast.makeText(context, "Rutina guardada con éxito", Toast.LENGTH_SHORT).show()
                    // Navegar de vuelta al Dashboard (o donde quieras ir)
                    findNavController().navigate(R.id.action_addRoutineDaysFragment_to_navigation_dashboard) // Usa la acción correcta
                    routineViewModel.resetSaveStatus()
                }
                SaveStatus.ERROR -> {
                    val errorMsg = routineViewModel.errorMessage.value ?: "Error desconocido al guardar"
                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                    routineViewModel.resetSaveStatus()
                }
                else -> {} // IDLE o LOADING
            }
        }
    }

    private fun setupClickListeners() {
        binding.buttonAddExercise.setOnClickListener {
            // Abrir diálogo para añadir ejercicio AL DÍA ACTUALMENTE SELECCIONADO
            val dialog = AddExerciseDialogFragment.newInstance(currentSelectedDayOrder)
            dialog.show(childFragmentManager, "AddExerciseDialog")
        }

        binding.buttonSaveRoutineFinalConfigure.setOnClickListener {
            val userId = auth.currentUser?.uid
            if (userId != null) {
                // Llamar al ViewModel para guardar todo
                routineViewModel.saveRoutineAndDaysToFirestore(userId, db)
            } else {
                Toast.makeText(context, "Error: Usuario no identificado.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupTabLayout() {
        binding.tabLayoutDays.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val selectedDay = tab?.tag as? DayData
                if (selectedDay != null) {
                    currentSelectedDayOrder = selectedDay.order
                    Log.d(TAG, "Tab seleccionada: Día ${selectedDay.order} - ${selectedDay.name}")
                    loadExercisesForSelectedDay()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        // Cargar tabs iniciales (se llamará de nuevo desde el observer)
        updateTabLayout(routineViewModel.daysList.value ?: emptyList())
    }

    private fun updateTabLayout(days: List<DayData>) {
        binding.tabLayoutDays.removeAllTabs()
        if (days.isEmpty()){
            binding.buttonAddExercise.isEnabled = false // No se pueden añadir ejercicios si no hay días
        } else {
            binding.buttonAddExercise.isEnabled = true
        }
        days.forEach { day ->
            binding.tabLayoutDays.addTab(
                binding.tabLayoutDays.newTab().apply {
                    text = "Día ${day.order}" // O day.name si prefieres
                    tag = day // Guardar el objeto DayData en la tag de la tab
                }
            )
        }
        // Asegurarse que la tab correcta está seleccionada visualmente
        selectTab(currentSelectedDayOrder)
    }

    private fun selectTab(dayOrderToSelect: Int) {
        val tabIndex = routineViewModel.daysList.value?.indexOfFirst { it.order == dayOrderToSelect } ?: -1
        if (tabIndex != -1 && tabIndex < binding.tabLayoutDays.tabCount) {
            binding.tabLayoutDays.getTabAt(tabIndex)?.select()
        } else if (binding.tabLayoutDays.tabCount > 0) {
            // Si el día no se encuentra (ej. eliminado), seleccionar la primera tab
            binding.tabLayoutDays.getTabAt(0)?.select()
            currentSelectedDayOrder = routineViewModel.daysList.value?.firstOrNull()?.order ?: 1
        }
    }


    private fun loadExercisesForSelectedDay() {
        // Forzar actualización del adapter leyendo del ViewModel
        val exercisesForCurrentDay = routineViewModel.exercisesPerDay.value?.get(currentSelectedDayOrder) ?: emptyList()
        Log.d(TAG, "Cargando ejercicios para Día $currentSelectedDayOrder: ${exercisesForCurrentDay.size} ejercicios")
        exerciseAdapter.updateData(exercisesForCurrentDay)
        binding.textNoExercises.isVisible = exercisesForCurrentDay.isEmpty()
        binding.recyclerViewExercises.isVisible = exercisesForCurrentDay.isNotEmpty()
    }

    // Implementación de la interfaz de AddExerciseDialog
    override fun onExerciseAdded(dayOrder: Int, exercise: ExerciseData) {
        Log.d(TAG, "Ejercicio recibido del diálogo para día $dayOrder: ${exercise.name}")
        // El ViewModel ya se actualizó (si el diálogo lo hizo),
        // el observer de exercisesPerDay debería actualizar la UI.
        // Si el diálogo NO actualiza el VM, hazlo aquí:
        // routineViewModel.addExerciseToDay(dayOrder, exercise)
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBarSaveRoutineConfigure.isVisible = isLoading
        binding.buttonAddExercise.isEnabled = !isLoading
        binding.buttonSaveRoutineFinalConfigure.isEnabled = !isLoading
        // Podrías deshabilitar más cosas si es necesario
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}