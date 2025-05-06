package com.example.gymtrack.ui.exercise.routine

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.example.gymtrack.databinding.DialogEditRoutineDetailsBinding // Binding para el nuevo diálogo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class EditRoutineDetailsDialogFragment : DialogFragment() {

    private var _binding: DialogEditRoutineDetailsBinding? = null
    private val binding get() = _binding!!

    // Firebase
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    // Argumentos recibidos
    private lateinit var routineId: String
    private var currentName: String? = null
    private var currentDescription: String? = null

    private val TAG = "EditRoutineDetailsDlg"

    interface RoutineDetailsUpdateListener {
        fun onRoutineDetailsUpdated()
    }
    private var listener: RoutineDetailsUpdateListener? = null


    companion object {
        private const val ARG_ROUTINE_ID = "routine_id"
        private const val ARG_CURRENT_NAME = "current_name"
        private const val ARG_CURRENT_DESC = "current_desc"

        fun newInstance(routineId: String, name: String?, description: String?): EditRoutineDetailsDialogFragment {
            val fragment = EditRoutineDetailsDialogFragment()
            val args = Bundle()
            args.putString(ARG_ROUTINE_ID, routineId)
            args.putString(ARG_CURRENT_NAME, name)
            args.putString(ARG_CURRENT_DESC, description)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Firebase.firestore
        auth = Firebase.auth

        arguments?.let {
            routineId = it.getString(ARG_ROUTINE_ID) ?: ""
            currentName = it.getString(ARG_CURRENT_NAME)
            currentDescription = it.getString(ARG_CURRENT_DESC)
        }
        if (routineId.isEmpty()) {
            Log.e(TAG, "Error: routineId no proporcionado.")
            dismissAllowingStateLoss() // Cerrar si falta ID
            return
        }

        // Obtener listener
        try {
            listener = parentFragment as? RoutineDetailsUpdateListener
            if (listener == null) {
                listener = activity as? RoutineDetailsUpdateListener
            }
        } catch (e: ClassCastException) {
            Log.w(TAG, "Host no implementa RoutineDetailsUpdateListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogEditRoutineDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Rellenar campos con datos actuales
        binding.dialogEditEditTextRoutineName.setText(currentName ?: "")
        binding.dialogEditEditTextRoutineDesc.setText(currentDescription ?: "")
        setupButtons()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun setupButtons() {
        binding.dialogEditButtonCancel.setOnClickListener { dismiss() }
        binding.dialogEditButtonSave.setOnClickListener { saveChanges() }
    }

    private fun saveChanges() {
        val newName = binding.dialogEditEditTextRoutineName.text.toString().trim()
        val newDesc = binding.dialogEditEditTextRoutineDesc.text.toString().trim()
        val userId = auth.currentUser?.uid

        if (userId == null) {
            Toast.makeText(context, "Error: Usuario no identificado.", Toast.LENGTH_SHORT).show()
            return
        }
        if (newName.isEmpty()) {
            binding.dialogEditInputLayoutRoutineName.error = "El nombre es obligatorio"
            return
        } else {
            binding.dialogEditInputLayoutRoutineName.error = null
        }

        showLoading(true)

        val routineDocRef = db.collection("users").document(userId)
            .collection("userRoutines").document(routineId)

        // Crear mapa solo con los campos a actualizar
        val updates = hashMapOf<String, Any>(
            "nombre" to newName,
            "descripcion" to newDesc // Actualiza aunque esté vacío
        )

        routineDocRef.update(updates)
            .addOnSuccessListener {
                showLoading(false)
                Log.d(TAG, "Detalles de rutina actualizados con éxito.")
                Toast.makeText(context, "Rutina actualizada", Toast.LENGTH_SHORT).show()
                listener?.onRoutineDetailsUpdated() // Notificar al fragmento padre
                dismiss()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e(TAG, "Error al actualizar rutina", e)
                Toast.makeText(context, "Error al guardar: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.dialogEditProgressBar.isVisible = isLoading
        binding.dialogEditButtonSave.isEnabled = !isLoading
        binding.dialogEditButtonCancel.isEnabled = !isLoading
        binding.dialogEditEditTextRoutineName.isEnabled = !isLoading
        binding.dialogEditEditTextRoutineDesc.isEnabled = !isLoading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}