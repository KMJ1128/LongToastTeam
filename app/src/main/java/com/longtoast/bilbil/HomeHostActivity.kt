package com.longtoast.bilbil

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.longtoast.bilbil.databinding.ActivityHomeHostBinding

class HomeHostActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeHostBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeHostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // FragmentManager에 리스너 추가: Fragment 스택 변경 시 호출됨
        setupBackStackListener()

        // 1. 앱 시작 시 기본 화면(HomeFragment) 설정
        if (savedInstanceState == null) {
            replaceFragment(HomeFragment())
        }

        // 2. Bottom Navigation Listener 설정
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            // NewPostFragment가 아닌 기본 Fragment로 이동할 때는 하단바를 보이게 유지
            setBottomNavVisibility(View.VISIBLE)

            when (item.itemId) {
                R.id.nav_home -> {
                    replaceFragment(HomeFragment())
                    true
                }
                R.id.nav_message -> {
                    replaceFragment(MessageFragment())
                    true
                }
                R.id.nav_my_items -> {
                    replaceFragment(MyItemsFragment())
                    true
                }
                else -> false
            }
        }

        // 3. FAB (새 게시글 작성) 리스너 설정
        binding.fabNewPost.setOnClickListener {
            // NewPostFragment를 띄우기 전에 하단바와 FAB를 숨깁니다.
            setBottomNavVisibility(View.GONE)

            supportFragmentManager.beginTransaction()
                .replace(R.id.main_fragment_container, NewPostFragment())
                .addToBackStack(null) // 뒤로 가기 시 복귀 가능하도록 스택에 추가
                .commit()
        }
    }

    // Fragment 교체 함수 (Bottom Nav 항목 선택 시 사용)
    private fun replaceFragment(fragment: Fragment): Boolean {
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_fragment_container, fragment)
            .commit()
        return true
    }

    /**
     * Fragment Back Stack의 변화를 감지하여 하단바 가시성을 관리하는 리스너 설정
     */
    private fun setupBackStackListener() {
        supportFragmentManager.addOnBackStackChangedListener {
            // 현재 Fragment가 Back Stack에서 제거되었는지 확인
            val fragmentCount = supportFragmentManager.backStackEntryCount

            // Back Stack이 비어있으면 (즉, HomeHostActivity의 기본 Fragment들만 남아있으면)
            if (fragmentCount == 0) {
                // NewPostFragment가 닫히고 기본 화면으로 돌아왔으므로, 하단바를 다시 표시합니다.
                setBottomNavVisibility(View.VISIBLE)
            }
            // NewPostFragment는 BackStack에 추가될 때 setBottomNavVisibility(View.GONE)에 의해 이미 숨겨져 있습니다.
        }
    }

    /**
     * 하단 네비게이션 바와 FAB의 가시성을 설정하는 함수
     */
    private fun setBottomNavVisibility(visibility: Int) {
        binding.bottomNavigation.visibility = visibility
        binding.fabNewPost.visibility = visibility
    }
}