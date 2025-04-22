// --- Definición de la Data Class (Asegúrate que esté accesible para HomeFragment) ---
import com.google.firebase.Timestamp // Importar si usas Timestamps

data class User(
    val username: String? = null,
    val nombre: String? = null,
    val email: String? = null,
    val edad: Int? = null,
    val peso: Double? = null,
    val altura: Double? = null,
    val sexo: String? = null,
    val objetivo: String? = null,
    val caloriasDiarias: Int? = null,
    val proteinasDiarias: Int? = null,
    val carboDiarios: Int? = null,
    val createdAt: Timestamp? = null,
    val nivelActividad: String? = null,
    val profileCompleted: Boolean? = null,
) {
    // Constructor vacío requerido por Firestore
    constructor() : this(
        null, null, null, null, null,
        null, null, null, null, null, null, null, null, null // Ajustar número de nulls
    )
}