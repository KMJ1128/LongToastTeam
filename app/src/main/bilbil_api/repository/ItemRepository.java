package com.longtoast.bilbil_api.repository;

import com.longtoast.bilbil_api.domain.Item;
import com.longtoast.bilbil_api.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {

    // 판매자가 등록한 아이템 목록
    List<Item> findByUser_Id(Integer userId);

    // 대여자가 빌린 아이템 목록
    List<Item> findByRenter_Id(Integer renterId);

    // 특정 상태의 아이템 목록 (예: AVAILABLE, RENTED)
    List<Item> findByStatus(Item.Status status);

    // 같은 상품을 빌린 사람이 있는지 확인하는 용도 (옵션)
    List<Item> findByIdIn(List<Long> ids);
}
