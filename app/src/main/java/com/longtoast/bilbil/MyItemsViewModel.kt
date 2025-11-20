package com.longtoast.bilbil.ui.myitems // 패키지 경로를 적절히 설정 (예시)

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.longtoast.bilbil.Product // Product 모델 import
import com.longtoast.bilbil.ProductRepository // Repository import
import kotlinx.coroutines.launch

class MyItemsViewModel(private val repository: ProductRepository) : ViewModel() {

    private val _products = MutableLiveData<List<Product>>(emptyList())
    val products: LiveData<List<Product>> = _products

    private val _loading = MutableLiveData<Boolean>(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    /**

    현재 로그인한 사용자의 판매 물품 목록을 조회합니다.
    @param userId 현재 로그인한 사용자의 ID*/
    fun loadMyProducts(userId: Int) {_loading.value = true
        _error.value = null
        viewModelScope.launch {
            val result = repository.getSellerProducts(userId) // 💡 Repository 호출

            result.onSuccess { list ->
                _products.value = list
            }.onFailure { t ->
                _products.value = emptyList()
                _error.value = t.localizedMessage ?: "알 수 없는 오류"
            }
            _loading.value = false
        }
    }
}

class MyItemsViewModelFactory(private val repository: ProductRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MyItemsViewModel::class.java)) {
            return MyItemsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}