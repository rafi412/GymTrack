package com.example.gymtrack.ui.diet

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle // Importar repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
// Importar BuildConfig generado (asegúrate que la ruta es correcta)
import com.example.gymtrack.BuildConfig
import com.example.gymtrack.R
import com.example.gymtrack.databinding.FragmentDietaBinding
// Importar Adapter y Modelo de Comida (ajusta la ruta si es necesario)

import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.launch

// Importar el DialogFragment (ajusta la ruta si es necesario)
import kotlin.math.roundToInt


class DietaFragment : Fragment() { // No necesita implementar listeners de diálogo

    private var _binding: FragmentDietaBinding? = null
    private val binding get() = _binding!!

    private val dietaViewModel: DietaViewModel by activityViewModels()
    private val TAG = "DietaFragment"

    private lateinit var generativeModel: GenerativeModel
    private var isGeminiInitialized = false

    private lateinit var mealAdapter: MealAdapter // Declarar

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDietaBinding.inflate(inflater, container, false)
        // --- Inicializar Adapter aquí ---
        mealAdapter = MealAdapter(mutableListOf())
        // -----------------------------
        initializeGenerativeModel()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated")

        setupRecyclerView()
        setupUI() // Mover setupUI antes de setupObservers puede ser más claro
        setupObservers()

        // Cargar datos iniciales
        dietaViewModel.loadUserMacroTargets()
        dietaViewModel.loadTodayIntake()
    }

    private fun initializeGenerativeModel() {
        try {
            // --- Asegúrate que GEMINI_API_KEY está definido en build.gradle (app) ---
            val apiKey = BuildConfig.API_KEY
            // --------------------------------------------------------------------
            if (apiKey == "TU_CLAVE_API_AQUI" || apiKey.isBlank()) {
                throw IllegalStateException("API Key de Gemini no configurada.")
            }
            generativeModel = GenerativeModel(
                modelName = "gemini-1.5-flash-latest",
                apiKey = apiKey
            )
            isGeminiInitialized = true
            Log.d(TAG, "GenerativeModel inicializado.")
        } catch (e: Exception) {
            isGeminiInitialized = false
            Log.e(TAG, "Error inicializando GenerativeModel: ${e.message}")
            // No mostrar Toast aquí, dejar que la UI lo indique (botón deshabilitado)
        }
    }

    private fun setupRecyclerView() {
        binding.recyclerViewMeals.apply { // Usar ID correcto
            layoutManager = LinearLayoutManager(context)
            adapter = mealAdapter // Asignar adapter
            Log.d(TAG, "RecyclerView configurado y adapter asignado.")
        }
    }

    private fun setupUI() {
        // Listener para el FloatingActionButton
        binding.fabAddMeal.setOnClickListener { // Asume ID fab_add_meal en fragment_dieta.xml
            Log.d(TAG, "FAB Añadir Comida presionado.")
            // Crear y mostrar el diálogo para añadir comida
            AddMealDialogFragment.newInstance()
                .show(parentFragmentManager, "AddMealDialog")
        }
        binding.buttonClearMeals.setOnClickListener {
            showClearConfirmationDialog()
        }
    }

    private fun showClearConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Limpiar Comidas")
            .setMessage("¿Estás seguro de que quieres eliminar todas las comidas registradas hoy? Esta acción no se puede deshacer.")
            .setIcon(R.drawable.baseline_warning_24)
            .setPositiveButton("Limpiar") { dialog, _ ->
                dietaViewModel.clearTodayMeals() // Llamar al ViewModel
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // --- setupObservers REFACTORIZADO ---
    private fun setupObservers() {
        // Usar repeatOnLifecycle para observar de forma segura
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // Observar isLoadingTargets
                dietaViewModel.isLoadingTargets.observe(viewLifecycleOwner) { isLoading ->
                    // Mostrar/ocultar ProgressBar general o uno específico para metas
                    // Solo mostrar si no está cargando la IA para evitar parpadeo
                    if (!(dietaViewModel.isLoadingMacros.value ?: false)) {
                        binding.progressBarDieta.isVisible = isLoading
                    }
                    Log.d(TAG, "Loading Metas State: $isLoading")
                }

                // Observar isLoadingMacros (IA)
                dietaViewModel.isLoadingMacros.observe(viewLifecycleOwner) { isLoading ->
                    binding.progressBarDieta.isVisible = isLoading // La carga de IA tiene prioridad visual
                    binding.fabAddMeal.isEnabled = !isLoading // Habilitar/Deshabilitar FAB
                    Log.d(TAG, "Loading Macros IA State: $isLoading")
                }

                // Observar Metas
                dietaViewModel.macroTargets.observe(viewLifecycleOwner) { targets ->
                    val intake = dietaViewModel.dailyIntake.value // Obtener valor actual
                    handleTargetOrIntakeChange(targets, intake) // Actualizar UI
                }

                // Observar Consumo Diario
                dietaViewModel.dailyIntake.observe(viewLifecycleOwner) { intake -> // Este debería reaccionar ahora
                    Log.d(TAG, ">>> Observador dailyIntake RECIBIÓ (con setValue): $intake") // Verifica este log
                    val targets = dietaViewModel.macroTargets.value
                    handleTargetOrIntakeChange(targets, intake)
                }

                // Observar Lista de Comidas
                dietaViewModel.mealsToday.observe(viewLifecycleOwner) { meals ->
                    Log.d(TAG, "Lista de comidas actualizada: ${meals.size} comidas")
                    mealAdapter.updateData(meals) // Actualizar adapter
                    val hasMeals = meals.isNotEmpty()
                    binding.recyclerViewMeals.isVisible = hasMeals // Mostrar recycler si HAY comidas
                    // Mostrar/ocultar mensaje "sin comidas" usando text_dieta_message
                    if (!hasMeals && dietaViewModel.errorMessage.value.isNullOrBlank()) {
                        binding.textDietaMessage.text = "No has registrado comidas hoy."
                        binding.textDietaMessage.isVisible = true
                    } else if (hasMeals && binding.textDietaMessage.text == "No has registrado comidas hoy.") {
                        // Ocultar solo si el mensaje actual es el de "sin comidas"
                        binding.textDietaMessage.isVisible = false
                    }
                }

                // Observar mensajes de error
                dietaViewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
                    if (!errorMessage.isNullOrBlank()) {
                        Log.e(TAG, "Error recibido del ViewModel: $errorMessage")
                        binding.textDietaMessage.text = errorMessage // Mostrar error
                        binding.textDietaMessage.isVisible = true
                        clearProgressUi() // Ocultar barras en error
                        binding.recyclerViewMeals.isVisible = false // Ocultar lista en error
                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                        dietaViewModel.clearErrorMessage() // Limpiar error después de mostrarlo
                    }
                    // No necesitamos un 'else' aquí, handleTargetOrIntakeChange y el observer de mealsToday
                    // se encargarán de la visibilidad del mensaje cuando no hay error.
                }
            } // Fin repeatOnLifecycle
        } // Fin lifecycleScope.launch
    }
    // ---------------------------------

    // Función centralizada para actualizar UI (sin cambios funcionales, revisada)
    private fun handleTargetOrIntakeChange(targets: MacroTargetsUi?, intake: DailyIntakeUi?) {
        _binding?.let { binding ->
            val hasMeals = !(dietaViewModel.mealsToday.value.isNullOrEmpty())

            if (targets != null && intake != null) {
                Log.d(TAG, "Actualizando progreso con Metas=$targets, Consumo=$intake")
                updateProgress(targets, intake.calories, intake.protein, intake.carbs)
                // Ocultar mensaje solo si NO es un mensaje de error existente
                if (binding.textDietaMessage.text == "No has registrado comidas hoy." || binding.textDietaMessage.text == "Completa tu perfil para ver metas."){
                    binding.textDietaMessage.isVisible = !hasMeals // Ocultar si hay comidas
                }
                binding.groupMacroProgressVisibility.isVisible = true
            } else if (targets == null) {
                Log.d(TAG, "No hay metas válidas, limpiando UI.")
                clearProgressUi()
                // Mostrar mensaje si no hay un error ya presente
                if (dietaViewModel.errorMessage.value.isNullOrBlank()) {
                    binding.textDietaMessage.text = "Completa tu perfil para ver metas."
                    binding.textDietaMessage.isVisible = true
                }
            }
            // El caso de 'Hay metas pero no comidas' se maneja implícitamente arriba
            // y en el observer de mealsToday
        }
    }


    // updateProgress (sin cambios internos)
     fun updateProgress(targets: MacroTargetsUi, consumedCalories: Int, consumedProtein: Int, consumedCarbs: Int) {
        _binding?.let { binding ->
            /* ... (cálculo de porcentajes) ... */
            val calPercent = if (targets.calories > 0) (consumedCalories * 100.0 / targets.calories).roundToInt() else 0
            val protPercent = if (targets.protein > 0) (consumedProtein * 100.0 / targets.protein).roundToInt() else 0
            val carbPercent = if (targets.carbs > 0) (consumedCarbs * 100.0 / targets.carbs).roundToInt() else 0

            /* ... (actualización de TextViews) ... */
            binding.textCaloriesProgress.text = "$consumedCalories / ${targets.calories} kcal"
            binding.textProteinProgress.text = "$consumedProtein / ${targets.protein} g"
            binding.textCarbsProgress.text = "$consumedCarbs / ${targets.carbs} g"

            /* ... (actualización de ProgressBars) ... */
            binding.progressBarCalories.progress = calPercent.coerceAtMost(100)
            binding.progressBarProtein.progress = protPercent.coerceAtMost(100)
            binding.progressBarCarbs.progress = carbPercent.coerceAtMost(100)

            /* ... (visibilidad del grupo) ... */
            binding.groupMacroProgressVisibility.isVisible = targets.calories > 0

            Log.d(TAG, "UI de progreso actualizada.")
        } ?: Log.w(TAG, "updateProgress llamada pero _binding es null")
    }

    // clearProgressUi (sin cambios)
    private fun clearProgressUi() {
        _binding?.let {
            it.textCaloriesProgress.text = ""
            it.textProteinProgress.text = ""
            it.textCarbsProgress.text = ""
            it.progressBarCalories.progress = 0
            it.progressBarProtein.progress = 0
            it.progressBarCarbs.progress = 0
            it.groupMacroProgressVisibility.isVisible = false
        }
    }

    private fun hideKeyboard() {
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView")
        _binding = null
    }
}