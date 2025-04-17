package com.example.gymtrack.ui.home // O donde esté tu diálogo

// Importa User desde tu paquete model
import User
import android.content.ContentValues.TAG // Usa esta constante TAG si quieres
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.example.gymtrack.R
import com.example.gymtrack.databinding.DialogEditProfileBinding

// Interfaz para notificar a HomeFragment
interface ProfileEditListener {
    fun onProfileSaved(updatedData: Map<String, Any?>)
}


class EditProfileDialogFragment : DialogFragment() {

    private var _binding: DialogEditProfileBinding? = null
    private val binding get() = _binding!! // Usar con precaución

    private var listener: ProfileEditListener? = null

    // --- Mapas y variables de estado ---
    private val objetivoMap = mapOf(
        "Perder Peso (Rápido)" to "PERDER_PESO",
        "Perder Peso (Lento)" to "PERDER_PESO_LENTO",
        "Mantener Peso" to "MANTENER",
        "Ganar Masa Muscular" to "GANAR_MASA",
        "Ganar Masa (Rápido)" to "GANAR_MASA_RAPIDO"
    )
    private var selectedObjetivoKey: String? = null

    private val actividadMap = mapOf(
        "Sedentario (Poco o ningún ejercicio)" to "SEDENTARIO",
        "Ligero (Ejercicio 1-3 días/sem)" to "LIGERO",
        "Moderado (Ejercicio 3-5 días/sem)" to "MODERADO",
        "Activo (Ejercicio 6-7 días/sem)" to "ACTIVO",
        "Muy Activo (Ejercicio intenso + Trabajo físico)" to "MUY_ACTIVO"
    )
    private var selectedActividadKey: String? = null

    // Variables para guardar los datos iniciales recibidos
    private var currentName: String? = null
    private var currentAge: Int? = null
    private var currentWeight: Double? = null
    private var currentHeightCm: Double? = null
    private var currentSex: String? = null
    private var initialCalories: Int? = null
    private var initialProteins: Int? = null
    private var initialCarbs: Int? = null
    // No necesitamos currentNivelActividadKey aquí, usamos selectedActividadKey directamente

    companion object {
        // --- Constantes para Claves de Argumentos ---
        private const val ARG_NOMBRE = "arg_nombre"
        private const val ARG_EDAD = "arg_edad"
        private const val ARG_PESO = "arg_peso"
        private const val ARG_ALTURA_CM = "arg_altura_cm" // Clave específica para CM
        private const val ARG_SEXO = "arg_sexo"
        private const val ARG_OBJETIVO_KEY = "arg_objetivo_key" // Clave interna
        private const val ARG_ACTIVIDAD_KEY = "arg_actividad_key" // Clave interna
        private const val ARG_CALORIAS = "arg_calorias"
        private const val ARG_PROTEINAS = "arg_proteinas"
        private const val ARG_CARBOS = "arg_carbos"
        // -------------------------------------------

        // newInstance usando constantes
        fun newInstance(
            user: User,
            calculatedGoals: MacronutrientCalculator.MacroGoals?
        ): EditProfileDialogFragment {
            Log.d("EditProfileDialog", "newInstance llamado con User: $user") // Log inicial
            val fragment = EditProfileDialogFragment()
            val args = Bundle()
            args.putString(ARG_NOMBRE, user.nombre)
            args.putInt(ARG_EDAD, user.edad ?: -1)
            args.putDouble(ARG_PESO, user.peso ?: -1.0)
            // ASUME que user.altura está en CM. Si está en metros, convierte aquí:
            // args.putDouble(ARG_ALTURA_CM, user.altura?.let { it * 100.0 } ?: -1.0)
            args.putDouble(ARG_ALTURA_CM, user.altura ?: -1.0) // Pasando altura (asume CM)
            args.putString(ARG_SEXO, user.sexo)
            args.putString(ARG_OBJETIVO_KEY, user.objetivo) // Pasar CLAVE objetivo
            args.putString(ARG_ACTIVIDAD_KEY, user.nivelActividad) // Pasar CLAVE actividad
            // Pasar macros iniciales (los guardados o los calculados como fallback)
            args.putInt(ARG_CALORIAS, user.caloriasDiarias ?: calculatedGoals?.calories ?: -1)
            args.putInt(ARG_PROTEINAS, user.proteinasDiarias ?: calculatedGoals?.proteinGrams ?: -1)
            args.putInt(ARG_CARBOS, user.carboDiarios ?: calculatedGoals?.carbGrams ?: -1)
            fragment.arguments = args
            Log.d("EditProfileDialog", "Argumentos creados: $args") // Log argumentos
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate ejecutado") // Log ciclo de vida
        // Recuperar datos usando las constantes
        arguments?.let {
            Log.d(TAG, "Recuperando argumentos: $it")
            currentName = it.getString(ARG_NOMBRE)
            currentAge = it.getInt(ARG_EDAD).takeIf { age -> age != -1 }
            currentWeight = it.getDouble(ARG_PESO).takeIf { w -> w != -1.0 }
            currentHeightCm = it.getDouble(ARG_ALTURA_CM).takeIf { h -> h != -1.0 } // Recupera CM
            currentSex = it.getString(ARG_SEXO)
            selectedObjetivoKey = it.getString(ARG_OBJETIVO_KEY) // Recupera clave objetivo
            selectedActividadKey = it.getString(ARG_ACTIVIDAD_KEY) // Recupera clave actividad
            initialCalories = it.getInt(ARG_CALORIAS).takeIf { c -> c != -1 }
            initialProteins = it.getInt(ARG_PROTEINAS).takeIf { p -> p != -1 }
            initialCarbs = it.getInt(ARG_CARBOS).takeIf { c -> c != -1 }
            Log.d(
                TAG,
                "Argumentos recuperados. Objetivo inicial: $selectedObjetivoKey, Actividad inicial: $selectedActividadKey"
            )
        }

        // Obtener listener
        try {
            // --- PRIORIZAR targetFragment ---
            Log.d(TAG, "Intentando obtener listener desde targetFragment: $targetFragment")
            listener = targetFragment as? ProfileEditListener
            // --------------------------------

            if (listener != null) {
                Log.d(TAG, "Listener ASIGNADO desde targetFragment a: ${listener!!::class.java.simpleName}")
            } else {
                // Fallback (menos probable que funcione si targetFragment se estableció)
                Log.w(TAG, "targetFragment no es el listener o es null, intentando con parent/activity...")
                listener = parentFragment as? ProfileEditListener ?: activity as? ProfileEditListener
                if (listener != null) {
                    Log.d(TAG, "Listener ASIGNADO desde parent/activity a: ${listener!!::class.java.simpleName}")
                } else {
                    Log.e(TAG, "Listener NO ASIGNADO (target, parent, y activity fallaron o no implementan)")
                }
            }
        } catch (e: ClassCastException) {
            Log.e(TAG, "Host (target, parent, o activity) NO implementa ProfileEditListener", e)
            listener = null
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Error de estado ilegal al obtener listener", e)
            listener = null
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogEditProfileBinding.inflate(inflater, container, false)
        Log.d(TAG, "onCreateView ejecutado, binding inflado.")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated ejecutado.")
        setupObjetivoDropdown()
        setupActividadDropdown()
        populateFields()
        setupButtons()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun setupObjetivoDropdown() {
        try {
            val objetivos = resources.getStringArray(R.array.objetivo_options)
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                objetivos
            )
            binding.dialogAutoCompleteObjetivo.setAdapter(adapter)

            val objetivoText = objetivoMap.entries.find { it.value == selectedObjetivoKey }?.key
            if (objetivoText != null) {
                binding.dialogAutoCompleteObjetivo.setText(objetivoText, false)
                Log.d(TAG, "Objetivo pre-seleccionado: $objetivoText")
            } else {
                Log.d(TAG, "No se pre-seleccionó objetivo (clave: $selectedObjetivoKey)")
            }

            binding.dialogAutoCompleteObjetivo.setOnItemClickListener { parent, _, position, _ ->
                val selectedText = parent.getItemAtPosition(position) as String
                selectedObjetivoKey = objetivoMap[selectedText]
                binding.dialogInputLayoutObjetivo.error = null
                Log.d(TAG, "Objetivo SELECCIONADO: $selectedText (Clave: $selectedObjetivoKey)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error configurando dropdown de objetivo", e)
        }
    }

    private fun setupActividadDropdown() {
        try {
            val niveles = resources.getStringArray(R.array.nivel_actividad_options)
            val adapter =
                ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, niveles)
            binding.dialogAutoCompleteNivelActividad.setAdapter(adapter)

            val actividadText = actividadMap.entries.find { it.value == selectedActividadKey }?.key
            if (actividadText != null) {
                binding.dialogAutoCompleteNivelActividad.setText(actividadText, false)
                Log.d(TAG, "Actividad pre-seleccionada: $actividadText")
            } else {
                Log.d(TAG, "No se pre-seleccionó actividad (clave: $selectedActividadKey)")
            }

            binding.dialogAutoCompleteNivelActividad.setOnItemClickListener { parent, _, position, _ ->
                val selectedText = parent.getItemAtPosition(position) as String
                selectedActividadKey = actividadMap[selectedText]
                binding.dialogInputLayoutNivelActividad.error = null
                Log.d(
                    TAG,
                    "Nivel actividad SELECCIONADO: $selectedText (Clave: $selectedActividadKey)"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error configurando dropdown de actividad", e)
        }
    }

    private fun populateFields() {
        Log.d(TAG, "populateFields ejecutado.")
        binding.dialogEditTextNombre.setText(currentName ?: "")
        binding.dialogEditTextEdad.setText(currentAge?.toString() ?: "")
        binding.dialogEditTextPeso.setText(currentWeight?.toString() ?: "")
        binding.dialogEditTextAltura.setText(currentHeightCm?.toString() ?: "") // Mostrar CM
        binding.dialogEditTextSexo.setText(currentSex ?: "")
        binding.dialogEditTextCalorias.setText(initialCalories?.toString() ?: "")
        binding.dialogEditTextProteinas.setText(initialProteins?.toString() ?: "")
        binding.dialogEditTextCarbos.setText(initialCarbs?.toString() ?: "")
    }

    private fun setupButtons() {
        binding.dialogButtonCancel.setOnClickListener { dismiss() }
        binding.dialogButtonSave.setOnClickListener {
            Log.d(TAG, "Botón Guardar presionado.")
            saveChanges()
        }
    }

    private fun saveChanges() {
        // --- Lectura de campos (sin cambios) ---
        val nombre = binding.dialogEditTextNombre.text.toString().trim()
        val edadStr = binding.dialogEditTextEdad.text.toString().trim()
        val pesoStr = binding.dialogEditTextPeso.text.toString().trim()
        val alturaCmStr = binding.dialogEditTextAltura.text.toString().trim()
        val sexo = binding.dialogEditTextSexo.text.toString().trim()
        val caloriasStr = binding.dialogEditTextCalorias.text.toString().trim()
        val proteinasStr = binding.dialogEditTextProteinas.text.toString().trim()
        val carbosStr = binding.dialogEditTextCarbos.text.toString().trim()

        var isValid = true
        clearValidationErrors()

        // --- Validación (sin cambios funcionales, pero revisada) ---
        if (nombre.isEmpty()) {
            binding.dialogInputLayoutNombre.error = "Nombre obligatorio"; isValid = false
        }
        val edad = edadStr.toIntOrNull()
        if (edad == null && edadStr.isNotEmpty() || (edad != null && edad <= 0)) {
            binding.dialogInputLayoutEdad.error = "Edad inválida"; isValid = false
        }
        val peso = pesoStr.toDoubleOrNull()
        if (peso == null && pesoStr.isNotEmpty() || (peso != null && peso <= 0)) {
            binding.dialogInputLayoutPeso.error = "Peso inválido"; isValid = false
        }
        val alturaCm = alturaCmStr.toDoubleOrNull()
        if (alturaCm == null && alturaCmStr.isNotEmpty() || (alturaCm != null && alturaCm <= 0)) {
            binding.dialogInputLayoutAltura.error = "Altura inválida (cm)"; isValid = false
        }
        if (sexo.isEmpty() || !(sexo.equals("Masculino", true) || sexo.equals("Femenino", true))) {
            binding.dialogInputLayoutSexo.error = "Introduce 'Masculino' o 'Femenino'"; isValid =
                false
        }
        if (selectedObjetivoKey == null) {
            binding.dialogInputLayoutObjetivo.error = "Selecciona un objetivo"; isValid = false
        }
        if (selectedActividadKey == null) {
            binding.dialogInputLayoutNivelActividad.error =
                "Selecciona un nivel de actividad"; isValid = false
        }
        val calorias = if (caloriasStr.isNotEmpty()) caloriasStr.toIntOrNull() else null
        if (calorias == null && caloriasStr.isNotEmpty()) {
            binding.dialogInputLayoutCalorias.error = "Número inválido"; isValid = false
        }
        val proteinas = if (proteinasStr.isNotEmpty()) proteinasStr.toIntOrNull() else null
        if (proteinas == null && proteinasStr.isNotEmpty()) {
            binding.dialogInputLayoutProteinas.error = "Número inválido"; isValid = false
        }
        val carbos = if (carbosStr.isNotEmpty()) carbosStr.toIntOrNull() else null
        if (carbos == null && carbosStr.isNotEmpty()) {
            binding.dialogInputLayoutCarbos.error = "Número inválido"; isValid = false
        }
        // ---------------------------------------------------------

        if (!isValid) {
            Log.w(TAG, "Validación fallida en saveChanges.")
            Toast.makeText(context, "Por favor, corrige los errores.", Toast.LENGTH_SHORT).show()
            return
        }

        // --- Crear mapa con los datos actualizados ---
        val updatedData = hashMapOf<String, Any?>(
            "nombre" to nombre,
            "edad" to edad,
            "peso" to peso,
            "altura" to alturaCm, // Guardar CM
            "sexo" to sexo.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
            "objetivo" to selectedObjetivoKey,
            "nivelActividad" to selectedActividadKey,
            "caloriasDiarias" to calorias,
            "proteinasDiarias" to proteinas,
            "carboDiarios" to carbos
        )

        // --- Notificar al listener (HomeFragment) ---
        Log.d(TAG, "Enviando datos actualizados al listener: $updatedData")
        if (listener == null) {
            Log.e(TAG, "¡ERROR CRÍTICO! Listener es null al intentar guardar.")
            Toast.makeText(context, "Error interno al guardar (listener nulo)", Toast.LENGTH_LONG)
                .show()
            return // No continuar si no hay listener
        }
        listener?.onProfileSaved(updatedData)
        dismiss()
    }

    private fun clearValidationErrors() {
        binding.dialogInputLayoutNombre.error = null
        binding.dialogInputLayoutEdad.error = null
        binding.dialogInputLayoutPeso.error = null
        binding.dialogInputLayoutAltura.error = null
        binding.dialogInputLayoutSexo.error = null
        binding.dialogInputLayoutObjetivo.error = null
        binding.dialogInputLayoutNivelActividad.error = null
        binding.dialogInputLayoutCalorias.error = null
        binding.dialogInputLayoutProteinas.error = null
        binding.dialogInputLayoutCarbos.error = null
    }

    private fun showLoading(isLoading: Boolean) {
        _binding?.let {
            it.dialogProgressBar.isVisible = isLoading
            it.dialogButtonSave.isEnabled = !isLoading
            it.dialogButtonCancel.isEnabled = !isLoading
            it.dialogEditTextNombre.isEnabled = !isLoading
            it.dialogEditTextEdad.isEnabled = !isLoading
            it.dialogEditTextPeso.isEnabled = !isLoading
            it.dialogEditTextAltura.isEnabled = !isLoading
            it.dialogEditTextSexo.isEnabled = !isLoading
            it.dialogAutoCompleteObjetivo.isEnabled = !isLoading
            it.dialogAutoCompleteNivelActividad.isEnabled = !isLoading
            it.dialogEditTextCalorias.isEnabled = !isLoading
            it.dialogEditTextProteinas.isEnabled = !isLoading
            it.dialogEditTextCarbos.isEnabled = !isLoading
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView ejecutado.")
        _binding = null // Limpiar binding
    }
} // Fin de la clase