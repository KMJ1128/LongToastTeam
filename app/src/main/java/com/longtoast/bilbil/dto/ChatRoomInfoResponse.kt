package com.longtoast.bilbil.dto

data class ChatRoomInfoResponse(
    val roomId: Int,
    val item: ItemDTO,
    val lender: UserDTO,
    val borrower: UserDTO
) {
    data class ItemDTO(
        val id: Int,
        val title: String,
        val price: Int,
        val imageUrl: String?
    )

    data class UserDTO(
        val id: Int,
        val nickname: String?,
        val profileImageUrl: String?
    )
}
