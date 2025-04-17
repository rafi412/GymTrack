package com.example.gymtrack.ui.exercise // O tu paquete

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.gymtrack.databinding.ItemExerciseBinding // Binding para item_exercise.xml
import com.example.gymtrack.ui.exercise.routine.ExerciseData

class ExerciseAdapter(
    private var exercises: MutableList<ExerciseData>,
    private val onDeleteClick: (ExerciseData) -> Unit
) : RecyclerView.Adapter<ExerciseAdapter.ExerciseViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
        val binding = ItemExerciseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ExerciseViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
        holder.bind(exercises[position])
    }

    override fun getItemCount(): Int = exercises.size

    fun updateData(newExercises: List<ExerciseData>) {
        exercises.clear()
        exercises.addAll(newExercises)
        notifyDataSetChanged() // Usar DiffUtil para mejor rendimiento en listas largas
    }

    inner class ExerciseViewHolder(private val binding: ItemExerciseBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(exercise: ExerciseData) {
            binding.textExerciseItemName.text = "${exercise.order}. ${exercise.name}"

            val detailsParts = mutableListOf<String>()
            exercise.muscleGroup?.let { if(it.isNotBlank()) detailsParts.add(it) }
            exercise.series?.let { detailsParts.add("$it series") }
            exercise.reps?.let { if(it.isNotBlank()) detailsParts.add("x $it reps") }
            exercise.rest?.let { if(it.isNotBlank()) detailsParts.add("- $it descanso") }
            binding.textExerciseItemDetails.text = detailsParts.joinToString(" ")
            binding.textExerciseItemDetails.visibility = if(detailsParts.isEmpty()) View.GONE else View.VISIBLE

            binding.textExerciseItemNotes.text = "Notas: ${exercise.notes}"
            binding.textExerciseItemNotes.visibility = if (exercise.notes.isNullOrBlank()) View.GONE else View.VISIBLE

            binding.buttonDeleteExerciseItem.setOnClickListener {
                onDeleteClick(exercise)
            }
        }
    }
}