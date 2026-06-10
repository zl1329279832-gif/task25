package com.procurement.security;

import com.procurement.common.exception.BizException;
import com.procurement.common.exception.ErrorCode;
import com.procurement.module.system.entity.SysUser;
import com.procurement.module.system.mapper.SysRoleMapper;
import com.procurement.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userMapper.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }
        if (user.getStatus() != 1) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }
        List<String> roles = roleMapper.findRoleCodesByUserId(user.getId());
        return new SecurityUser(user, roles);
    }
}
