package com.procurement.module.system.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserVO {
    private Long id;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private Long supplierId;
    private Integer status;
    private List<String> roles;
    private LocalDateTime createTime;
}
