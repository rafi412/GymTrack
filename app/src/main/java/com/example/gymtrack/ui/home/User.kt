// --- Definición de la Data Class (Asegúrate que esté accesible para HomeFragment) ---
import com.google.firebase.Timestamp // Importar si usas Timestamps

data class User(
    val username: String? = null,
    val nombre: String? = null,
    val email: String? = null,      // Añadido (útil tenerlo)
    val edad: Int? = null,
    val peso: Double? = null,
    val altura: Double? = null,
    val sexo: String? = null,       // Añadido desde ProfileSetup
    val objetivo: String? = null,
    val caloriasDiarias: Int? = null, // Podrías calcular esto en vez de guardarlo
    val proteinasDiarias: Int? = null,// Podrías calcular esto en vez de guardarlo
    val carboDiarios: Int? = null,  // Podrías calcular esto en vez de guardarlo
    val createdAt: Timestamp? = null, // Opcional: si guardaste cuándo se creó/actualizó
    val nivelActividad: String? = null, // Añadido desde ProfileSetup
    val profileCompleted: Boolean? = null, // Añadido desde ProfileSetup
    // Ya no necesitamos sequentialId aquí si usamos UID como identificador principal
) {
    // Constructor vacío requerido por Firestore
    constructor() : this(
        null, null, null, null, null,
        null, null, null, null, null, null, null, null, null // Ajustar número de nulls
    )
}