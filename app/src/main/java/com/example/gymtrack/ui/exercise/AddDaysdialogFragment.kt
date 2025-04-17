package com.example.gymtrack.ui.exercise // O tu paquete

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.example.gymtrack.databinding.DialogAddDaysBinding // Binding del nuevo diálogo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class AddDaysDialogFragment : DialogFragment() {

    private var _binding: DialogAddDaysBinding? = null
    private val binding get() = _binding!!

    // Firebase
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    // Argumentos recibidos
    private lateinit var routineId: String
    private lateinit var routineName: String

    // Estado local
    private var nextDayOrder: Int = 1
    private val addedDaysTextBuilder = StringBuilder()

    private val TAG = "AddDaysDialog"

    // Interfaz para notificar cuando se finaliza (opcional, si necesitas hacer algo más)
    interface AddDaysListener {
        fun onAddDaysFinished(routineId: String)
    }
    private var listener: AddDaysListener? = null


    companion object {
        private const val ARG_ROUTINE_ID = "routine_id"
        private const val ARG_ROUTINE_NAME = "routine_name"

        // Fábrica para pasar argumentos
        fun newInstance(routineId: String, routineName: String): AddDaysDialogFragment {
            val fragment = AddDaysDialogFragment()
            val args = Bundle()
            args.putString(ARG_ROUTINE_ID, routineId)
            args.putString(ARG_ROUTINE_NAME, routineName)
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
            routineName = it.getString(ARG_ROUTINE_NAME) ?: "Rutina Desconocida"
        }
        if (routineId.isEmpty()) {
            Log.e(TAG, "Error: routineId no fue proporcionado al diálogo.")
            Toast.makeText(context, "Error al abrir diálogo de días.", Toast.LENGTH_SHORT).show()
            dismiss() // Cerrar si falta el ID
            return
        }

        // Obtener listener (opcional)
        try {
            listener = parentFragment as? AddDaysListener
        } catch (e: ClassCastException) {
            // No hacer nada si el padre no implementa, es opcional
        }

        // Determinar el próximo número de orden al iniciar
        fetchNextDayOrder()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddDaysBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.dialogTitleAddDays.text = "Añadir Días a: $routineName"
        binding.textAddedDaysDialog.movementMethod = ScrollingMovementMethod() // Para scroll
        setupButtons()
        updateAddedDaysText() // Mostrar texto inicial
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun setupButtons() {
        binding.dialogButtonAddDay.setOnClickListener {
            addDayToRoutine()
        }
        binding.dialogButtonFinish.setOnClickListener {
            listener?.onAddDaysFinished(routineId) // Notificar (opcional)
            dismiss()
        }
    }

    private fun fetchNextDayOrder() {
        val currentUser = auth.currentUser
        if (currentUser == null) return // No debería pasar

        val userId = currentUser.uid
        db.collection("users").document(userId)
            .collection("userRoutines").document(routineId)
            .collection("routineDays")
            .orderBy("orden", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    nextDayOrder = 1
                } else {
                    val lastOrder = querySnapshot.documents[0].getLong("orden") ?: 0
                    nextDayOrder = (lastOrder + 1).toInt()
                }
                Log.d(TAG, "Próximo orden de día determinado: $nextDayOrder")
                updateAddedDaysText() // Actualizar texto con el orden correcto
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al obtener último orden de día", e)
                // Asumir 1 si falla, o mostrar error
                nextDayOrder = 1
                updateAddedDaysText()
            }
    }

    private fun addDayToRoutine() {
        val dayName = binding.dialogEditTextDayName.text.toString().trim()
        val currentUser = auth.currentUser

        if (currentUser == null) {
            Toast.makeText(context, "Error: Usuario no identificado.", Toast.LENGTH_SHORT).show()
            return
        }
        if (dayName.isEmpty()) {
            binding.dialogInputLayoutDayName.error = "El nombre del día es obligatorio"
            return
        } else {
            binding.dialogInputLayoutDayName.error = null
        }

        showLoading(true)
        val userId = currentUser.uid
        val routineDaysCollectionRef = db.collection("users").document(userId)
            .collection("userRoutines").document(routineId)
            .collection("routineDays")

        val newDayData = hashMapOf(
            "nombreDia" to dayName,
            "orden" to nextDayOrder
        )

        routineDaysCollectionRef.add(newDayData)
            .addOnSuccessListener { documentReference ->
                showLoading(false)
                Log.d(TAG, "Día '$dayName' añadido con orden $nextDayOrder")

                // Actualizar UI local
                addedDaysTextBuilder.append("Día $nextDayOrder: $dayName\n")
                updateAddedDaysText()
                binding.dialogEditTextDayName.text?.clear()
                nextDayOrder++ // Incrementar para el siguiente
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e(TAG, "Error al añadir día", e)
                Toast.makeText(context, "Error al añadir día: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun updateAddedDaysText() {
        if (addedDaysTextBuilder.isEmpty()) {
            binding.textAddedDaysDialog.text = "Añade el primer día (Día $nextDayOrder)."
        } else {
            binding.textAddedDaysDialog.text = addedDaysTextBuilder.toString()
        }
        // Actualizar hint o label si quieres indicar el siguiente número de día
        binding.dialogInputLayoutDayName.hint = "Nombre del Día $nextDayOrder"
    }


    private fun showLoading(isLoading: Boolean) {
        binding.dialogProgressBarAddDay.isVisible = isLoading
        binding.dialogButtonAddDay.isEnabled = !isLoading
        binding.dialogButtonFinish.isEnabled = !isLoading
        binding.dialogEditTextDayName.isEnabled = !isLoading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}