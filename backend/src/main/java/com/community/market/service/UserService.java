package com.community.market.service;

import com.community.market.common.BizException;
import com.community.market.domain.entity.User;
import com.community.market.domain.enums.UserRole;
import com.community.market.dto.AuthDtos;
import com.community.market.repository.UserRepository;
import com.community.market.security.CurrentUser;
import com.community.market.security.TokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TokenStore tokenStore;

    @Transactional
    public AuthDtos.LoginResponse login(String username, String password) {
        User u = userRepository.findByUsername(username)
                .orElseThrow(() -> new BizException("用户名或密码错误"));
        if (!u.isEnabled() || !u.getPassword().equals(password)) {
            throw new BizException("用户名或密码错误");
        }
        String token = tokenStore.issue(u.getId(), u.getUsername(), u.getRole());
        return new AuthDtos.LoginResponse(token, toResponse(u));
    }

    @Transactional
    public User register(AuthDtos.RegisterRequest req) {
        if (userRepository.findByUsername(req.username()).isPresent()) {
            throw new BizException("用户名已存在");
        }
        User u = new User();
        u.setUsername(req.username());
        u.setPassword(req.password());
        u.setRealName(req.realName());
        u.setPhone(req.phone());
        u.setRole(UserRole.VENDOR);
        u.setCreditScore(100);
        return userRepository.save(u);
    }

    /** 管理员创建摊主/工作人员账号 */
    @Transactional
    public User adminCreate(UserRole role, AuthDtos.RegisterRequest req) {
        if (userRepository.findByUsername(req.username()).isPresent()) {
            throw new BizException("用户名已存在");
        }
        User u = new User();
        u.setUsername(req.username());
        u.setPassword(req.password());
        u.setRealName(req.realName());
        u.setPhone(req.phone());
        u.setRole(role == null ? UserRole.VENDOR : role);
        u.setCreditScore(100);
        return userRepository.save(u);
    }

    public List<User> list(UserRole role) {
        return role == null ? userRepository.findAll() : userRepository.findByRoleOrderByIdAsc(role);
    }

    public AuthDtos.UserResponse toResponse(User u) {
        return new AuthDtos.UserResponse(u.getId(), u.getUsername(), u.getRealName(), u.getPhone(),
                u.getRole().name(), u.getCreditScore(), u.isEnabled(),
                u.isFoodRiskFlag(), u.getFoodRiskCount(), u.isRequirePreReview(), u.getPreReviewReason());
    }

    public CurrentUser.SessionUser current() {
        return CurrentUser.get();
    }
}
