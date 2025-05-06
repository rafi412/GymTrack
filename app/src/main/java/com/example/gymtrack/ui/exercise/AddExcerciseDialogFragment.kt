package com.example.gymtrack.ui.exercise
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels // Para acceder al ViewModel compartido
import com.example.gymtrack.databinding.DialogAddExerciseBinding
import com.example.gymtrack.ui.exercise.routine.ExerciseData
import com.example.gymtrack.ui.exercise.routine.RoutineCreationViewModel

class AddExerciseDialogFragment : DialogFragment() {

    private var _binding: DialogAddExerciseBinding? = null
    private val binding get() = _binding!!

    // ViewModel compartido
    private val routineViewModel: RoutineCreationViewModel by activityViewModels()

    private var targetDayOrder: Int = -1 // Orden del día al que añadir

    private val TAG = "AddExerciseDialog"

    interface AddExerciseListener {
        fun onExerciseAdded(dayOrder: Int, exercise: ExerciseData)
    }
    private var listener: AddExerciseListener? = null


    companion object {
        private const val ARG_DAY_ORDER = "day_order"

        fun newInstance(dayOrder: Int): AddExerciseDialogFragment {
            val fragment = AddExerciseDialogFragment()
            val args = Bundle()
            args.putInt(ARG_DAY_ORDER, dayOrder)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            targetDayOrder = it.getInt(ARG_DAY_ORDER, -1)
        }
        if (targetDayOrder == -1) {
            Log.e(TAG, "Error: dayOrder no fue proporcionado.")
            dismiss() // No se puede añadir sin saber a qué día
        }

        // Obtener listener
        try {
            listener = parentFragment as? AddExerciseListener
        } catch (e: ClassCastException) {
            Log.e(TAG, "El Fragmento host debe implementar AddExerciseListener", e)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddExerciseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.dialogTitleAddExercise.text = "Añadir Ejercicio al Día $targetDayOrder"
        setupButtons()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun setupButtons() {
        binding.dialogExButtonCancel.setOnClickListener {
            dismiss()
        }
        binding.dialogExButtonAdd.setOnClickListener {
            addExerciseToViewModel()
        }
    }

    private fun addExerciseToViewModel() {
        val name = binding.dialogExEditName.text.toString().trim()
        val group = binding.dialogExEditGroup.text.toString().trim()
        val seriesStr = binding.dialogExEditSeries.text.toString().trim()
        val reps = binding.dialogExEditReps.text.toString().trim()
        val rest = binding.dialogExEditRest.text.toString().trim()
        val notes = binding.dialogExEditNotes.text.toString().trim()

        if (name.isEmpty()) {
            binding.dialogExInputName.error = "Nombre obligatorio"
            return
        } else {
            binding.dialogExInputName.error = null
        }

        val series = seriesStr.toIntOrNull()
        if (series == null && seriesStr.isNotEmpty()) {
            binding.dialogExInputSeries.error = "Número inválido"
            return
        } else {
            binding.dialogExInputSeries.error = null
        }


        val newExercise = ExerciseData(
            name = name,
            muscleGroup = group.ifEmpty { null },
            series = series,
            reps = reps.ifEmpty { null },
            rest = rest.ifEmpty { null },
            notes = notes.ifEmpty { null }
            // El orden se asignará en el ViewModel
        )

        // Llamar al ViewModel para añadir el ejercicio
        routineViewModel.addExerciseToDay(targetDayOrder, newExercise)

        // Notificar al listener (ConfigureExercisesFragment)
        listener?.onExerciseAdded(targetDayOrder, newExercise)

        dismiss() // Cerrar el diálogo
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}