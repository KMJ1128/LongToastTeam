package com.longtoast.bilbil

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.longtoast.bilbil.databinding.FragmentMessageBinding // 🚨 Binding 클래스 이름 확인

class MessageFragment : Fragment() {

    private var _binding: FragmentMessageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Fragment의 뷰 바인딩 초기화
        _binding = FragmentMessageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 여기에 메시지 화면의 로직을 추가합니다. (예: RecyclerView 설정 등)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}