package com.longtoast.bilbil

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.longtoast.bilbil.databinding.ActivityHomeHostBinding
import android.widget.TextView

class HomeHostActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeHostBinding

    private lateinit var homeFragment: HomeFragment
    private lateinit var messageFragment: MessageFragment
    private lateinit var myItemsFragment: MyItemsFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeHostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupFragments()
        setupBottomNav()
        setupFABs()
        setupBackStackListener()

        setupNavigationHeader()
    }

    // ----------------------------
    // Fragment 초기화 및 add + hide/show
    // ----------------------------
    private fun setupFragments() {
        val fm = supportFragmentManager
        homeFragment = HomeFragment()
        messageFragment = MessageFragment()
        myItemsFragment = MyItemsFragment()

        val transaction = fm.beginTransaction()
        transaction.add(R.id.main_fragment_container, homeFragment, "HOME")
            .add(R.id.main_fragment_container, messageFragment, "MESSAGE").hide(messageFragment)
            .add(R.id.main_fragment_container, myItemsFragment, "MY_ITEMS").hide(myItemsFragment)
            .commit()
    }

    private fun showFragment(fragment: Fragment) {
        val fm = supportFragmentManager
        val transaction = fm.beginTransaction()
        fm.fragments.forEach { transaction.hide(it) }
        transaction.show(fragment).commit()
    }

    // ----------------------------
    // BottomNavigation
    // ----------------------------
    private fun setupBottomNav() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            setBottomNavVisibility(View.VISIBLE)

            when (item.itemId) {
                R.id.nav_home -> showFragment(homeFragment)
                R.id.nav_message -> showFragment(messageFragment)
                R.id.nav_my_items -> showFragment(myItemsFragment)
            }
            true
        }
    }

    // ----------------------------
    // FAB
    // ----------------------------
    private fun setupFABs() {
        binding.fabNewPost.setOnClickListener {
            setBottomNavVisibility(View.GONE)
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_fragment_container, NewPostFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    fun setBottomNavVisibility(visibility: Int) {
        binding.bottomNavigation.visibility = visibility
        binding.fabNewPost.visibility = visibility
    }

    // ----------------------------
    // BackStack listener
    // ----------------------------
    private fun setupBackStackListener() {
        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                setBottomNavVisibility(View.VISIBLE)
            }
        }
    }

    private fun setupNavigationHeader() {
        val headerView = binding.navView.getHeaderView(0)
        val nicknameTextView = headerView.findViewById<TextView>(R.id.nav_header_nickname)
        val addressTextView = headerView.findViewById<TextView>(R.id.nav_header_address)  // 🆕 추가

        // 닉네임 표시
        val nickname = AuthTokenManager.getNickname()
        nicknameTextView.text = nickname ?: "이름 미지정"

        // 🆕 주소 표시
        val address = AuthTokenManager.getAddress()
        addressTextView.text = address ?: "위치 미지정"
    }
}