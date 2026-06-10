package com.procurement.module.comparison.strategy;

import com.procurement.module.comparison.entity.ComparisonItem;
import java.util.List;

public interface ComparisonStrategy {
    List<ComparisonItem> evaluate(List<ComparisonItem> items);
}
