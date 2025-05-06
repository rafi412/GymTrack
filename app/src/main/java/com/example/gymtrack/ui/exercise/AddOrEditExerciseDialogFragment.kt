package com.example.gymtrack.ui.routine_detail

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.example.gymtrack.databinding.DialogAddEditExerciseBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.io.Serializable
import java.util.HashMap

// Interfaz para notificar al fragmento padre
interface ExerciseUpdateListener {
    fun onExerciseUpdated()
}

class AddOrEditExerciseDialogFragment : DialogFragment() {

    private var _binding: DialogAddEditExerciseBinding? = null

    private val binding get() = _binding!!

    // Firebase
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    // Argumentos
    private lateinit var routineId: String
    private lateinit var dayId: String
    private var exerciseId: String? = null // Null para añadir
    private var currentExerciseData: Map<String, Any>? = null // Para editar

    // Estado anterior para UI
    private var previousWeight: String? = null
    private var previousReps: String? = null

    private var listener: ExerciseUpdateListener? = null
    private val TAG = "AddEditExerciseDialog"

    companion object {
        private const val ARG_ROUTINE_ID = "routine_id"
        private const val ARG_DAY_ID = "day_id"
        private const val ARG_EXERCISE_ID = "exercise_id"
        private const val ARG_EXERCISE_DATA = "exercise_data"

        fun newInstance(
            routineId: String,
            dayId: String,
            exerciseId: String?,
            exerciseDataMap: Map<String, Any>?
        ): AddOrEditExerciseDialogFragment {
            val fragment = AddOrEditExerciseDialogFragment()
            val args = Bundle()
            args.putString(ARG_ROUTINE_ID, routineId)
            args.putString(ARG_DAY_ID, dayId)
            args.putString(ARG_EXERCISE_ID, exerciseId)
            if (exerciseDataMap != null) {
                // Pasar como HashMap Serializable
                args.putSerializable(ARG_EXERCISE_DATA, HashMap(exerciseDataMap))
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Firebase.firestore
        auth = Firebase.auth

        // Recuperar argumentos
        arguments?.let {
            routineId = it.getString(ARG_ROUTINE_ID) ?: ""
            dayId = it.getString(ARG_DAY_ID) ?: ""
            exerciseId = it.getString(ARG_EXERCISE_ID)
            if (it.containsKey(ARG_EXERCISE_DATA)) {
                @Suppress("UNCHECKED_CAST", "DEPRECATION")
                currentExerciseData = it.getSerializable(ARG_EXERCISE_DATA) as? HashMap<String, Any>
            }
        }

        // Validar IDs esenciales
        if (routineId.isEmpty() || dayId.isEmpty()) {
            Log.e(TAG, "Error crítico: routineId o dayId vacíos en onCreate.")
        }

        listener = targetFragment as? ExerciseUpdateListener
        if (listener == null) {
            // Fallback a parentFragment o activity si targetFragment no se usó/funcionó
            try {
                listener = parentFragment as? ExerciseUpdateListener
                if (listener == null) {
                    listener = activity as? ExerciseUpdateListener
                }
            } catch (e: ClassCastException) {
                Log.w(
                    TAG,
                    "Host (target, parent, or activity) no implementa ExerciseUpdateListener"
                )
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddEditExerciseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Validar IDs de nuevo antes de configurar UI
        if (routineId.isEmpty() || dayId.isEmpty()) {
            Toast.makeText(context, "Error: Faltan datos de rutina/día.", Toast.LENGTH_LONG).show()
            dismissAllowingStateLoss()
            return
        }


        if (exerciseId == null) { // --- MODO AÑADIR ---
            binding.dialogExerciseTitle.text = "Añadir Ejercicio al Día" // Título más específico
            binding.dialogExButtonAdd.text = "Añadir Ejercicio"
            binding.layoutPesoAnterior.visibility = View.GONE
            binding.layoutRepsAnteriores.visibility = View.GONE
        } else { // --- MODO EDITAR ---
            binding.dialogExerciseTitle.text = "Editar Ejercicio"
            binding.dialogExButtonAdd.text = "Guardar Cambios"
            binding.layoutPesoAnterior.visibility = View.VISIBLE
            binding.layoutRepsAnteriores.visibility = View.VISIBLE
            populateFieldsForEdit()
        }
        setupButtons()
        setupFieldListeners()
    }

    override fun onStart() {
        super.onStart() // Llamada a super
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun populateFieldsForEdit() {
        currentExerciseData?.let { data ->
            binding.dialogExEditName.setText(data["nombre"] as? String ?: "")
            binding.dialogExEditGroup.setText(data["grupoMuscular"] as? String ?: "")
            binding.dialogExEditSeries.setText((data["series"] as? Long)?.toString() ?: "")

            // --- Obtener peso y reps guardados ---
            // Manejar Double y Long para peso desde Firestore
            val pesoDbValue = data["peso"]
            val pesoActualGuardado = when (pesoDbValue) {
                is Double -> pesoDbValue.toString()
                is Long -> pesoDbValue.toString()
                else -> ""
            }
            val repsActualesGuardadas = data["repeticiones"] as? String ?: ""
            // ------------------------------------

            binding.dialogExEditRest.setText(data["descanso"] as? String ?: "")
            binding.dialogExEditNotes.setText(data["notas"] as? String ?: "")

            // --- Rellenar campos Actuales y Anteriores ---
            binding.dialogExEditPeso.setText(pesoActualGuardado)
            binding.textPesoAnterior.text = pesoActualGuardado.ifEmpty { "-" }
            previousWeight = pesoActualGuardado // Guardar estado inicial

            binding.dialogExEditReps.setText(repsActualesGuardadas)
            binding.textRepsAnteriores.text = repsActualesGuardadas.ifEmpty { "-" }
            previousReps = repsActualesGuardadas // Guardar estado inicial
            // --------------------------------------------
        }
    }

    private fun setupFieldListeners() {
        // Listener para Peso Actual
        binding.dialogExEditPeso.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (exerciseId != null && s.toString() != previousWeight) {
                    binding.textPesoAnterior.text = previousWeight?.ifEmpty { "-" } ?: "-"
                }
            }
        })

        // Listener para Reps Actuales
        binding.dialogExEditReps.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (exerciseId != null && s.toString() != previousReps) {
                    binding.textRepsAnteriores.text = previousReps?.ifEmpty { "-" } ?: "-"
                }
            }
        })
    }

    private fun setupButtons() {
        binding.dialogExButtonCancel.setOnClickListener { dismiss() }
        binding.dialogExButtonAdd.setOnClickListener { saveExercise() }
    }

    private fun saveExercise() {
        val name = binding.dialogExEditName.text.toString().trim()
        val group = binding.dialogExEditGroup.text.toString().trim()
        val seriesStr = binding.dialogExEditSeries.text.toString().trim()
        val pesoStr = binding.dialogExEditPeso.text.toString().trim()
        val reps = binding.dialogExEditReps.text.toString().trim()
        val rest = binding.dialogExEditRest.text.toString().trim()
        val notes = binding.dialogExEditNotes.text.toString().trim()
        val userId = auth.currentUser?.uid

        // --- Validación ---
        if (userId == null) {
            Toast.makeText(context, "Error: Usuario no identificado.", Toast.LENGTH_SHORT).show()
            return
        }
        if (routineId.isEmpty() || dayId.isEmpty()) {
            Toast.makeText(context, "Error: IDs de rutina/día inválidos.", Toast.LENGTH_SHORT)
                .show()
            return
        }
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

        val peso = pesoStr.toDoubleOrNull()
        if (peso == null && pesoStr.isNotEmpty()) {
            binding.dialogExInputPeso.error = "Número inválido"
            return
        } else {
            binding.dialogExInputPeso.error = null
        }
        // ----------------

        showLoading(true)

        val exercisesCollectionRef = db.collection("users").document(userId)
            .collection("userRoutines").document(routineId)
            .collection("routineDays").document(dayId)
            .collection("dayExercises")

        val exerciseDataMap = hashMapOf<String, Any?>(
            "nombre" to name,
            "grupoMuscular" to group.ifEmpty { null },
            "series" to series,
            "peso" to peso, // Guardar Double?
            "repeticiones" to reps.ifEmpty { null },
            "descanso" to rest.ifEmpty { null },
            "notas" to notes.ifEmpty { null }
        )

        if (exerciseId == null) { // --- MODO AÑADIR ---
            exercisesCollectionRef.orderBy("orden", Query.Direction.DESCENDING).limit(1).get()
                .addOnSuccessListener { querySnapshot ->
                    val nextOrder =
                        if (querySnapshot.isEmpty) 1 else (querySnapshot.documents[0].getLong("orden")
                            ?: 0) + 1
                    exerciseDataMap["orden"] = nextOrder

                    exercisesCollectionRef.add(exerciseDataMap)
                        .addOnSuccessListener { handleSaveSuccess("Ejercicio añadido") }
                        .addOnFailureListener { e -> handleSaveError(e) }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error al obtener orden", e)
                    handleSaveError(e)
                }

        } else { // --- MODO EDITAR ---
            val exerciseDocRef = exercisesCollectionRef.document(exerciseId!!)
            exerciseDocRef.update(exerciseDataMap) // Actualiza campos (no actualiza orden aquí)
                .addOnSuccessListener { handleSaveSuccess("Ejercicio actualizado") }
                .addOnFailureListener { e -> handleSaveError(e) }
        }
    }

    private fun handleSaveSuccess(message: String) {
        // Asegurarse que el binding no es null antes de usar listener o dismiss
        if (_binding == null) return
        showLoading(false)
        Log.d(TAG, message)
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        listener?.onExerciseUpdated()
        dismiss()
    }

    private fun handleSaveError(e: Exception) {
        if (_binding == null) return
        showLoading(false)
        Log.e(TAG, "Error al guardar ejercicio", e)
        Toast.makeText(context, "Error al guardar: ${e.message}", Toast.LENGTH_LONG).show()
    }

    private fun showLoading(isLoading: Boolean) {
        _binding?.let {
            it.dialogExerciseProgressBar.isVisible = isLoading
            it.dialogExButtonAdd.isEnabled = !isLoading
            it.dialogExButtonCancel.isEnabled = !isLoading
            it.dialogExEditName.isEnabled = !isLoading
            it.dialogExEditGroup.isEnabled = !isLoading
            it.dialogExEditSeries.isEnabled = !isLoading
            it.dialogExEditPeso.isEnabled = !isLoading
            it.dialogExEditReps.isEnabled = !isLoading
            it.dialogExEditRest.isEnabled = !isLoading
            it.dialogExEditNotes.isEnabled = !isLoading
        }
    }

    override fun onDestroyView() {
        super.onDestroyView() // Llamada a super
        _binding = null // Limpiar binding
    }
}