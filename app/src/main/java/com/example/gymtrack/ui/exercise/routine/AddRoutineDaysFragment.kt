package com.example.gymtrack.ui.exercise.routine // O tu paquete

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels // O navGraphViewModels
import androidx.navigation.fragment.findNavController
import com.example.gymtrack.R // Asegúrate de importar tu R
import com.example.gymtrack.databinding.FragmentAddRoutineDaysBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class AddRoutineDaysFragment : Fragment() {

    private var _binding: FragmentAddRoutineDaysBinding? = null
    private val binding get() = _binding!!

    // ViewModel compartido
    private val routineViewModel: RoutineCreationViewModel by activityViewModels()

    // Firebase
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddRoutineDaysBinding.inflate(inflater, container, false)
        db = Firebase.firestore
        auth = Firebase.auth
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textAddedDaysList.movementMethod = ScrollingMovementMethod() // Para scroll

        setupObservers()
        setupClickListeners() // Configura los listeners aquí
    }

    private fun setupObservers() {
        // Mostrar nombre de la rutina
        routineViewModel.routineName.observe(viewLifecycleOwner) { name ->
            binding.textRoutineTitleAddDays.text = "Añadir Días a: ${name ?: "Rutina sin nombre"}"
        }

        // Mostrar lista de días añadidos
        routineViewModel.daysList.observe(viewLifecycleOwner) { days ->
            if (days.isNullOrEmpty()) {
                binding.textAddedDaysList.text = "No se han añadido días."
                binding.inputLayoutDayNameAdd.hint = "Nombre del Día 1"
            } else {
                binding.textAddedDaysList.text =
                    days.joinToString("\n") { "Día ${it.order}: ${it.name}" }
                binding.inputLayoutDayNameAdd.hint = "Nombre del Día ${days.size + 1}"
            }
            // Habilitar botón "Siguiente" solo si hay días
            binding.buttonSaveRoutineFinal.isEnabled =
                days.isNotEmpty() // Cambiado a buttonSaveRoutineFinal
        }

        // --- ELIMINADO: Observador de saveStatus de aquí ---
        // La lógica de guardado final ya no está en este fragmento
        // routineViewModel.saveStatus.observe(viewLifecycleOwner) { ... }
        // --------------------------------------------------
    }

    private fun setupClickListeners() {
        // Listener para añadir día a la lista del ViewModel
        binding.buttonAddDayToList.setOnClickListener {
            val dayName = binding.editTextDayNameAdd.text.toString().trim()
            if (dayName.isNotEmpty()) {
                routineViewModel.addDay(dayName) // Llama al ViewModel
                binding.editTextDayNameAdd.text?.clear() // Limpia campo
                binding.inputLayoutDayNameAdd.error = null
            } else {
                binding.inputLayoutDayNameAdd.error = "Introduce un nombre para el día"
            }
        }

        // Listener para el botón "Siguiente: Configurar Ejercicios"
        binding.buttonSaveRoutineFinal.text = "Siguiente: Configurar Ejercicios" // Establecer texto
        binding.buttonSaveRoutineFinal.setOnClickListener {
            // Validar que se haya añadido al menos un día
            if (routineViewModel.daysList.value.isNullOrEmpty()) {
                Toast.makeText(
                    context,
                    "Debes añadir al menos un día a la rutina",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            // Navegar al fragmento de configuración de ejercicios
            // *** ASEGÚRATE QUE ESTE ID DE ACCIÓN ES CORRECTO EN mobile_navigation.xml ***
            findNavController().navigate(R.id.action_addRoutineDaysFragment_to_configureExercisesFragment) // <-- CORREGIDO
        }

    }


    private fun showLoading(isLoading: Boolean) {
        // Ya no necesitamos el ProgressBar aquí si el guardado final no ocurre aquí
        // binding.progressBarSaveRoutine.isVisible = isLoading
        binding.buttonAddDayToList.isEnabled = !isLoading
        // Habilitar botón "Siguiente" solo si hay días y no está (hipotéticamente) cargando
        binding.buttonSaveRoutineFinal.isEnabled =
            !isLoading && (routineViewModel.daysList.value?.isNotEmpty() == true)
        binding.editTextDayNameAdd.isEnabled = !isLoading
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}