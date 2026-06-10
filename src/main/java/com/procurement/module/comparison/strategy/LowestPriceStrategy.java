package com.procurement.module.comparison.strategy;

import com.procurement.module.comparison.entity.ComparisonItem;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component("LOWEST_PRICE")
public class LowestPriceStrategy implements ComparisonStrategy {

    @Override
    public List<ComparisonItem> evaluate(List<ComparisonItem> items) {
        Map<Long, List<ComparisonItem>> byMaterial = items.stream()
                .collect(Collectors.groupingBy(ComparisonItem::getMaterialId));

        for (Map.Entry<Long, List<ComparisonItem>> entry : byMaterial.entrySet()) {
            List<ComparisonItem> group = entry.getValue();
            group.sort(Comparator.comparing(ComparisonItem::getUnitPrice));
            for (int i = 0; i < group.size(); i++) {
                group.get(i).setPriceRank(i + 1);
                group.get(i).setIsSelected(i == 0 ? 1 : 0);
            }
        }
        return items;
    }
}
