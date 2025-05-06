package com.example.gymtrack.ui.exercise.routine

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels // Para ViewModel compartido con Activity
import androidx.navigation.fragment.findNavController
import com.example.gymtrack.R
import com.example.gymtrack.databinding.FragmentCreateRoutineDetailsBinding

class CreateRoutineDetailsFragment : Fragment() {

    private var _binding: FragmentCreateRoutineDetailsBinding? = null
    private val binding get() = _binding!!

    // Obtener instancia del ViewModel compartido (alcance a la Activity por defecto)
    private val routineViewModel: RoutineCreationViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflar el layout usando ViewBinding
        _binding = FragmentCreateRoutineDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Observar datos del ViewModel para pre-rellenar los campos
        // Esto es útil si el usuario navega hacia atrás desde el siguiente paso
        routineViewModel.routineName.observe(viewLifecycleOwner) { name ->
            // Solo actualiza si el texto actual es diferente, para no interferir con la escritura del usuario
            if (binding.editTextRoutineNameDetails.text.toString() != name) {
                binding.editTextRoutineNameDetails.setText(name ?: "")
            }
        }
        routineViewModel.routineDescription.observe(viewLifecycleOwner) { desc ->
            if (binding.editTextRoutineDescDetails.text.toString() != desc) {
                binding.editTextRoutineDescDetails.setText(desc ?: "")
            }
        }

        // Configurar el listener para el botón "Siguiente"
        binding.buttonNextToAddDays.setOnClickListener {
            val name = binding.editTextRoutineNameDetails.text.toString().trim()
            val desc = binding.editTextRoutineDescDetails.text.toString().trim()

            // Validación simple
            if (name.isEmpty()) {
                binding.inputLayoutRoutineNameDetails.error = "El nombre de la rutina es obligatorio"
                return@setOnClickListener // Detener si el nombre está vacío
            } else {
                binding.inputLayoutRoutineNameDetails.error = null // Limpiar error si es válido
            }

            // Guardar los detalles introducidos en el ViewModel compartido
            routineViewModel.setRoutineDetails(name, desc)

            // Navegar al siguiente fragmento (AddRoutineDaysFragment)
            findNavController().navigate(R.id.action_createRoutineDetailsFragment_to_addRoutineDaysFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Limpiar la referencia al binding para evitar memory leaks
    }
}