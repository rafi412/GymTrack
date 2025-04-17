package com.example.gymtrack.ui.settings // Asegúrate que el paquete es correcto

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.gymtrack.databinding.FragmentSettingsBinding // Importa el binding generado

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    // Esta propiedad solo es válida entre onCreateView y onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Puedes añadir lógica aquí si es necesario, por ejemplo, configurar listeners
        // binding.someSettingSwitch.setOnCheckedChangeListener { ... }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Limpiar binding
    }
}