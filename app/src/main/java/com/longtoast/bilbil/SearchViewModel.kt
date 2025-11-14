package com.longtoast.bilbil

import androidx.lifecycle.*
import kotlinx.coroutines.launch

class SearchViewModel(private val repository: ProductRepository) : ViewModel() {

    private val _products = MutableLiveData<List<Product>>(emptyList())
    val products: LiveData<List<Product>> = _products

    private val _loading = MutableLiveData<Boolean>(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    /**
     * searchQuery: 검색창(타이틀)에서 온 값. category: 카테고리 버튼에서 온 값.
     * 둘 중 하나에 값이 들어오면 API에 해당 파라미터로 전달합니다.
     */
    fun loadProducts(searchQuery: String? = null, category: String? = null) {
        _loading.value = true
        _error.value = null
        viewModelScope.launch {
            val result = repository.getProductList(searchQuery = searchQuery, category = category, sort = null)
            result.onSuccess { list ->
                _products.value = list
            }
            result.onFailure { t ->
                _products.value = emptyList()
                _error.value = t.localizedMessage ?: "알 수 없는 오류"
            }
            _loading.value = false
        }
    }
}

/** ViewModel Factory to inject repository */
class SearchViewModelFactory(private val repository: ProductRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            return SearchViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
