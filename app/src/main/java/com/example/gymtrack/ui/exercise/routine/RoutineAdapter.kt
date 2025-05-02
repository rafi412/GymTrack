package com.example.gymtrack.ui.exercise.routine // Asegúrate que el paquete es correcto

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.gymtrack.databinding.ItemRoutineBinding // Tu ViewBinding

// Data class (sin cambios)
data class RoutineViewData(
    val id: String,
    val name: String,
    val description: String?
)

class RoutineAdapter(
    private var routines: MutableList<RoutineViewData>,
    private val onItemClick: (RoutineViewData) -> Unit,
    private val onDeleteClick: (RoutineViewData) -> Unit,
    private val onShareClick: (RoutineViewData) -> Unit // <-- El constructor está BIEN
) : RecyclerView.Adapter<RoutineAdapter.RoutineViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoutineViewHolder {
        val binding = ItemRoutineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RoutineViewHolder(binding)
    }

    // --- CORRECCIÓN EN onBindViewHolder ---
    override fun onBindViewHolder(holder: RoutineViewHolder, position: Int) {
        val routine = routines[position]
        // Pasa la rutina Y las tres lambdas al método bind
        holder.bind(routine, onItemClick, onDeleteClick, onShareClick) // <-- PASAR LAS 4 COSAS
    }
    // ------------------------------------

    override fun getItemCount(): Int = routines.size

    fun updateData(newRoutines: List<RoutineViewData>) {
        routines.clear()
        routines.addAll(newRoutines)
        notifyDataSetChanged()
    }

    // Función para eliminar item (sin cambios, pero útil si la necesitas)
    /*
    fun removeItem(routine: RoutineViewData) {
        val position = routines.indexOf(routine)
        if (position > -1) {
            routines.removeAt(position)
            notifyItemRemoved(position)
        }
    }
    */

    inner class RoutineViewHolder(private val binding: ItemRoutineBinding) :
        RecyclerView.ViewHolder(binding.root) {

        // --- CORRECCIÓN EN LA FIRMA Y CUERPO DE bind ---
        // El método bind AHORA ACEPTA los 4 parámetros
        fun bind(
            routine: RoutineViewData,
            onItemClickLambda: (RoutineViewData) -> Unit, // Renombrado para claridad
            onDeleteClickLambda: (RoutineViewData) -> Unit, // Renombrado para claridad
            onShareClickLambda: (RoutineViewData) -> Unit  // Renombrado para claridad
        ) {
            // --- Ajusta los IDs si cambiaste el layout item_routine.xml ---
            // Asumiendo que los IDs son textViewRoutineName, textViewRoutineDescription,
            // buttonDeleteRoutine, buttonShareRoutine y la raíz para el click general.
            binding.textRoutineItemName.text = routine.name // ID del nombre
            binding.textRoutineItemDesc.text = routine.description ?: "" // ID de la descripción
            binding.textRoutineItemDesc.visibility =
                if (routine.description.isNullOrBlank()) View.GONE else View.VISIBLE

            // Click en toda la fila (usa la raíz del ViewHolder)
            binding.root.setOnClickListener {
                onItemClickLambda(routine) // Usa la lambda pasada como parámetro
            }

            // Click en el botón de eliminar (asegúrate que el ID es correcto)
            binding.buttonDeleteRoutineItem.setOnClickListener { // <-- ¿Es buttonDeleteRoutine o buttonDeleteRoutineItem? Usa el ID del XML
                onDeleteClickLambda(routine) // Usa la lambda pasada como parámetro
            }

            // Click en el botón de compartir (asegúrate que el ID es correcto)
            binding.buttonShareRoutine.setOnClickListener { // <-- Asegúrate que este ID existe en item_routine.xml
                onShareClickLambda(routine) // Usa la lambda pasada como parámetro
            }
            // ----------------------------------------------------------
        }
    }
}