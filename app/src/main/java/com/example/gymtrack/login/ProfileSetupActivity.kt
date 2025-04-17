package com.example.gymtrack.login // O tu paquete

// Quita la importación de User si no la usas directamente aquí
// import User
import User
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RadioButton
import android.widget.Toast
import com.example.gymtrack.LoginActivity // Si necesitas volver a Login
import com.example.gymtrack.MainActivity
import com.example.gymtrack.R // Importa tu R
import com.example.gymtrack.databinding.ActivityProfileSetupBinding // Importa tu ViewBinding
import android.widget.ArrayAdapter // Importar ArrayAdapter
// Quita AutoCompleteTextView si no lo usas directamente
// import android.widget.AutoCompleteTextView
// Importa tu Data Class User si la necesitas para leer datos existentes
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions // Importar SetOptions para merge
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject // Importar para convertir
import com.google.firebase.ktx.Firebase

class ProfileSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileSetupBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var currentUser: FirebaseUser? = null
    private val TAG = "ProfileSetupActivity"

    // --- Mapa y variable para el desplegable de Objetivo ---
    private val objetivoMap = mapOf(
        // TEXTO EN UI (de strings.xml) to CLAVE INTERNA (para Firestore y Calculadora)
        "Perder Peso (Rápido)" to "PERDER_PESO", // Ajusta claves si son diferentes
        "Perder Peso (Lento)" to "PERDER_PESO_LENTO",
        "Mantener Peso" to "MANTENER",
        "Ganar Masa Muscular" to "GANAR_MASA",
        "Ganar Masa (Rápido)" to "GANAR_MASA_RAPIDO"
    )
    private var selectedObjetivoKey: String? = null // Guarda la CLAVE ("MANTENER", etc.)
    // ----------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = Firebase.auth
        db = Firebase.firestore
        currentUser = firebaseAuth.currentUser

        if (currentUser == null) {
            Log.e(TAG, "Usuario no logueado en ProfileSetupActivity. Volviendo a Login.")
            navigateToLogin()
            return
        }

        // Configurar desplegable ANTES de intentar cargar datos existentes
        setupObjetivoDropdown()
        setupActividadDropdown()

        binding.editTextNombreSetup.setText(currentUser?.displayName ?: currentUser?.email ?: "")
        loadExistingData() // Intentar cargar y pre-rellenar

        binding.buttonGuardarPerfil.setOnClickListener {
            guardarPerfilUsuario()
        }
    }

    private fun setupObjetivoDropdown() {
        // Obtener las opciones desde el array de strings
        // Asegúrate de que R.array.objetivo_options existe en tu strings.xml
        val objetivos = resources.getStringArray(R.array.objetivo_options)
        // Crear un ArrayAdapter
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, objetivos)

        // Referenciar el AutoCompleteTextView usando el binding y el ID correcto
        val autoCompleteTextView = binding.autoCompleteObjetivoSetup // Usa el ID de tu AutoCompleteTextView

        // Asignar el adapter
        autoCompleteTextView.setAdapter(adapter)

        // Listener para guardar la CLAVE seleccionada
        autoCompleteTextView.setOnItemClickListener { parent, view, position, id ->
            val selectedText = parent.getItemAtPosition(position) as String
            selectedObjetivoKey = objetivoMap[selectedText] // Guarda la CLAVE interna
            Log.d(TAG, "Objetivo seleccionado: $selectedText (Clave: $selectedObjetivoKey)")
            // Limpiar error si lo había
            binding.inputLayoutObjetivoSetup.error = null
        }
    }

    private fun setupActividadDropdown() {
        try { // Añadir try-catch para depurar errores de recursos
            // 1. Obtener las opciones desde el array de strings
            // VERIFICA QUE R.array.nivel_actividad_options EXISTE Y TIENE ITEMS
            val niveles = resources.getStringArray(R.array.nivel_actividad_options)
            if (niveles.isEmpty()) {
                Log.e(TAG, "El array 'nivel_actividad_options' está vacío o no se encontró.")
                Toast.makeText(this, "Error al cargar opciones de actividad", Toast.LENGTH_SHORT).show()
                return // Salir si no hay opciones
            }
            Log.d(TAG, "Opciones de actividad cargadas: ${niveles.joinToString()}")

            // 2. Crear un ArrayAdapter
            val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, niveles)

            // 3. Referenciar el AutoCompleteTextView usando el binding y el ID CORRECTO
            // VERIFICA QUE EL ID EN TU XML ES 'auto_complete_nivel_actividad'
            val autoCompleteTextView = binding.autoCompleteNivelActividad

            // 4. Asignar el adapter
            autoCompleteTextView.setAdapter(adapter)
            Log.d(TAG, "Adapter asignado a autoCompleteNivelActividad")

            // 5. Pre-seleccionar valor (esto se hace en loadExistingData si ya hay datos)
            // Lo movemos a loadExistingData para asegurar que se haga después de cargar

            // 6. Listener para actualizar la clave seleccionada
            autoCompleteTextView.setOnItemClickListener { parent, _, position, _ ->
                val selectedText = parent.getItemAtPosition(position) as String
                // Usa el mapa 'actividadMap' que DEBES definir en tu Activity/Fragment
                selectedActividadKey = actividadMap[selectedText]
                binding.inputLayoutNivelActividad.error = null // Limpiar error si lo había
                Log.d(TAG, "Nivel actividad seleccionado: $selectedText (Clave: $selectedActividadKey)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en setupActividadDropdown", e)
            Toast.makeText(this, "Error configurando nivel de actividad", Toast.LENGTH_SHORT).show()
        }
    }

    private val actividadMap = mapOf(
        "Sedentario (Poco o ningún ejercicio)" to "SEDENTARIO",
        "Ligero (Ejercicio 1-3 días/sem)" to "LIGERO",
        "Moderado (Ejercicio 3-5 días/sem)" to "MODERADO",
        "Activo (Ejercicio 6-7 días/sem)" to "ACTIVO",
        "Muy Activo (Ejercicio intenso + Trabajo físico)" to "MUY_ACTIVO"
    )
    private var selectedActividadKey: String? = null // Variable para guardar la clave

    private fun loadExistingData() {
        showProgressBar()
        val userId = currentUser!!.uid
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                hideProgressBar()
                if (document != null && document.exists()) {
                    Log.d(TAG, "Cargando datos existentes para pre-rellenar.")
                    val user = document.toObject<User>()
                    if (user != null) {
                        binding.editTextNombreSetup.setText(user.nombre ?: currentUser?.displayName ?: "")
                        // Asumiendo que tienes un campo 'username' en tu User data class y Firestore
                        binding.editTextUserNameSetup.setText(user.username ?: "")
                        user.edad?.let { binding.editTextEdadSetup.setText(it.toString()) }
                        // Asumiendo altura en CM (Long)
                        user.altura?.let { binding.editTextAlturaSetup.setText(it.toString()) }
                        user.peso?.let { binding.editTextPesoSetup.setText(it.toString()) }

                        // --- Pre-rellenar Desplegable ---
                        selectedObjetivoKey = user.objetivo // Guardar la clave existente
                        // Buscar el texto correspondiente a la clave guardada
                        val objetivoText = objetivoMap.entries.find { it.value == selectedObjetivoKey }?.key
                        if (objetivoText != null) {
                            // Establecer el texto en el AutoCompleteTextView
                            // El 'false' es importante para que no intente filtrar la lista al establecer el texto
                            binding.autoCompleteObjetivoSetup.setText(objetivoText, false)
                            Log.d(TAG, "Objetivo pre-rellenado: $objetivoText (Clave: $selectedObjetivoKey)")
                        } else if (!selectedObjetivoKey.isNullOrEmpty()){
                            Log.w(TAG,"La clave de objetivo guardada '$selectedObjetivoKey' no coincide con ninguna opción del mapa.")
                            // Opcional: Mostrar un mensaje o dejarlo vacío
                            binding.autoCompleteObjetivoSetup.setText("", false)
                            selectedObjetivoKey = null // Resetear si la clave no es válida
                        }
                        // --------------------------------

                        when (user.sexo) {
                            "Masculino" -> binding.radioButtonMasculino.isChecked = true
                            "Femenino" -> binding.radioButtonFemenino.isChecked = true
                        }
                        // Pre-rellenar otros campos si los tienes (nivelActividad, etc.)
                        // Ejemplo para nivelActividad (necesitarías un Spinner/RadioGroup similar)
                        // user.nivelActividad?.let { binding.spinnerNivelActividad.setSelection(...) }
                    }
                } else {
                    Log.d(TAG, "No hay datos existentes para pre-rellenar.")
                }
            }
            .addOnFailureListener { e ->
                hideProgressBar()
                Log.w(TAG, "Error al cargar datos existentes", e)
                Toast.makeText(this, "No se pudieron cargar datos previos.", Toast.LENGTH_SHORT).show()
            }
    }


    private fun guardarPerfilUsuario() {
        val user = firebaseAuth.currentUser
        if (user == null) {
            Log.e(TAG, "Error: guardarPerfilUsuario llamado sin usuario logueado.")
            Toast.makeText(this, "Error: No hay sesión activa.", Toast.LENGTH_SHORT).show()
            navigateToLogin() // Ir a login si no hay usuario
            return
        }

        // --- Leer datos del formulario ---
        val nombre = binding.editTextNombreSetup.text.toString().trim()
        val username = binding.editTextUserNameSetup.text.toString().trim() // Asegúrate que este campo existe en tu layout/binding
        val edadStr = binding.editTextEdadSetup.text.toString().trim()
        val alturaCmStr = binding.editTextAlturaSetup.text.toString().trim() // Asume que es cm
        val pesoStr = binding.editTextPesoSetup.text.toString().trim()
        // El objetivo se lee de la variable 'selectedObjetivoKey'
        val selectedSexoId = binding.radioGroupSexo.checkedRadioButtonId

        // --- Validaciones ---
        var isValid = true
        if (nombre.isEmpty()) {
            binding.inputLayoutNombreSetup.error = "El nombre es obligatorio" // Asume inputLayoutNombreSetup
            isValid = false
        } else {
            binding.inputLayoutNombreSetup.error = null
        }

        if (username.isEmpty()) { // Validar username si es obligatorio
            binding.inputLayoutUserNameSetup.error = "El nombre de usuario es obligatorio" // Asume inputLayoutUserNameSetup
            isValid = false
        } else {
            binding.inputLayoutUserNameSetup.error = null
        }

        val edad = edadStr.toIntOrNull() // Usar Int para edad
        if (edad == null || edad <= 0) {
            binding.inputLayoutEdadSetup.error = "Edad inválida" // Asume inputLayoutEdadSetup
            isValid = false
        } else {
            binding.inputLayoutEdadSetup.error = null
        }

        val alturaCm = alturaCmStr.toDoubleOrNull() // Usar Double para altura (o Int si prefieres cm enteros)
        if (alturaCm == null || alturaCm <= 0) {
            binding.inputLayoutAlturaSetup.error = "Altura inválida" // Asume inputLayoutAlturaSetup
            isValid = false
        } else {
            binding.inputLayoutAlturaSetup.error = null
        }

        val peso = pesoStr.toDoubleOrNull()
        if (peso == null || peso <= 0) {
            binding.inputLayoutPesoSetup.error = "Peso inválido" // Asume inputLayoutPesoSetup
            isValid = false
        } else {
            binding.inputLayoutPesoSetup.error = null
        }

        // Validar Objetivo
        if (selectedObjetivoKey == null) {
            binding.inputLayoutObjetivoSetup.error = "Debes seleccionar un objetivo"
            isValid = false
        } else {
            binding.inputLayoutObjetivoSetup.error = null
        }

        // Validar Sexo
        var sexo: String? = null
        if (selectedSexoId == -1) {
            Toast.makeText(this, "Debes seleccionar tu sexo", Toast.LENGTH_SHORT).show()
            isValid = false
        } else {
            sexo = findViewById<RadioButton>(selectedSexoId).text.toString()
        }

        if (!isValid) {
            Toast.makeText(this, "Por favor, corrige los errores.", Toast.LENGTH_SHORT).show()
            return // Detener si hay errores
        }

        val actividadFisica = selectedActividadKey ?: "SEDENTARIO" // Valor por defecto si no se selecciona nada


        // --- Preparar datos para Firestore ---
        val userId = user.uid
        val userProfileData = hashMapOf<String, Any?>(
            "nombre" to nombre,
            "username" to username, // Asegúrate que existe en Firestore/User.kt
            "edad" to edad,         // Guardar Int
            "altura" to alturaCm,   // Guardar Double (o Int) para cm
            "peso" to peso,
            "sexo" to sexo,
            "objetivo" to selectedObjetivoKey, // Guardar la CLAVE INTERNA
            "nivelActividad" to selectedActividadKey,
            "email" to user.email,
            "profileCompleted" to true,
            // Añade nivelActividad si lo pides en esta pantalla
            // Añade metas calculadas si las calculas aquí
            // "caloriasMeta" to ...,
        )

        showProgressBar()

        // --- Guardar/Actualizar en Firestore usando merge ---
        db.collection("users").document(userId)
            .set(userProfileData, SetOptions.merge()) // merge() es importante si ya existía el doc
            .addOnSuccessListener {
                hideProgressBar()
                Log.d(TAG, "Perfil de usuario guardado/actualizado con éxito para $userId")
                Toast.makeText(this, "Perfil guardado", Toast.LENGTH_SHORT).show()
                navigateToMainActivity() // Ir a la pantalla principal
            }
            .addOnFailureListener { e ->
                hideProgressBar()
                Log.e(TAG, "Error al guardar perfil", e)
                Toast.makeText(this, "Error al guardar perfil: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun navigateToMainActivity() {
        Log.d(TAG, "Navegando a MainActivity desde ProfileSetup")
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showProgressBar() {
        // Asegúrate que el ID progressBarSetup existe en tu layout
        binding.progressBarSetup?.visibility = View.VISIBLE
        binding.buttonGuardarPerfil.isEnabled = false
    }

    private fun hideProgressBar() {
        binding.progressBarSetup?.visibility = View.GONE
        binding.buttonGuardarPerfil.isEnabled = true
    }
}