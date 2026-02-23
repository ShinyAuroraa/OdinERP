package com.odin.wms.domain.repository;

import com.odin.wms.domain.entity.InventoryCountItem;
import com.odin.wms.domain.entity.InventoryCountItem.ItemCountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryCountItemRepository extends JpaRepository<InventoryCountItem, UUID> {

    @Query("""
            SELECT i FROM InventoryCountItem i
            LEFT JOIN FETCH i.location l
            LEFT JOIN FETCH i.product p
            LEFT JOIN FETCH i.lot lot
            WHERE i.inventoryCountId = :countId
            ORDER BY l.code ASC
            """)
    Page<InventoryCountItem> findByInventoryCountIdWithDetails(
            @Param("countId") UUID countId, Pageable pageable);

    List<InventoryCountItem> findByInventoryCountIdAndStatus(UUID countId, ItemCountStatus status);

    List<InventoryCountItem> findByInventoryCountIdAndStatusIn(
            UUID countId, List<ItemCountStatus> statuses);

    long countByInventoryCountId(UUID countId);

    Optional<InventoryCountItem> findByIdAndInventoryCountId(UUID itemId, UUID countId);
}
