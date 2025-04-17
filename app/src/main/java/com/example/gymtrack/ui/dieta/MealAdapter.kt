package com.example.gymtrack.ui.dieta


import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.gymtrack.databinding.ItemMealBinding // Binding para item_meal.xml

class MealAdapter(
    private var meals: MutableList<MealData>
    // Añade listeners si necesitas (ej: onDeleteClick: (MealData) -> Unit)
) : RecyclerView.Adapter<MealAdapter.MealViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MealViewHolder {
        val binding = ItemMealBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MealViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MealViewHolder, position: Int) {
        holder.bind(meals[position])
    }

    override fun getItemCount(): Int = meals.size

    fun updateData(newMeals: List<MealData>) {
        meals.clear()
        meals.addAll(newMeals)
        notifyDataSetChanged() // O DiffUtil
    }

    inner class MealViewHolder(private val binding: ItemMealBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(meal: MealData) {
            binding.textMealItemTitle.text = meal.title ?: "Comida"
            binding.textMealItemCalories.text = "${meal.calories ?: 0} kcal"
            binding.textMealItemMacros.text =
                "P: ${meal.proteinGrams ?: 0}g / C: ${meal.carbGrams ?: 0}g / G: ${meal.fatGrams ?: 0}g"

            // Configura listeners aquí si los añadiste (ej: para borrar)
            // binding.buttonDeleteMealItem.setOnClickListener { onDeleteClick(meal) }
        }
    }
}