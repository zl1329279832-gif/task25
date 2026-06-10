package com.procurement.service;

import com.procurement.common.Result;
import java.util.Map;

public interface AuthService {
    Map<String, Object> login(String username, String password);
}
