package com.example.gymtrack // O tu paquete

import User
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem // <-- Importante para la referencia al menú
import android.view.View
import android.widget.TextView // <-- Importante para el header
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI // <-- Usar NavigationUI para onSupportNavigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.gymtrack.databinding.ActivityMainBinding
import com.example.gymtrack.ui.user.EditProfileDialogFragment
import com.example.gymtrack.ui.user.MacronutrientCalculator
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore // <-- Importar Firestore
import com.google.firebase.firestore.ktx.firestore    // <-- Importar extensión KTX
import com.google.firebase.ktx.Firebase


class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var navController: NavController
    private val TAG = "MainActivity"

    private var currentUserProfile: User? = null
    private var editProfileMenuItem: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        firebaseAuth = Firebase.auth

        if (firebaseAuth.currentUser == null) {
            Log.d(TAG, "Usuario no logueado, iniciando LoginActivity.")
            goToLogin()
            return
        }

        Log.d(TAG, "Usuario ${firebaseAuth.currentUser?.uid} logueado. Configurando MainActivity.")
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d(TAG, "setContentView completado.")

        setSupportActionBar(binding.toolbar)
        Log.d(TAG, "setSupportActionBar completado.")

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navViewDrawer: NavigationView = binding.navViewDrawer
        val bottomNavView: BottomNavigationView = binding.bottomNavView

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_main) as? NavHostFragment
        if (navHostFragment == null) {
            Log.e(TAG, "CRITICAL ERROR: NavHostFragment NO FUE ENCONTRADO.")
            Toast.makeText(this, "Error crítico al cargar la navegación.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        navController = navHostFragment.navController
        Log.d(TAG, "NavController obtenido.")

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home,
                R.id.navigation_dashboard,
                R.id.navigation_notifications,
                R.id.nav_settings
            ), drawerLayout
        )
        Log.d(TAG, "AppBarConfiguration creada.")

        setupActionBarWithNavController(navController, appBarConfiguration)
        Log.d(TAG, "setupActionBarWithNavController completado.")

        setupDrawerNavigation(navViewDrawer, drawerLayout)
        bottomNavView.setupWithNavController(navController)
        Log.d(TAG, "Controles de navegación configurados.")

        setupProfileUpdateListener()

        fetchUserProfileData()
        updateNavHeader() // Actualiza inicialmente

        Log.d(TAG, "Setup de MainActivity completado.")
    }

    // --- Configuración del Listener del Navigation Drawer ---
    private fun setupDrawerNavigation(navViewDrawer: NavigationView, drawerLayout: DrawerLayout) {
        editProfileMenuItem = navViewDrawer.menu.findItem(R.id.edit_profile)
        editProfileMenuItem?.isEnabled = false
        Log.d(TAG, "Item 'Editar Perfil' deshabilitado inicialmente.")

        navViewDrawer.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.closeDrawer(GravityCompat.START)
            var handled = true

            when (menuItem.itemId) {
                R.id.nav_logout -> {
                    showLogoutConfirmationDialog()
                }

                R.id.edit_profile -> {
                    Log.d(TAG, "'Editar Perfil' seleccionado en el drawer.")
                    currentUserProfile?.let { userProfile ->
                        Log.d(
                            TAG,
                            "currentUserProfile no es null, procediendo a calcular metas y abrir diálogo."
                        )
                        try {
                            // Calcular metas ANTES de abrir el diálogo
                            val calculatedGoals = MacronutrientCalculator.calculateGoals(
                                age = userProfile.edad, // Acceso seguro dentro de let
                                sex = userProfile.sexo,
                                weightKg = userProfile.peso,
                                heightCm = userProfile.altura,
                                activityLevelKey = userProfile.nivelActividad
                                    ?: "MODERADO", // Valor por defecto si es null
                                goalKey = userProfile.objetivo
                                    ?: "MANTENER" // Valor por defecto si es null
                            )
                            Log.d(TAG, "Metas calculadas para pasar al diálogo: $calculatedGoals")

                            // Crear y mostrar el diálogo, pasando User y Metas Calculadas
                            val dialogFragment =
                                EditProfileDialogFragment.newInstance(userProfile, calculatedGoals)
                            Log.d(
                                TAG,
                                "Mostrando EditProfileDialog desde MainActivity usando supportFragmentManager"
                            )

                            dialogFragment.show(supportFragmentManager, "EditProfileDialog")

                        } catch (e: Exception) {
                            Log.e(TAG, "Error al calcular metas o mostrar diálogo", e)
                            Toast.makeText(
                                this,
                                "Error al preparar la edición del perfil.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                    } ?: run { // Se ejecuta si currentUserProfile es null
                        Log.w(
                            TAG,
                            "currentUserProfile es null al intentar editar desde el drawer (botón estaba habilitado?)."
                        )
                        Toast.makeText(
                            this,
                            "Datos del perfil no disponibles aún.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                else -> {
                    handled = NavigationUI.onNavDestinationSelected(menuItem, navController)
                }
            }
            handled
        }
    }
    // ----------------------------------------------------

    // --- Listener para Resultados del DialogFragment ---
    private fun setupProfileUpdateListener() {
        supportFragmentManager.setFragmentResultListener(
            "profileUpdateResult",
            this
        ) { requestKey, bundle ->
            Log.d(TAG, "Resultado recibido de EditProfileDialogFragment con clave: $requestKey")
            // --- LLAMAR A LA FUNCIÓN DE GUARDADO EN FIRESTORE ---
            saveProfileUpdatesToFirestore(bundle) // Pasa el bundle recibido
        }
        Log.d(TAG, "Listener de resultados del fragmento configurado para 'profileUpdateResult'")
    }
    // -------------------------------------------------

    private fun saveProfileUpdatesToFirestore(updatedDataBundle: Bundle) {
        val userId = firebaseAuth.currentUser?.uid // Usar la instancia de MainActivity
        if (userId == null) {
            Toast.makeText(this, "Error: Usuario no identificado.", Toast.LENGTH_SHORT)
                .show() // Usar 'this'
            return
        }

        // --- Convertir Bundle a Map para Firestore ---
        val profileUpdates = hashMapOf<String, Any?>()
        updatedDataBundle.keySet().forEach { key ->
            when (key) {
                "updatedNombre" -> profileUpdates["nombre"] = updatedDataBundle.getString(key)
                "updatedEdad" -> profileUpdates["edad"] = updatedDataBundle.getInt(key)
                "updatedPeso" -> profileUpdates["peso"] = updatedDataBundle.getDouble(key)
                "updatedAltura" -> profileUpdates["altura"] = updatedDataBundle.getDouble(key)
                "updatedSexo" -> profileUpdates["sexo"] = updatedDataBundle.getString(key)
                "updatedObjetivo" -> profileUpdates["objetivo"] = updatedDataBundle.getString(key)
                "updatedNivelActividad" -> profileUpdates["nivelActividad"] =
                    updatedDataBundle.getString(key)

                "updatedCalorias" -> profileUpdates["caloriasDiarias"] =
                    updatedDataBundle.getInt(key)

                "updatedProteinas" -> profileUpdates["proteinasDiarias"] =
                    updatedDataBundle.getInt(key)

                "updatedCarbos" -> profileUpdates["carboDiarios"] = updatedDataBundle.getInt(key)
                "profileWasUpdated" -> if (updatedDataBundle.getBoolean(key)) profileUpdates["profileCompleted"] =
                    true
            }
        }
        // -------------------------------------------

        // Verificar si hay algo que actualizar
        if (profileUpdates.isEmpty()) {
            Log.w(TAG, "No se encontraron datos válidos en el Bundle para actualizar Firestore.")
            Toast.makeText(this, "No hay cambios para guardar.", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "saveProfileUpdatesToFirestore ENVIANDO: $profileUpdates")

        // Mostrar progreso usando el ProgressBar de MainActivity
        binding.mainActivityProgressBar.visibility = View.VISIBLE

        val db = Firebase.firestore // Obtener instancia de Firestore
        val userRef = db.collection("users").document(userId)

        // --- OPERACIÓN DE ACTUALIZACIÓN ---
        userRef.update(profileUpdates) // Usar el mapa construido desde el Bundle
            .addOnSuccessListener {
                binding.mainActivityProgressBar.visibility = View.GONE // Ocultar al éxito
                Log.d(TAG, "Perfil de usuario actualizado correctamente en Firestore.")
                Toast.makeText(this, "Perfil actualizado", Toast.LENGTH_SHORT).show()

                fetchUserProfileData()

            }
            .addOnFailureListener { e ->
                binding.mainActivityProgressBar.visibility = View.GONE // Ocultar en caso de error
                Log.e(TAG, "Error al actualizar el perfil en Firestore", e)
                Toast.makeText(this, "Error al guardar: ${e.message}", Toast.LENGTH_LONG)
                    .show()
            }
        // ---------------------------------
    }
    // ----------------------------------------------------------------------


    // --- Carga de Datos del Perfil desde Firestore (Sin cambios respecto a la versión anterior) ---
    private fun fetchUserProfileData() {
        val userId = firebaseAuth.currentUser?.uid
        if (userId == null) {
            Log.w(TAG, "fetchUserProfileData: userId es null.")
            editProfileMenuItem?.isEnabled = false
            return
        }
        Log.d(TAG, "Iniciando carga de datos de Firestore para usuario: $userId")
        val db = Firebase.firestore
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    currentUserProfile = document.toObject(User::class.java)
                    Log.d(TAG, "Datos del perfil cargados: ${currentUserProfile}")
                    updateNavHeader()
                    editProfileMenuItem?.isEnabled = true // Habilitar botón
                    Log.d(TAG, "Item 'Editar Perfil' HABILITADO.")
                } else {
                    Log.d(TAG, "No se encontró documento de perfil para: $userId")
                    currentUserProfile = null
                    updateNavHeader()
                    editProfileMenuItem?.isEnabled = false // Mantener deshabilitado
                    Log.w(TAG, "Item 'Editar Perfil' DESHABILITADO (no hay perfil Firestore).")
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error al obtener datos del perfil", exception)
                currentUserProfile = null
                updateNavHeader()
                editProfileMenuItem?.isEnabled = false // Deshabilitado por error
                Log.e(TAG, "Item 'Editar Perfil' DESHABILITADO (error carga).")
                Toast.makeText(this, "Error al cargar el perfil.", Toast.LENGTH_SHORT).show()
            }
    }
    // -----------------------------------------------

    // --- Actualización de la Cabecera del Drawer ---
    private fun updateNavHeader() {
        try {
            val headerView = binding.navViewDrawer.getHeaderView(0)
            val headerName: TextView =
                headerView.findViewById(R.id.textViewHeaderName)
            val headerEmail: TextView =
                headerView.findViewById(R.id.textViewHeaderEmail)
            val firebaseUser = firebaseAuth.currentUser

            if (currentUserProfile != null) {
                headerName.text = currentUserProfile?.nombre ?: currentUserProfile?.username
                        ?: firebaseUser?.displayName ?: "Usuario" // Prioriza nombre completo
                headerEmail.text =
                    currentUserProfile?.email ?: firebaseUser?.email ?: "email@ejemplo.com"
                Log.d(TAG, "NavHeader actualizado con datos de Firestore.")
            } else if (firebaseUser != null) {
                headerName.text = firebaseUser.displayName ?: "Usuario"
                headerEmail.text = firebaseUser.email ?: "email@ejemplo.com"
                Log.d(TAG, "NavHeader actualizado con datos de Auth.")
            } else {
                headerName.text = "Invitado"; headerEmail.text = ""
                Log.w(TAG, "NavHeader actualizado como Invitado.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al actualizar NavHeader: ${e.message}", e)
        }
    }
    // ---------------------------------------------

    // --- Diálogo de Confirmación para Logout ---
    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Cerrar Sesión")
            .setMessage("¿Estás seguro?")
            .setIcon(R.drawable.baseline_logout_24)
            .setPositiveButton("Sí") { dialog, _ ->
                performLogout()
                dialog.dismiss()
            }
            .setNegativeButton("No", null)
            .show()
    }
    // ------------------------------------------

    // --- Lógica de Cierre de Sesión  ---
    private fun performLogout() {
        Log.d(TAG, "Cerrando sesión...")
        firebaseAuth.signOut()
        currentUserProfile = null
        editProfileMenuItem?.isEnabled = false
        Log.d(TAG, "Item 'Editar Perfil' deshabilitado por logout.")
        goToLogin()
    }
    // ---------------------------------

    // --- Helper para ir a Login ---
    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    // ---------------------------

    // --- Manejo del botón "Up" ---
    override fun onSupportNavigateUp(): Boolean {
        return NavigationUI.navigateUp(
            navController,
            appBarConfiguration
        ) || super.onSupportNavigateUp()
    }
    // ---------------------------------------------------------
}