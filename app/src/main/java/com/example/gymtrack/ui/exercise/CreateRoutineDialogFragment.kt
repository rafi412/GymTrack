package com.example.gymtrack.ui.exercise // O tu paquete

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.example.gymtrack.databinding.DialogCreateRoutineBinding // Binding del diálogo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class CreateRoutineDialogFragment : DialogFragment() {

    private var _binding: DialogCreateRoutineBinding? = null
    private val binding get() = _binding!!

    // Firebase
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private val TAG = "CreateRoutineDialog"

    // --- Interfaz Corregida ---
    // Interfaz para notificar al fragmento padre DESPUÉS de crear la rutina base
    interface RoutineCreationListener {
        fun onRoutineCreatedOrUpdated(routineId: String) // Solo necesita el ID para refrescar
    }
    private var listener: RoutineCreationListener? = null
    // -------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Firebase.firestore
        auth = Firebase.auth

        // Obtener el listener (DashboardFragment)
        try {
            listener = parentFragment as? RoutineCreationListener
        } catch (e: ClassCastException) {
            Log.e(TAG, "El Fragmento host debe implementar RoutineCreationListener", e)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCreateRoutineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupButtons()
    }

    override fun onStart() {
        super.onStart()
        // Ajustes de ventana (sin cambios)
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun setupButtons() {
        binding.dialogButtonCancel.setOnClickListener {
            dismiss()
        }
        binding.dialogButtonSave.setOnClickListener {
            saveNewRoutineAndOpenAddDays() // Llama a la función que guarda y abre el otro diálogo
        }
    }

    // Guarda la rutina y luego abre el diálogo para añadir días
    private fun saveNewRoutineAndOpenAddDays() {
        val routineName = binding.dialogEditTextRoutineName.text.toString().trim()
        val routineDesc = binding.dialogEditTextRoutineDesc.text.toString().trim()
        val currentUser = auth.currentUser

        // Validación de entradas
        if (currentUser == null) {
            Toast.makeText(context, "Error: Usuario no identificado.", Toast.LENGTH_SHORT).show()
            return
        }
        if (routineName.isEmpty()) {
            binding.dialogInputLayoutRoutineName.error = "El nombre es obligatorio"
            return
        } else {
            binding.dialogInputLayoutRoutineName.error = null
        }

        showLoading(true) // Mostrar progreso

        val userId = currentUser.uid
        val userRoutinesCollection = db.collection("users").document(userId).collection("userRoutines")

        // Datos para la nueva rutina
        val newRoutineData = hashMapOf(
            "nombre" to routineName,
            "descripcion" to routineDesc,
            "createdAt" to FieldValue.serverTimestamp()
        )

        // Añadir la rutina a Firestore
        userRoutinesCollection.add(newRoutineData)
            .addOnSuccessListener { documentReference ->
                // No ocultar loading aquí, se hará al cerrar el diálogo
                val newRoutineId = documentReference.id
                Log.d(TAG, "Rutina '$routineName' creada con ID: $newRoutineId. Abriendo diálogo de días.")

                // --- Llamada Corregida al Listener ---
                // Notificar al DashboardFragment que la rutina se creó (para que refresque la lista)
                listener?.onRoutineCreatedOrUpdated(newRoutineId)
                // ----------------------------------

                // Abrir el siguiente diálogo (AddDaysDialogFragment)
                val addDaysDialog = AddDaysDialogFragment.newInstance(newRoutineId, routineName)
                // Usar parentFragmentManager para mostrar un diálogo desde otro
                addDaysDialog.show(parentFragmentManager, "AddDaysDialog")

                // Cerrar este diálogo (CreateRoutine)
                dismiss()
            }
            .addOnFailureListener { e ->
                showLoading(false) // Ocultar loading en caso de error
                Log.e(TAG, "Error al guardar rutina", e)
                Toast.makeText(context, "Error al guardar rutina: ${e.message}", Toast.LENGTH_LONG).show()
                // No cerrar el diálogo para que el usuario pueda reintentar
            }
    }

    // Muestra/oculta el ProgressBar y habilita/deshabilita botones
    private fun showLoading(isLoading: Boolean) {
        binding.dialogProgressBar.isVisible = isLoading
        binding.dialogButtonSave.isEnabled = !isLoading
        binding.dialogButtonCancel.isEnabled = !isLoading
        binding.dialogEditTextRoutineName.isEnabled = !isLoading
        binding.dialogEditTextRoutineDesc.isEnabled = !isLoading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Limpiar binding
    }

    // Opcional: Método estático para crear instancia si necesitas pasarle argumentos en el futuro
    // companion object {
    //     fun newInstance(): CreateRoutineDialogFragment {
    //         return CreateRoutineDialogFragment()
    //     }
    // }
}