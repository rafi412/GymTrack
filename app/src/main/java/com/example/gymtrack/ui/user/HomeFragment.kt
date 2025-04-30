package com.example.gymtrack.ui.user

// Importa tu clase User (asegúrate que la ruta es correcta)
import User
// Importa tu clase de cálculo (asegúrate que la ruta es correcta)
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
// Importa el DialogFragment y su Listener (asegúrate que las rutas son correctas)
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser // Importar FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import com.bumptech.glide.Glide // <-- Importa Glide

// Implementa la interfaz del DialogFragment
class HomeFragment : Fragment(), ProfileEditListener { // Implementa la interfaz correcta

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

        setupEditButtonListener()

        // Ocultar elementos hasta que carguen los datos
        binding.profileView.isVisible = false
        binding.editProfileButton.isVisible = false
        loadUserProfile() // Carga los datos del perfil de Firestore
    }

    private fun setupEditButtonListener() {
        binding.editProfileButton.setOnClickListener {
            Log.d(TAG, "Edit button clicked")
            // Usar el perfil cargado de Firestore (currentUserProfile)
            currentUserProfile?.let { userProfile ->
                // Calcular metas ANTES de abrir el diálogo, usando los datos del perfil
                val calculatedGoals = MacronutrientCalculator.calculateGoals(
                    age = userProfile.edad,
                    sex = userProfile.sexo,
                    weightKg = userProfile.peso,
                    heightCm = userProfile.altura, // ASUME QUE userProfile.altura ESTÁ EN CM
                    activityLevelKey = userProfile.nivelActividad ?: "MODERADO",
                    goalKey = userProfile.objetivo ?: "MANTENER"
                )
                Log.d(TAG, "Metas calculadas para pasar al diálogo: $calculatedGoals")

                // Crear y mostrar el diálogo, pasando el objeto User de Firestore
                val dialogFragment = EditProfileDialogFragment.newInstance(userProfile, calculatedGoals)
                Log.d(TAG, ">>> LLAMANDO a dialog.show() con ${parentFragmentManager}")
                dialogFragment.setTargetFragment(this, 0)
                dialogFragment.show(parentFragmentManager, "EditProfileDialog")

            } ?: run {
                Log.w(TAG, "currentUserProfile es null, no se puede abrir diálogo.")
                Toast.makeText(context, "Datos del perfil aún no cargados.", Toast.LENGTH_SHORT).show()
            }
        }
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
                            // 3. Hacer visible el botón de editar
                            binding.editProfileButton.isVisible = true
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
                binding.editProfileButton.isVisible = false // No permitir editar si falla la carga
                Toast.makeText(context, "Error al cargar datos.", Toast.LENGTH_SHORT).show()
            }
    }

// ... dentro de tu Fragment o Activity

    private fun displayUserData() {
        val user = currentUserProfile // Tu objeto de perfil personalizado de Firestore
        val firebaseUser = auth.currentUser // El usuario de Firebase Auth

        if (user != null && firebaseUser != null) { // Verifica ambos usuarios
            Log.d(TAG, "Mostrando datos del usuario: $user")
            Log.d(TAG, "Usuario de Firebase Auth: ${firebaseUser.uid}, Email: ${firebaseUser.email}, PhotoURL: ${firebaseUser.photoUrl}")

            // --- Cargar Imagen de Perfil de Google ---
            val photoUri = firebaseUser.photoUrl // Obtiene la Uri de la foto
            if (photoUri != null) {
                Log.d(TAG, "Cargando foto de perfil desde URL: $photoUri")
                Glide.with(this) // 'this' si estás en un Fragment o Activity
                    .load(photoUri) // Carga la Uri directamente
                    .error(R.drawable.ic_user_24dp) // Imagen si hay error (opcional)
                    // .circleCrop() // Opcional: si tu ImageView no es ShapeableImageView o no está configurada como círculo en XML
                    .into(binding.profileImageView) // El ImageView destino
            } else {
                // Si no hay URL de foto, muestra el placeholder
                Log.d(TAG, "El usuario no tiene foto de perfil en Firebase Auth.")
                binding.profileImageView.setImageResource(R.drawable.ic_user_24dp)
            }
            // --- Fin Cargar Imagen de Perfil ---

            // --- Rellenar los TextViews (como en la respuesta anterior) ---
            binding.usernameTextView.text = "Bienvenido, ${user.nombre?.takeIf { it.isNotBlank() } ?: firebaseUser.displayName ?: "Usuario"}!" // Puedes usar displayName de Firebase como fallback
            binding.emailTextView.text = firebaseUser.email ?: "N/A" // Mejor obtener el email de firebaseUser
            binding.ageTextView.text = user.edad?.toString()?.takeIf { it.isNotBlank() }
                ?.let { "$it años" } ?: "N/A"
            binding.weightTextView.text = user.peso?.toString()?.takeIf { it.isNotBlank() }
                ?.let { "$it kg" } ?: "N/A"
            binding.heightTextView.text = user.altura?.toString()?.takeIf { it.isNotBlank() }
                ?.let { "$it cm" } ?: "N/A"
            binding.sexTextView.text = user.sexo?.takeIf { it.isNotBlank() } ?: "N/A"
            binding.objectiveTextView.text = user.objetivo?.takeIf { it.isNotBlank() } ?: "N/A"
            // Asegúrate de tener activityLevelTextView en tu XML si lo usas
            // --- Fin Rellenar TextViews ---

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
            Log.w("MacroGoals", "Faltan datos en el perfil para calcular macros (edad, sexo, peso, altura, actividad, objetivo).")
            binding.usernameTextView.append("\n\nMetas Diarias: Completa tu perfil (edad, sexo, peso, altura, actividad, objetivo) para calcular.")
            return
        }

        // --- Llamar a la calculadora ---
        val calculatedGoals = MacronutrientCalculator.calculateGoals(
            age = age, sex = sex, weightKg = weightKg, heightCm = heightCm,
            activityLevelKey = activityLevel, goalKey = goal
        )

        if (calculatedGoals != null) {
            Log.i("MacroGoals", "Metas Calculadas: C:${calculatedGoals.calories}, P:${calculatedGoals.proteinGrams}, C:${calculatedGoals.carbGrams}")
            Toast.makeText(context, "Metas diarias calculadas", Toast.LENGTH_SHORT).show()

            // --- ACTUALIZAR currentUserProfile CON METAS CALCULADAS ---
            // Esto es opcional si no necesitas los valores calculados en el objeto local
            // currentUserProfile = currentUserProfile?.copy(
            //     caloriasDiarias = calculatedGoals.calories,
            //     proteinasDiarias = calculatedGoals.proteinGrams,
            //     carboDiarios = calculatedGoals.carbGrams
            // )
            // -------------------------------------------------------

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
                .addOnSuccessListener { Log.d("MacroGoals", "Metas calculadas guardadas en Firestore.") }
                .addOnFailureListener { e -> Log.e("MacroGoals", "Error al guardar metas calculadas", e) }
            // ----------------------------------------------------

        } else {
            Log.e("MacroGoals", "El cálculo de metas falló (devolvió null). Revisa logs de MacroCalc.")
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

        // --- ¡¡LOG CLAVE AQUÍ!! ---
        // Imprime el mapa EXACTO que recibes del diálogo ANTES de hacer nada más
        Log.d(TAG, "updateFirestoreProfile RECIBIENDO: $updates")
        // --------------------------

        showProgressBar(true) // Mostrar progreso mientras guarda
        val userRef = db.collection("users").document(userId)

        // --- OPERACIÓN DE ACTUALIZACIÓN ---
        // ¿Estás pasando el mapa 'updates' DIRECTAMENTE a userRef.update()?
        userRef.update(updates) // <-- ¿Es realmente así? ¿O estás creando un NUEVO mapa aquí?
            .addOnSuccessListener {
                showProgressBar(false) // Ocultar al éxito
                Log.d(TAG, "Perfil de usuario actualizado correctamente en Firestore.")
                Toast.makeText(context, "Perfil actualizado", Toast.LENGTH_SHORT).show()

                // --- Actualizar perfil local DESPUÉS de guardar en Firestore ---
                // Pasamos el MISMO mapa 'updates' que se usó para Firestore
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
    // ------------------------------------------

    // --- updateLocalProfile ACTUALIZADA ---
    private fun updateLocalProfile(updates: Map<String, Any?>) {
        // Actualiza el objeto local 'currentUserProfile' con los datos que
        // ACABAN de ser guardados exitosamente en Firestore.
        // Asegúrate que los tipos coinciden con lo que guardaste.
        currentUserProfile = currentUserProfile?.copy(
            nombre = updates["nombre"] as? String ?: currentUserProfile?.nombre,
            // Firestore devuelve números enteros como Long, castear a Int si es necesario
            edad = (updates["edad"] as? Long)?.toInt() ?: currentUserProfile?.edad,
            peso = updates["peso"] as? Double ?: currentUserProfile?.peso,
            altura = updates["altura"] as? Double ?: currentUserProfile?.altura, // Asume CM
            sexo = updates["sexo"] as? String ?: currentUserProfile?.sexo,
            objetivo = updates["objetivo"] as? String ?: currentUserProfile?.objetivo,
            nivelActividad = updates["nivelActividad"] as? String ?: currentUserProfile?.nivelActividad
            // NO actualizamos los macros aquí, se hará en calculateAndSetMacroGoals si se guardan
        )
        Log.d(TAG, "Perfil local (básico) actualizado: $currentUserProfile")
    }
    // ------------------------------------

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
        binding.editProfileButton.isVisible = false // No permitir editar si hay error
    }

    private fun handleProfileNotFoundError(userId: String) {
        Log.w(TAG, "Documento de usuario no encontrado para UID: $userId")
        binding.usernameTextView.text = "Perfil no encontrado. Completa tu perfil o contacta soporte."
        binding.profileView.isVisible = true
        binding.editProfileButton.isVisible = false
        // Considera redirigir a ProfileSetupActivity si es necesario
        // val intent = Intent(activity, ProfileSetupActivity::class.java)
        // startActivity(intent)
    }

    // showProgressBar ahora solo toma boolean
    private fun showProgressBar(isLoading: Boolean) {
        binding.progressBarHome.isVisible = isLoading
        // Opcional: Deshabilitar botón editar mientras carga
        binding.editProfileButton.isEnabled = !isLoading
    }
    // Eliminado hideProgressBar() ya que showProgressBar(false) hace lo mismo

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView called")
        _binding = null // Limpiar binding
    }
}