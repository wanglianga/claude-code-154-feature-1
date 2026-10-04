package com.community.market.repository;

import com.community.market.domain.entity.GoodsChangeRequest;
import com.community.market.domain.enums.ChangeReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GoodsChangeRequestRepository extends JpaRepository<GoodsChangeRequest, Long> {
    List<GoodsChangeRequest> findByEventIdOrderByIdDesc(Long eventId);
    List<GoodsChangeRequest> findByApplicationIdOrderByIdDesc(Long applicationId);
    List<GoodsChangeRequest> findByVendorIdOrderByIdDesc(Long vendorId);
    List<GoodsChangeRequest> findByEventIdAndStatusOrderByIdDesc(Long eventId, ChangeReviewStatus status);
}
