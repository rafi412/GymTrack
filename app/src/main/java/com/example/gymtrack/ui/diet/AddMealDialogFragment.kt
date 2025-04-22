package com.example.gymtrack.ui.diet // O tu paquete

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.gymtrack.R
import com.example.gymtrack.databinding.DialogAddMealBinding
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException


// Ya no necesitamos un listener específico si el ViewModel maneja todo
// interface MealAdditionListener { ... }

class AddMealDialogFragment : DialogFragment() {

    private var _binding: DialogAddMealBinding? = null
    private val binding get() = _binding!!

    private val dietaViewModel: DietaViewModel by activityViewModels()
    private lateinit var generativeModel: GenerativeModel
    private var isGeminiInitialized = false

    private var selectedMealTitle: String? = null
    private val TAG = "AddMealDialog"

    // Parser JSON para la respuesta de IA (si el parseo se hace aquí)
    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

    companion object {
        fun newInstance(): AddMealDialogFragment {
            return AddMealDialogFragment()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, ">>> AddMealDialogFragment onCreate INICIO") // LOG INICIO onCreate
        super.onCreate(savedInstanceState)
        initializeGenerativeModel()
        Log.d(TAG, ">>> AddMealDialogFragment onCreate FIN") // LOG FIN onCreate (¿llega aquí?)
        // ... resto de onCreate ...
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Log.d(TAG, "onCreateView - Intentando inflar DialogAddMealBinding...")
        Log.d(TAG, "  -> Inflater: $inflater")
        Log.d(TAG, "  -> Container: $container")
        try {
            _binding = DialogAddMealBinding.inflate(inflater, container, false)
            Log.d(TAG, "onCreateView - Binding inflado CORRECTAMENTE.")
        } catch (e: Exception) {
            Log.e(TAG, "¡¡¡ERROR CRÍTICO AL INFLAR DialogAddMealBinding!!!", e)
            // Intenta inflar un layout mínimo de emergencia o devuelve una vista vacía
            return TextView(requireContext()).apply { text = "Error al cargar layout" }
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMealTitleDropdown()
        setupButtons()
        // No necesitamos observar aquí si el ViewModel no nos devuelve la estimación directamente
        // observeViewModel()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun initializeGenerativeModel() {
        Log.d(TAG, ">>> initializeGenerativeModel INICIO") // LOG INICIO Init
        try {
            val apiKey = com.example.gymtrack.BuildConfig.API_KEY
            Log.d(TAG, "API Key leída: ${apiKey.isNotEmpty()}") // Log si la clave no está vacía
            if (apiKey.isBlank() || apiKey == "TU_CLAVE_API_AQUI") {
                Log.e(TAG, "API Key inválida o no configurada.")
                throw IllegalStateException("API Key no configurada")
            }
            generativeModel = GenerativeModel(modelName = "gemini-1.5-pro-latest", apiKey = apiKey)
            isGeminiInitialized = true
            Log.d(TAG, "GenerativeModel inicializado con éxito.") // LOG ÉXITO Init
        } catch (e: Exception) {
            isGeminiInitialized = false
            Log.e(TAG, ">>> ERROR en initializeGenerativeModel", e) // LOG ERROR Init
            // Considera NO lanzar Toast aquí, puede ser muy temprano
        }
    }

    private fun setupMealTitleDropdown() {
        val titles = resources.getStringArray(R.array.meal_title_options)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, titles)
        binding.autoCompleteMealTitle.setAdapter(adapter)
        binding.autoCompleteMealTitle.setOnItemClickListener { parent, _, position, _ ->
            selectedMealTitle = parent.getItemAtPosition(position) as String
            binding.inputLayoutMealTitle.error = null
        }
    }

    private fun setupButtons() {
        binding.dialogMealButtonCancel.setOnClickListener { dismiss() }

        // Botón Estimar Macros
        binding.buttonEstimateMacros.isEnabled = isGeminiInitialized // Habilitar solo si IA está lista
        binding.buttonEstimateMacros.setOnClickListener {
            val description = binding.editTextMealDescription.text.toString().trim()
            if (description.isNotEmpty()) {
                estimateMacrosWithIA(description) // Llama a la función local de estimación
            } else {
                binding.inputLayoutMealDescription.error = "Describe la comida primero"
            }
        }

        // Botón Añadir Comida
        binding.dialogMealButtonAdd.setOnClickListener {
            addMealToJournal() // Llama a la función que valida y guarda
        }
    }

    // --- Función para llamar a IA y rellenar campos ---
    private fun estimateMacrosWithIA(description: String) {
        showLoading(true) // Mostrar loading específico de IA
        binding.inputLayoutMealDescription.error = null // Limpiar error

        val prompt = """
            Estima los macronutrientes (calorías, proteínas en gramos, carbohidratos en gramos, grasas en gramos) para: "$description".
            Responde ÚNICAMENTE con un objeto JSON válido con claves "calories"(Int), "proteinGrams"(Int), "carbGrams"(Int), "fatGrams"(Int).
        """.trimIndent()

        // Usar lifecycleScope del DialogFragment para la coroutine
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Enviando prompt a Gemini...")
                val response = generativeModel.generateContent(prompt)
                val jsonResponse = response.text
                Log.d(TAG, "Respuesta de Gemini: $jsonResponse")

                if (jsonResponse != null) {
                    parseAndPopulateFields(jsonResponse) // Parsea y rellena los EditText
                } else {
                    Toast.makeText(context, "La IA no devolvió respuesta.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error llamando a Gemini", e)
                Toast.makeText(context, "Error al estimar: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                showLoading(false) // Ocultar loading
            }
        }
    }
    // -------------------------------------------------

    // --- Parsea la respuesta y rellena los EditText ---
    private fun parseAndPopulateFields(jsonString: String) {
        try {
            val cleanJson = jsonString.trim().removeSurrounding("```json\n", "\n```").removeSurrounding("```", "```")
            val estimate = jsonParser.decodeFromString<MacroEstimate>(cleanJson) // Usa DTO
            Log.d(TAG, "Macros parseados: $estimate")

            // Rellenar los EditText con los valores estimados
            binding.editTextMealCalories.setText(estimate.calories?.toString() ?: "")
            binding.editTextMealProtein.setText(estimate.proteinGrams?.toString() ?: "")
            binding.editTextMealCarbs.setText(estimate.carbGrams?.toString() ?: "")

            Toast.makeText(context, "Macros estimados. Puedes ajustarlos.", Toast.LENGTH_SHORT).show()

        } catch (e: SerializationException) {
            Log.e(TAG, "Error al parsear JSON de Gemini: ${e.message}")
            Log.e(TAG, "JSON recibido: $jsonString")
            Toast.makeText(context, "Error al procesar respuesta IA.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error inesperado al parsear/rellenar: ${e.message}")
            Toast.makeText(context, "Error al procesar respuesta.", Toast.LENGTH_SHORT).show()
        }
    }
    // -----------------------------------------------


    // Añade la comida al registro (llamando al ViewModel)
    private fun addMealToJournal() {
        val title = selectedMealTitle
        val description = binding.editTextMealDescription.text.toString().trim()
        // --- Leer valores FINALES de los EditText ---
        val caloriesStr = binding.editTextMealCalories.text.toString().trim()
        val proteinStr = binding.editTextMealProtein.text.toString().trim()
        val carbsStr = binding.editTextMealCarbs.text.toString().trim()
        // ------------------------------------------

        // --- Validación (igual que antes) ---
        var isValid = true
        clearValidationErrors() // Limpiar errores previos
        if (title.isNullOrBlank()) { binding.inputLayoutMealTitle.error = "Selecciona un tipo"; isValid = false }
        val calories = caloriesStr.toIntOrNull()
        if (calories == null && caloriesStr.isNotEmpty() || (calories !=null && calories < 0)) { binding.inputLayoutMealCalories.error = "Inválido"; isValid = false }
        val protein = proteinStr.toIntOrNull()
        if (protein == null && proteinStr.isNotEmpty() || (protein !=null && protein < 0)) { binding.inputLayoutMealProtein.error = "Inválido"; isValid = false }
        val carbs = carbsStr.toIntOrNull()
        if (carbs == null && carbsStr.isNotEmpty() || (carbs !=null && carbs < 0)) { binding.inputLayoutMealCarbs.error = "Inválido"; isValid = false }
        if ((calories ?: 0) <= 0 && (protein ?: 0) <= 0 && (carbs ?: 0) <= 0) {
            Toast.makeText(context, "Introduce al menos un valor nutricional.", Toast.LENGTH_LONG).show()
            isValid = false
        }
        if (!isValid) return
        // ------------------------------------

        // Crear objeto MealData con los datos FINALES de los EditText
        val meal = MealData(
            title = title,
            description = description.ifEmpty { null },
            calories = calories ?: 0,
            proteinGrams = protein ?: 0,
            carbGrams = carbs ?: 0
        )

        // Llamar al ViewModel para guardar y actualizar
        dietaViewModel.addMealAndUpdateIntake(meal)

        dismiss() // Cerrar diálogo
    }

    // Muestra/oculta loading específico para la IA
    private fun showLoading(isLoading: Boolean) {
        binding.dialogMealProgressBar.isVisible = isLoading
        binding.buttonEstimateMacros.isEnabled = !isLoading && isGeminiInitialized // Habilitar/Deshabilitar
        binding.dialogMealButtonAdd.isEnabled = !isLoading // Deshabilitar añadir mientras estima/guarda
        binding.dialogMealButtonCancel.isEnabled = !isLoading
        // Puedes deshabilitar también los EditText si lo deseas
        // binding.editTextMealDescription.isEnabled = !isLoading
        // binding.editTextMealCalories.isEnabled = !isLoading ... etc
    }

    // Limpia errores de validación
    private fun clearValidationErrors() {
        binding.inputLayoutMealTitle.error = null
        binding.inputLayoutMealDescription.error = null
        binding.inputLayoutMealCalories.error = null
        binding.inputLayoutMealProtein.error = null
        binding.inputLayoutMealCarbs.error = null
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}