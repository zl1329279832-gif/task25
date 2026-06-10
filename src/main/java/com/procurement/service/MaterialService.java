package com.procurement.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.procurement.entity.Material;

public interface MaterialService {
    Material create(Material material);
    Material update(Long id, Material material);
    Material getById(Long id);
    Page<Material> list(String keyword, String category, int page, int size);
}
