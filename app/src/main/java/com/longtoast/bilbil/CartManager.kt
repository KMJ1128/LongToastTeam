package com.longtoast.bilbil

import com.longtoast.bilbil.dto.ProductDTO

object CartManager {
    // 장바구니에 담긴 상품 리스트
    private val cartItems = mutableListOf<ProductDTO>()

    // 상품 추가
    fun addItem(product: ProductDTO) {
        cartItems.add(product)
    }

    // 상품 삭제
    fun removeItem(position: Int) {
        if (position in cartItems.indices) {
            cartItems.removeAt(position)
        }
    }

    // 전체 상품 가져오기
    fun getItems(): List<ProductDTO> {
        return cartItems
    }

    // 총 금액 계산
    fun getTotalPrice(): Int {
        return cartItems.sumOf { it.price ?: 0 }
    }

    // 장바구니 비우기 (결제 후 등)
    fun clear() {
        cartItems.clear()
    }
}