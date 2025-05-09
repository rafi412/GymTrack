package com.example.gymtrack.ui.user

import User
import android.content.Intent // Necesario para LoginActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.gymtrack.LoginActivity // Necesario para redirigir
import com.example.gymtrack.R
import com.example.gymtrack.databinding.FragmentHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser // Importar FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import com.bumptech.glide.Glide // <-- Importa Glide


class HomeFragment : Fragment(), ProfileEditListener {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth // Cambiado a auth para consistencia
    private var currentUser: FirebaseUser? = null // Para guardar el usuario de Firebase Auth
    private val TAG = "HomeFragment"

    private var currentUserProfile: User? = null // Para guardar los datos de Firestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        db = Firebase.firestore
        auth = Firebase.auth // Usar la instancia auth
        currentUser = auth.currentUser // Obtener el usuario actual aquí
        Log.d(TAG, "onCreateView called")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated called")

        // Comprobar usuario aquí también por si acaso
        if (currentUser == null) {
            handleUserNotLoggedIn()
            return
        }

        // Ocultar elementos hasta que carguen los datos
        binding.profileView.isVisible = false
        loadUserProfile() // Carga los datos del perfil de Firestore
    }

    private fun loadUserProfile() {
        showProgressBar(true) // Mostrar ProgressBar
        //binding.textHome.text = "Cargando perfil..." // No necesario si ProgressBar es visible
        binding.profileView.isVisible = true

        val userId = currentUser?.uid // Usar el currentUser obtenido en onCreateView
        if (userId == null) {
            handleUserNotLoggedIn()
            showProgressBar(false) // Ocultar si fallamos rápido
            return
        }

        Log.d(TAG, "Cargando perfil para usuario: $userId")
        val userRef = db.collection("users").document(userId)

        userRef.get()
            .addOnSuccessListener { document ->
                showProgressBar(false) // Ocultar ProgressBar
                if (document != null && document.exists()) {
                    try {
                        // Guardar el perfil de Firestore en currentUserProfile
                        currentUserProfile = document.toObject<User>()
                        if (currentUserProfile != null) {
                            Log.d(TAG, "Perfil cargado de Firestore: $currentUserProfile")
                            // 1. Mostrar datos básicos del perfil
                            displayUserData()
                            // 2. Calcular y mostrar/guardar metas (si los datos necesarios están)
                            calculateAndSetMacroGoals()
                        } else {
                            // Error al convertir el documento
                            handleProfileConversionError("Error al procesar datos del perfil.")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Excepción al convertir documento a User", e)
                        handleProfileConversionError("Error de formato en datos: ${e.localizedMessage}")
                    }
                } else {
                    // El documento del usuario no existe en Firestore
                    handleProfileNotFoundError(userId)
                }
            }
            .addOnFailureListener { exception ->
                showProgressBar(false) // Ocultar ProgressBar
                Log.e(TAG, "Error al obtener el perfil del usuario desde Firestore", exception)
                // Mostrar error en el TextView principal
                binding.usernameTextView.text = "Error al cargar el perfil: ${exception.message}"
                binding.profileView.isVisible = true
                Toast.makeText(context, "Error al cargar datos.", Toast.LENGTH_SHORT).show()
            }
    }


    private fun displayUserData() {
        val user = currentUserProfile // Tu objeto de perfil personalizado de Firestore
        val firebaseUser = auth.currentUser // El usuario de Firebase Auth

        if (user != null && firebaseUser != null) { // Verifica ambos usuarios
            Log.d(TAG, "Mostrando datos del usuario: $user")
            Log.d(
                TAG,
                "Usuario de Firebase Auth: ${firebaseUser.uid}, Email: ${firebaseUser.email}, PhotoURL: ${firebaseUser.photoUrl}"
            )

            // --- Cargar Imagen de Perfil de Google ---
            val photoUri = firebaseUser.photoUrl // Obtiene la Uri de la foto
            if (photoUri != null) {
                Log.d(TAG, "Cargando foto de perfil desde URL: $photoUri")
                Glide.with(this)
                    .load(photoUri) // Carga la Uri directamente
                    .error(R.drawable.ic_user_24dp) // Imagen si hay error (opcional)
                    .into(binding.profileImageView) // El ImageView destino
            } else {
                // Si no hay URL de foto, muestra el placeholder
                Log.d(TAG, "El usuario no tiene foto de perfil en Firebase Auth.")
                binding.profileImageView.setImageResource(R.drawable.ic_user_24dp)
            }

            binding.usernameTextView.text =
                "Bienvenido, ${user.nombre?.takeIf { it.isNotBlank() } ?: firebaseUser.displayName ?: "Usuario"}!" // Puedes usar displayName de Firebase como fallback
            binding.emailTextView.text =
                firebaseUser.email ?: "N/A" // Mejor obtener el email de firebaseUser
            binding.ageTextView.text = user.edad?.toString()?.takeIf { it.isNotBlank() }
                ?.let { "$it años" } ?: "N/A"
            binding.weightTextView.text = user.peso?.toString()?.takeIf { it.isNotBlank() }
                ?.let { "$it kg" } ?: "N/A"
            binding.heightTextView.text = user.altura?.toString()?.takeIf { it.isNotBlank() }
                ?.let { "$it cm" } ?: "N/A"
            binding.sexTextView.text = user.sexo?.takeIf { it.isNotBlank() } ?: "N/A"
            binding.objectiveTextView.text = user.objetivo?.takeIf { it.isNotBlank() } ?: "N/A"

        } else {
            Log.w(TAG, "Intento de mostrar datos con currentUserProfile o firebaseUser null")
            // Muestra estado de error o vacío
            binding.usernameTextView.text = "Perfil no disponible"
            binding.emailTextView.text = "N/A"
            binding.ageTextView.text = "N/A"
            binding.weightTextView.text = "N/A"
            binding.heightTextView.text = "N/A"
            binding.sexTextView.text = "N/A"
            binding.objectiveTextView.text = "N/A"
            binding.profileImageView.setImageResource(R.drawable.ic_user_24dp)
        }
    }

    // Calcula las metas y las añade al TextView y opcionalmente guarda en Firestore
    private fun calculateAndSetMacroGoals() {
        val user = currentUserProfile // Usa el perfil cargado de Firestore
        val userId = currentUser?.uid

        if (user == null || userId == null) {
            Log.w(TAG, "No se pueden calcular metas: faltan datos de usuario.")
            // Añadir mensaje a la UI indicando que faltan datos
            binding.usernameTextView.append("\n\nMetas Diarias: Completa tu perfil para calcular.")
            return
        }

        // --- Obtener datos para el cálculo ---
        val age = user.edad
        val sex = user.sexo
        val weightKg = user.peso
        val heightCm = user.altura // ASUME QUE ESTÁ EN CM
        val activityLevel = user.nivelActividad // Clave como "MODERADO"
        val goal = user.objetivo        // Clave como "MANTENER"

        // Validar que tenemos los datos necesarios ANTES de calcular
        if (age == null || sex == null || weightKg == null || heightCm == null || activityLevel == null || goal == null) {
            Log.w(
                "MacroGoals",
                "Faltan datos en el perfil para calcular macros (edad, sexo, peso, altura, actividad, objetivo)."
            )
            binding.usernameTextView.append("\n\nMetas Diarias: Completa tu perfil (edad, sexo, peso, altura, actividad, objetivo) para calcular.")
            return
        }

        // --- Llamar a la calculadora ---
        val calculatedGoals = MacronutrientCalculator.calculateGoals(
            age = age, sex = sex, weightKg = weightKg, heightCm = heightCm,
            activityLevelKey = activityLevel, goalKey = goal
        )

        if (calculatedGoals != null) {
            Log.i(
                "MacroGoals",
                "Metas Calculadas: C:${calculatedGoals.calories}, P:${calculatedGoals.proteinGrams}, C:${calculatedGoals.carbGrams}"
            )

            // --- ACTUALIZAR currentUserProfile CON METAS CALCULADAS ---
             currentUserProfile = currentUserProfile?.copy(
                 caloriasDiarias = calculatedGoals.calories,
                 proteinasDiarias = calculatedGoals.proteinGrams,
                 carboDiarios = calculatedGoals.carbGrams
             )

            // --- AÑADIR METAS AL TEXTVIEW EXISTENTE ---
            // ---------------------------------------------------

            // --- OPCIONAL: Guardar metas calculadas en Firestore ---
            val userDocRef = db.collection("users").document(userId)
            val metaUpdates = mapOf(
                "caloriasDiarias" to calculatedGoals.calories,
                "proteinasDiarias" to calculatedGoals.proteinGrams,
                "carboDiarios" to calculatedGoals.carbGrams
                // Podrías añadir "grasasDiaras" si tienes ese campo
            )
            userDocRef.update(metaUpdates)
                .addOnSuccessListener {
                    Log.d(
                        "MacroGoals",
                        "Metas calculadas guardadas en Firestore."
                    )
                }
                .addOnFailureListener { e ->
                    Log.e(
                        "MacroGoals",
                        "Error al guardar metas calculadas",
                        e
                    )
                }
            // ----------------------------------------------------

        } else {
            Log.e(
                "MacroGoals",
                "El cálculo de metas falló (devolvió null). Revisa logs de MacroCalc."
            )
            Toast.makeText(context, "No se pudieron calcular las metas.", Toast.LENGTH_SHORT).show()
            binding.usernameTextView.append("\n\nMetas Diarias: No se pudieron calcular.")
        }
    }

    // --- Implementación de la interfaz ProfileEditListener ---
    override fun onProfileSaved(updatedData: Map<String, Any?>) {
        Log.d(TAG, "Datos básicos recibidos del diálogo: $updatedData")
        // 1. Guardar datos básicos en Firestore Y actualizar perfil local
        updateFirestoreProfile(updatedData) // Esta función ahora debe actualizar también currentUserProfile
    }

    // --- updateFirestoreProfile ACTUALIZADA ---
    private fun updateFirestoreProfile(updates: Map<String, Any?>) { // 'updates' viene del diálogo
        val userId = auth.currentUser?.uid // Cambiado a auth
        if (userId == null) {
            Toast.makeText(context, "Error: Usuario no identificado.", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "updateFirestoreProfile RECIBIENDO: $updates")

        showProgressBar(true) // Mostrar progreso mientras guarda
        val userRef = db.collection("users").document(userId)

        // --- OPERACIÓN DE ACTUALIZACIÓN ---
        userRef.update(updates)
            .addOnSuccessListener {
                showProgressBar(false) // Ocultar al éxito
                Log.d(TAG, "Perfil de usuario actualizado correctamente en Firestore.")
                Toast.makeText(context, "Perfil actualizado desde homefragment", Toast.LENGTH_SHORT)
                    .show()

                // --- Actualizar perfil local DESPUÉS de guardar en Firestore ---
                updateLocalProfile(updates)

                // --- Recalcular y mostrar/guardar metas AHORA ---
                calculateAndSetMacroGoals() // Usa el currentUserProfile actualizado

            }
            .addOnFailureListener { e ->
                showProgressBar(false) // Ocultar en caso de error
                Log.e(TAG, "Error al actualizar el perfil en Firestore", e)
                Toast.makeText(context, "Error al guardar: ${e.message}", Toast.LENGTH_LONG).show()
            }
        // ---------------------------------
    }

    // --- updateLocalProfile ACTUALIZADA ---
    private fun updateLocalProfile(updates: Map<String, Any?>) {
        currentUserProfile = currentUserProfile?.copy(
            nombre = updates["nombre"] as? String ?: currentUserProfile?.nombre,
            // Firestore devuelve números enteros como Long, castear a Int si es necesario
            edad = (updates["edad"] as? Long)?.toInt() ?: currentUserProfile?.edad,
            peso = updates["peso"] as? Double ?: currentUserProfile?.peso,
            altura = updates["altura"] as? Double ?: currentUserProfile?.altura, // Asume CM
            sexo = updates["sexo"] as? String ?: currentUserProfile?.sexo,
            objetivo = updates["objetivo"] as? String ?: currentUserProfile?.objetivo,
            nivelActividad = updates["nivelActividad"] as? String
                ?: currentUserProfile?.nivelActividad
        )
        Log.d(TAG, "Perfil local (básico) actualizado: $currentUserProfile")
    }

    // --- Funciones de manejo de errores y UI ---
    private fun handleUserNotLoggedIn() {
        Log.w(TAG, "Usuario no logueado detectado. Redirigiendo a Login.")
        Toast.makeText(context, "Sesión no iniciada.", Toast.LENGTH_SHORT).show()
        val intent = Intent(activity, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        activity?.finish() // Finalizar la Activity contenedora
    }

    private fun handleProfileConversionError(message: String = "Error al leer datos del perfil.") {
        Log.w(TAG, message)
        binding.usernameTextView.text = message
        binding.profileView.isVisible = true
    }

    private fun handleProfileNotFoundError(userId: String) {
        Log.w(TAG, "Documento de usuario no encontrado para UID: $userId")
        binding.usernameTextView.text =
            "Perfil no encontrado. Completa tu perfil o contacta soporte."
        binding.profileView.isVisible = true
        // val intent = Intent(activity, ProfileSetupActivity::class.java)
        // startActivity(intent)
    }

    // showProgressBar ahora solo toma boolean
    fun showProgressBar(isLoading: Boolean) {
        binding.progressBarHome.isVisible = isLoading
        // Opcional: Deshabilitar botón editar mientras carga
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView called")
        _binding = null // Limpiar binding
    }
}