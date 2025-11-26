package com.longtoast.bilbil_api.repository;

import com.longtoast.bilbil_api.domain.Item;
import com.longtoast.bilbil_api.domain.ItemImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ItemImageRepository extends JpaRepository<ItemImage, Integer> {

    /**
     * 1️⃣ 특정 아이템의 메인 이미지(is_main = TRUE) 1개 조회
     */
    Optional<ItemImage> findByItemAndIsMain(Item item, Boolean isMain);

    /**
     * 2️⃣ 특정 아이템의 모든 이미지 조회 (메인 이미지 먼저)
     */
    List<ItemImage> findByItemOrderByIsMainDesc(Item item);

    /**
     * 3️⃣ 여러 아이템의 이미지 한 번에 조회 (목록 조회 최적화용)
     */
    @Query("SELECT i FROM ItemImage i WHERE i.item IN :items ORDER BY i.isMain DESC")
    List<ItemImage> findByItemInOrderByIsMainDesc(List<Item> items);

    /**
     * 4️⃣ 특정 아이템의 첫 번째 이미지만 가져오기
     */
    @Query(value = "SELECT i FROM ItemImage i WHERE i.item = :item ORDER BY i.isMain DESC")
    Optional<ItemImage> findFirstByItemOrderByIsMainDesc(Item item);
}
