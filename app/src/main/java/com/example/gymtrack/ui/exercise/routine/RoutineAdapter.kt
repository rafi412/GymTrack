package com.example.gymtrack.ui.exercise.routine

import android.view.LayoutInflater
import android.view.View // Importar View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.gymtrack.databinding.ItemRoutineBinding // Asegúrate que el binding se regenera para ConstraintLayout

// Data class (sin cambios)
data class RoutineViewData(
    val id: String,
    val name: String,
    val description: String?
)

class RoutineAdapter(
    private var routines: MutableList<RoutineViewData>, // Cambiado a MutableList
    private val onItemClick: (RoutineViewData) -> Unit,
    private val onDeleteClick: (RoutineViewData) -> Unit // Nuevo listener para eliminar
) : RecyclerView.Adapter<RoutineAdapter.RoutineViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoutineViewHolder {
        // Asegúrate que ItemRoutineBinding corresponde al nuevo layout con ConstraintLayout
        val binding = ItemRoutineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RoutineViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RoutineViewHolder, position: Int) {
        val routine = routines[position]
        holder.bind(routine)
    }

    override fun getItemCount(): Int = routines.size

    fun updateData(newRoutines: List<RoutineViewData>) {
        routines.clear()
        routines.addAll(newRoutines)
        notifyDataSetChanged() // O usar DiffUtil
    }

    // --- Añadido: Función para eliminar un item visualmente ---
    fun removeItem(routine: RoutineViewData) {
        val position = routines.indexOf(routine)
        if (position > -1) {
            routines.removeAt(position)
            notifyItemRemoved(position)
            // Opcional: notificar cambio de rango si afecta a otras posiciones
            // notifyItemRangeChanged(position, routines.size)
        }
    }
    // -------------------------------------------------------


    inner class RoutineViewHolder(private val binding: ItemRoutineBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(routine: RoutineViewData) {
            // Acceder a TextViews dentro del LinearLayout anidado
            binding.textRoutineItemName.text =
                routine.name // Ajustar acceso si cambiaste IDs
            binding.textRoutineItemDesc.text = routine.description ?: ""
            binding.textRoutineItemDesc.visibility =
                if (routine.description.isNullOrBlank()) View.GONE else View.VISIBLE

            // Click en toda la fila (para ver detalles)
            // Asegúrate que el ID 'layout_routine_info' existe en el LinearLayout
            binding.layoutRoutineInfo.setOnClickListener {
                onItemClick(routine)
            }
            // Click SOLO en el botón de eliminar
            binding.buttonDeleteRoutineItem.setOnClickListener {
                onDeleteClick(routine)
            }
        }
    }
}