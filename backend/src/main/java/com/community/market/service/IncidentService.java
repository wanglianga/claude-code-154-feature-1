package com.community.market.service;

import com.community.market.domain.entity.Application;
import com.community.market.domain.entity.Incident;
import com.community.market.domain.entity.User;
import com.community.market.domain.enums.IncidentStatus;
import com.community.market.domain.enums.IncidentType;
import com.community.market.repository.ApplicationRepository;
import com.community.market.repository.IncidentRepository;
import com.community.market.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    @Lazy
    private final CreditService creditService;

    @Transactional
    public Incident raise(Long eventId, Long applicationId, IncidentType type, String description, Integer severity, Long raisedBy) {
        Incident i = new Incident();
        i.setEventId(eventId);
        i.setApplicationId(applicationId);
        i.setType(type);
        i.setDescription(description);
        i.setSeverity(severity == null ? 2 : severity);
        i.setStatus(IncidentStatus.OPEN);
        i.setRaisedBy(raisedBy);
        Incident saved = incidentRepository.save(i);

        // 食品安全风险事件：升级摊主食品风险等级，影响其后续食品类报名，并扣信用
        if (type == IncidentType.FOOD_RISK && applicationId != null) {
            Application a = applicationRepository.findById(applicationId).orElse(null);
            if (a != null) {
                User vendor = userRepository.findById(a.getVendorId()).orElse(null);
                if (vendor != null) {
                    vendor.setFoodRiskLevel(2);
                    vendor.setNextApplyReviewRequired(true);
                    userRepository.save(vendor);
                }
                creditService.adjust(a.getVendorId(), eventId, applicationId, -15,
                        "发生食品安全风险事件，后续食品类报名受限");
            }
        }
        return saved;
    }

    @Transactional
    public Incident resolve(Long id, String resolution, Long resolvedBy) {
        Incident i = incidentRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("异常不存在"));
        i.setStatus(IncidentStatus.RESOLVED);
        i.setResolution(resolution);
        i.setResolvedBy(resolvedBy);
        i.setResolvedAt(LocalDateTime.now());
        return incidentRepository.save(i);
    }

    public List<Incident> list(Long eventId) {
        return incidentRepository.findByEventIdOrderByIdDesc(eventId);
    }
}
