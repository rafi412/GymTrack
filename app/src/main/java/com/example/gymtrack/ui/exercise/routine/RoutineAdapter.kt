package com.example.gymtrack.ui.exercise.routine

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.gymtrack.databinding.ItemRoutineBinding

data class RoutineViewData(
    val id: String,
    val name: String,
    val description: String?
)

class RoutineAdapter(
    private var routines: MutableList<RoutineViewData>,
    private val onItemClick: (RoutineViewData) -> Unit,
    private val onDeleteClick: (RoutineViewData) -> Unit,
    private val onShareClick: (RoutineViewData) -> Unit
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

        fun bind(
            routine: RoutineViewData,
            onItemClickLambda: (RoutineViewData) -> Unit,
            onDeleteClickLambda: (RoutineViewData) -> Unit,
            onShareClickLambda: (RoutineViewData) -> Unit
        ) {
            binding.textRoutineItemName.text = routine.name // ID del nombre
            binding.textRoutineItemDesc.text = routine.description ?: "" // ID de la descripción
            binding.textRoutineItemDesc.visibility =
                if (routine.description.isNullOrBlank()) View.GONE else View.VISIBLE

            binding.root.setOnClickListener {
                onItemClickLambda(routine) // Usa la lambda pasada como parámetro
            }

            binding.buttonDeleteRoutineItem.setOnClickListener {
                onDeleteClickLambda(routine) // Usa la lambda pasada como parámetro
            }

            binding.buttonShareRoutine.setOnClickListener {
                onShareClickLambda(routine) // Usa la lambda pasada como parámetro
            }
            // ----------------------------------------------------------
        }
    }
}