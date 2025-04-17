package com.example.gymtrack // O tu paquete

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AlertDialog // Importar AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat // Importar para cerrar drawer
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController // Importar NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI // Importar NavigationUI
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
// Quita setupWithNavController si manejas manualmente
// import androidx.navigation.ui.setupWithNavController
import com.example.gymtrack.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var navController: NavController // Guardar referencia
    private val TAG = "MainActivity" // TAG actualizado

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        firebaseAuth = Firebase.auth

        // --- Comprobación de Autenticación ---
        if (firebaseAuth.currentUser == null) {
            Log.d(TAG, "Usuario no logueado, iniciando LoginActivity.")
            goToLogin() // Usar helper
            return
        }

        // --- Usuario SÍ está logueado ---
        Log.d(TAG, "Usuario ${firebaseAuth.currentUser?.uid} logueado. Configurando MainActivity.")
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d(TAG, "setContentView completado.")

        // --- Configuración de Toolbar ---
        setSupportActionBar(binding.toolbar)
        Log.d(TAG, "setSupportActionBar completado.")

        // --- Referencias a los componentes de Navegación ---
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navViewDrawer: NavigationView = binding.navViewDrawer // Drawer
        val bottomNavView: BottomNavigationView = binding.bottomNavView // BottomNav

        // --- OBTENER NAVCONTROLLER ---
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_main) as? NavHostFragment
        if (navHostFragment == null) {
            Log.e(TAG, "CRITICAL ERROR: NavHostFragment NO FUE ENCONTRADO.")
            throw IllegalStateException("NavHostFragment not found...")
        }
        navController = navHostFragment.navController // Asignar a variable de clase
        Log.d(TAG, "NavController obtenido.")

        // --- Configuración de AppBarConfiguration ---
        appBarConfiguration = AppBarConfiguration(
            setOf(
                // IDs principales de AMBOS menús
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications, R.id.nav_settings
                // NO incluir nav_logout aquí
            ), drawerLayout
        )
        Log.d(TAG, "AppBarConfiguration creada.")

        // Conecta la ActionBar (Toolbar) con NavController y AppBarConfiguration
        setupActionBarWithNavController(navController, appBarConfiguration)
        Log.d(TAG, "setupActionBarWithNavController completado.")

        // --- CONECTAR CONTROLES DE NAVEGACIÓN ---

        // --- MANEJO MANUAL DE CLICS EN NAVIGATION VIEW (DRAWER) ---
        navViewDrawer.setNavigationItemSelectedListener { menuItem ->
            // Cerrar el drawer al seleccionar
            drawerLayout.closeDrawer(GravityCompat.START)

            when (menuItem.itemId) {
                R.id.nav_logout -> {
                    // Acción de cerrar sesión
                    showLogoutConfirmationDialog() // Mostrar confirmación
                    true // Indica que hemos manejado este item
                }
                else -> {
                    // Para los items de navegación, deja que NavigationUI los maneje.
                    // Esto también actualizará el estado seleccionado del BottomNavigationView si el ID coincide.
                    NavigationUI.onNavDestinationSelected(menuItem, navController)
                }
            }
            // Devuelve true si NavigationUI manejó la navegación, o si nosotros lo hicimos (logout)
            // Devuelve false si quieres que otro listener (si lo hubiera) también actúe.
            // En este caso, true es generalmente correcto.
            true
        }
        Log.d(TAG, "Listener manual para Drawer configurado.")
        // ------------------------------------------------

        // Conecta el BottomNavigationView con el MISMO NavController
        // Esto SÍ puede usar setupWithNavController porque no necesitamos interceptar sus clics (a menos que quieras)
        bottomNavView.setupWithNavController(navController)
        Log.d(TAG, "BottomNav bottomNavView.setupWithNavController completado.")

        // (Opcional) Actualizar datos del Header del Drawer
        updateNavHeader()
        Log.d(TAG, "Setup de MainActivity completado.")
    }

    // --- Diálogo de Confirmación para Logout ---
    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Cerrar Sesión")
            .setMessage("¿Estás seguro de que quieres cerrar sesión?")
            .setIcon(R.drawable.baseline_logout_24) // Asegúrate de tener este icono
            .setPositiveButton("Sí") { dialog, _ ->
                performLogout() // Llama a la función de logout
                dialog.dismiss()
            }
            .setNegativeButton("No", null) // null simplemente cierra
            .show()
    }
    // ------------------------------------------

    // --- Lógica de Cierre de Sesión ---
    private fun performLogout() {
        Log.d(TAG, "Cerrando sesión...")
        firebaseAuth.signOut()
        // Añade aquí signOut de otros proveedores si los usas (Google, etc.)
        goToLogin() // Redirigir a Login y finalizar MainActivity
    }
    // ---------------------------------

    // --- Helper para ir a Login ---
    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish() // Finalizar esta MainActivity
    }
    // ---------------------------

    // (Opcional) Actualizar cabecera del drawer (sin cambios)
    private fun updateNavHeader() {
        try {
            val headerView = binding.navViewDrawer.getHeaderView(0)
            val headerName: TextView = headerView.findViewById(R.id.textViewHeaderName)
            val headerEmail: TextView = headerView.findViewById(R.id.textViewHeaderEmail)
            val user = firebaseAuth.currentUser
            if (user != null) {
                headerName.text = user.displayName ?: "Usuario"
                headerEmail.text = user.email ?: "email@ejemplo.com"
            } else {
                headerName.text = "Invitado"; headerEmail.text = ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al actualizar NavHeader: ${e.message}", e)
        }
    }

    // --- onSupportNavigateUp (sin cambios) ---
    override fun onSupportNavigateUp(): Boolean {
        // val navController = findNavController(R.id.nav_host_fragment_activity_main) // Ya tenemos la variable navController
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}