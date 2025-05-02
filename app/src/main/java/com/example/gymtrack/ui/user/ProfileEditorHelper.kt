package com.example.gymtrack.ui.user

import User
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ProfileEditorHelper {

    companion object {
        // Método para abrir el diálogo de editar perfil
        fun openEditProfileDialog(context: Context, currentUserProfile: User?) {
            currentUserProfile?.let { userProfile ->
                // Calcular las metas del usuario antes de abrir el diálogo
                val calculatedGoals = MacronutrientCalculator.calculateGoals(
                    age = userProfile.edad,
                    sex = userProfile.sexo,
                    weightKg = userProfile.peso,
                    heightCm = userProfile.altura,
                    activityLevelKey = userProfile.nivelActividad ?: "MODERADO",
                    goalKey = userProfile.objetivo ?: "MANTENER"
                )

                // Crear el fragmento del diálogo y pasar los datos
                val dialogFragment = EditProfileDialogFragment.newInstance(userProfile, calculatedGoals)
                dialogFragment.show((context as AppCompatActivity).supportFragmentManager, "EditProfileDialog")
            } ?: run {
                Log.w("ProfileEditorHelper", "currentUserProfile es null, no se puede abrir diálogo.")
                Toast.makeText(context, "Datos del perfil aún no cargados.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
