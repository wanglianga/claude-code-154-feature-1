package com.community.market.domain.entity;

import com.community.market.domain.enums.UserRole;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sys_user")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String password;

    /** 摊主或工作人员姓名 / 组织名 */
    @Column(length = 50)
    private String realName;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    /** 摊主信用分，初始 100，受履约/投诉/异常影响 */
    private Integer creditScore = 100;

    /** 下次报名是否需要提前复核（现场违规/换品处理后由平台标记） */
    private boolean nextApplyReviewRequired = false;

    /** 食品风险等级：0 无 / 1 关注（下架处理） / 2 高风险（食品安全风险事件） */
    private Integer foodRiskLevel = 0;

    /** 是否为亲子摊主的历史标记（便于招商） */
    private boolean enabled = true;

    private LocalDateTime createdAt = LocalDateTime.now();
}
