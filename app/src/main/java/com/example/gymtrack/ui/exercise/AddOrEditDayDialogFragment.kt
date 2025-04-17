package com.example.gymtrack.ui // O donde lo pongas

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater // Importar LayoutInflater
import android.view.View
import android.view.ViewGroup // Importar ViewGroup
import android.view.WindowManager // Importar WindowManager
import android.widget.Toast
import androidx.core.view.isVisible // Importar isVisible
import androidx.fragment.app.DialogFragment
import com.example.gymtrack.databinding.DialogAddEditDayBinding // Binding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

// Interfaz para notificar
interface DayUpdateListener {
    fun onDayUpdated()
}

class AddOrEditDayDialogFragment : DialogFragment() {

    private var _binding: DialogAddEditDayBinding? = null
    private val binding get() = _binding!!

    // Firebase
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    // Argumentos
    private lateinit var routineId: String
    private var dayId: String? = null
    private var currentDayName: String? = null

    private var listener: DayUpdateListener? = null
    private val TAG = "AddEditDayDialog"

    companion object {
        private const val ARG_ROUTINE_ID = "routine_id"
        private const val ARG_DAY_ID = "day_id"
        private const val ARG_DAY_NAME = "day_name"

        fun newInstance(routineId: String, dayId: String?, currentName: String?): AddOrEditDayDialogFragment {
            val fragment = AddOrEditDayDialogFragment()
            val args = Bundle()
            args.putString(ARG_ROUTINE_ID, routineId)
            args.putString(ARG_DAY_ID, dayId)
            args.putString(ARG_DAY_NAME, currentName)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) // Llamada a super
        db = Firebase.firestore
        auth = Firebase.auth
        arguments?.let {
            routineId = it.getString(ARG_ROUTINE_ID) ?: ""
            dayId = it.getString(ARG_DAY_ID)
            currentDayName = it.getString(ARG_DAY_NAME)
        }
        if (routineId.isEmpty()) {
            Log.e(TAG, "Routine ID vacío en onCreate")
            // Considera no llamar a dismiss aquí, sino manejarlo en onViewCreated o al intentar guardar
        }

        try {
            listener = parentFragment as? DayUpdateListener
            if (listener == null) listener = activity as? DayUpdateListener
        } catch (e: ClassCastException) { Log.w(TAG, "Host no implementa DayUpdateListener") }
    }

    // --- onCreateView RESTAURADO ---
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddEditDayBinding.inflate(inflater, container, false)
        return binding.root
    }
    // -----------------------------

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState) // Llamada a super
        // Validar routineId aquí también por si acaso
        if (routineId.isEmpty()) {
            Toast.makeText(context, "Error: ID de rutina no válido.", Toast.LENGTH_SHORT).show()
            dismissAllowingStateLoss()
            return
        }

        if (dayId == null) {
            binding.dialogDayTitle.text = "Añadir Nuevo Día"
            binding.dialogDayButtonSave.text = "Añadir Día" // Ajustar texto botón
        } else {
            binding.dialogDayTitle.text = "Editar Día"
            binding.dialogDayEditTextName.setText(currentDayName ?: "")
            binding.dialogDayButtonSave.text = "Guardar Cambios" // Ajustar texto botón
        }
        setupButtons()
    }

    // --- onStart CORREGIDO ---
    override fun onStart() {
        super.onStart() // <-- LLAMADA A SUPER AÑADIDA
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
    // -------------------------

    private fun setupButtons() {
        binding.dialogDayButtonCancel.setOnClickListener { dismiss() }
        binding.dialogDayButtonSave.setOnClickListener { saveDay() }
    }

    private fun saveDay() {
        val newDayName = binding.dialogDayEditTextName.text.toString().trim()
        val userId = auth.currentUser?.uid

        // --- Validación Mejorada ---
        if (userId == null) {
            Toast.makeText(context, "Error: Usuario no identificado.", Toast.LENGTH_SHORT).show()
            return
        }
        if (routineId.isEmpty()){ // Doble check
            Toast.makeText(context, "Error: ID de rutina inválido.", Toast.LENGTH_SHORT).show()
            return
        }
        if (newDayName.isEmpty()) {
            binding.dialogDayInputLayoutName.error = "El nombre del día es obligatorio"
            return
        }
        // -------------------------

        binding.dialogDayInputLayoutName.error = null // Limpiar error
        showLoading(true)

        val daysCollectionRef = db.collection("users").document(userId)
            .collection("userRoutines").document(routineId)
            .collection("routineDays")

        if (dayId == null) { // --- MODO AÑADIR ---
            daysCollectionRef.orderBy("orden", Query.Direction.DESCENDING).limit(1).get()
                .addOnSuccessListener { querySnapshot ->
                    val nextOrder = if (querySnapshot.isEmpty) 1 else (querySnapshot.documents[0].getLong("orden") ?: 0) + 1
                    val newDayData = hashMapOf("nombreDia" to newDayName, "orden" to nextOrder)

                    daysCollectionRef.add(newDayData)
                        .addOnSuccessListener {
                            handleSaveSuccess("Día añadido")
                        }
                        .addOnFailureListener { e -> handleSaveError(e) }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error al obtener orden para nuevo día", e)
                    handleSaveError(e)
                }
        } else { // --- MODO EDITAR ---
            val dayDocRef = daysCollectionRef.document(dayId!!)
            dayDocRef.update("nombreDia", newDayName)
                .addOnSuccessListener {
                    handleSaveSuccess("Día actualizado")
                }
                .addOnFailureListener { e -> handleSaveError(e) }
        }
    }

    // --- Funciones Helper para Éxito/Error ---
    private fun handleSaveSuccess(message: String) {
        showLoading(false)
        Log.d(TAG, message)
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        listener?.onDayUpdated() // Notificar al listener
        dismiss() // Cerrar diálogo
    }

    private fun handleSaveError(e: Exception) {
        showLoading(false)
        Log.e(TAG, "Error al guardar día", e)
        Toast.makeText(context, "Error al guardar: ${e.message}", Toast.LENGTH_LONG).show()
    }
    // ---------------------------------------

    private fun showLoading(isLoading: Boolean) {
        // Asegurarse que el binding no es null
        _binding?.let {
            it.dialogDayProgressBar.isVisible = isLoading
            it.dialogDayButtonSave.isEnabled = !isLoading
            it.dialogDayButtonCancel.isEnabled = !isLoading
            it.dialogDayEditTextName.isEnabled = !isLoading
        }
    }

    override fun onDestroyView() {
        super.onDestroyView() // Llamada a super
        _binding = null
    }
}