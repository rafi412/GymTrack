package com.example.gymtrack // O tu paquete

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.example.gymtrack.databinding.ActivityLoginBinding // Importa tu ViewBinding
import com.example.gymtrack.login.ProfileSetupActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore // Importar Firestore
import com.google.firebase.firestore.ktx.firestore    // Importar KTX
import com.google.firebase.ktx.Firebase

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var db: FirebaseFirestore // Instancia de Firestore
    private val TAG = "LoginActivity"

    // ActivityResultLauncher para el flujo de Google Sign-In
    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        hideProgressBar() // Ocultar progreso independientemente del resultado aquí
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                Log.d(TAG, "Google Sign-In Exitoso: ID de Cuenta ${account.id}")
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Log.w(TAG, "Google sign in fallido", e)
                Toast.makeText(this, "Fallo de inicio de sesión con Google: ${e.statusCode}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.w(TAG, "Inicio de sesión con Google cancelado o fallido (resultCode: ${result.resultCode})")
            Toast.makeText(this, "Inicio de sesión con Google cancelado", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = Firebase.auth
        db = Firebase.firestore // Inicializar Firestore

        // Configurar Google Sign-In
        configureGoogleSignIn()

        // --- Listeners de los Botones ---
        binding.buttonLogin.setOnClickListener {
            performEmailPasswordLogin()
        }

        binding.buttonSignInGoogle.setOnClickListener {
            signInWithGoogle()
        }

        binding.buttonGoToRegister.setOnClickListener {
            // Iniciar la actividad de registro (debes crearla y aplicar lógica similar)
            // val intent = Intent(this, RegisterActivity::class.java)
            // startActivity(intent)
            Toast.makeText(this, "Ir a pantalla de Registro (pendiente)", Toast.LENGTH_SHORT).show() // Placeholder
        }
    }

    private fun configureGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // Crucial para Firebase
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun performEmailPasswordLogin() {
        val email = binding.editTextEmail.text.toString().trim()
        val password = binding.editTextPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Ingresa correo y contraseña", Toast.LENGTH_SHORT).show()
            return
        }
        showProgressBar()
        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                // No ocultamos progress bar aquí, lo hacemos después de verificar perfil
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithEmail: Exitoso")
                    handleLoginSuccess(firebaseAuth.currentUser) // Verificar perfil y navegar
                } else {
                    hideProgressBar() // Ocultar si falla el login
                    Log.w(TAG, "signInWithEmail: Fallido", task.exception)
                    Toast.makeText(baseContext, "Autenticación fallida: ${task.exception?.message}",
                        Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun signInWithGoogle() {
        Log.d(TAG, "Iniciando flujo de Google Sign-In")
        showProgressBar() // Mostrar progreso mientras se abre la selección de cuenta
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        Log.d(TAG, "Autenticando con Firebase usando Google Token...")
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        // Progress bar ya está visible desde signInWithGoogle()
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                // No ocultamos progress bar aquí, lo hacemos después de verificar perfil
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithCredential(Google): Exitoso")
                    handleLoginSuccess(firebaseAuth.currentUser) // Verificar perfil y navegar
                } else {
                    hideProgressBar() // Ocultar si falla la autenticación con Firebase
                    Log.w(TAG, "signInWithCredential(Google): Fallido", task.exception)
                    Toast.makeText(this, "Fallo de autenticación con Firebase (Google).", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // Función centralizada para manejar el éxito del login Y verificar perfil
    private fun handleLoginSuccess(firebaseUser: FirebaseUser?) {
        if (firebaseUser == null) {
            Log.e(TAG, "handleLoginSuccess llamado con usuario nulo!")
            Toast.makeText(this, "Error inesperado al obtener usuario.", Toast.LENGTH_SHORT).show()
            hideProgressBar()
            return
        }

        // Mantener progress bar visible mientras verificamos perfil
        Log.d(TAG, "Login/Registro exitoso para ${firebaseUser.uid}. Verificando perfil...")
        checkProfileAndNavigate(firebaseUser.uid)
    }

    // Nueva función para verificar el perfil y decidir la navegación
    private fun checkProfileAndNavigate(userId: String) {
        val userRef = db.collection("users").document(userId) // Asegúrate que la colección es "users"

        userRef.get()
            .addOnSuccessListener { document ->
                hideProgressBar() // Ocultar progreso AHORA
                val intent: Intent
                if (document != null && document.exists()) {
                    // Perfil YA EXISTE -> Ir a MainActivity
                    Log.d(TAG, "Perfil encontrado para $userId. Navegando a MainActivity.")
                    intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                } else {
                    // Perfil NO EXISTE -> Ir a ProfileSetupActivity
                    Log.d(TAG, "Perfil no encontrado para $userId. Navegando a ProfileSetupActivity.")
                    intent = Intent(this, ProfileSetupActivity::class.java)
                    // No es necesario limpiar la pila aquí, ProfileSetup lo hará al ir a Main
                }
                startActivity(intent)
                finish() // Finalizar LoginActivity
            }
            .addOnFailureListener { e ->
                hideProgressBar() // Ocultar progreso en caso de error
                Log.e(TAG, "Error al verificar perfil para $userId", e)
                Toast.makeText(this, "Error al verificar perfil: ${e.message}", Toast.LENGTH_LONG).show()
                // Fallback: Podrías ir a ProfileSetup o mostrar error y no hacer nada.
                // Ir a ProfileSetup puede ser una opción segura.
                // val intent = Intent(this, ProfileSetupActivity::class.java)
                // startActivity(intent)
                // finish()
            }
    }

    private fun showProgressBar() {
        binding.progressBarLogin.visibility = View.VISIBLE
        binding.buttonLogin.isEnabled = false
        binding.buttonSignInGoogle.isEnabled = false
        binding.buttonGoToRegister.isEnabled = false
    }

    private fun hideProgressBar() {
        binding.progressBarLogin.visibility = View.GONE
        binding.buttonLogin.isEnabled = true
        binding.buttonSignInGoogle.isEnabled = true
        binding.buttonGoToRegister.isEnabled = true
    }
}