package com.procurement.module.comparison.strategy;

import com.procurement.module.comparison.entity.ComparisonItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Component("COMPREHENSIVE")
public class ComprehensiveScoreStrategy implements ComparisonStrategy {

    @Override
    public List<ComparisonItem> evaluate(List<ComparisonItem> items) {
        Map<Long, List<ComparisonItem>> byMaterial = items.stream()
                .collect(Collectors.groupingBy(ComparisonItem::getMaterialId));

        for (Map.Entry<Long, List<ComparisonItem>> entry : byMaterial.entrySet()) {
            List<ComparisonItem> group = entry.getValue();
            BigDecimal minPrice = group.stream()
                    .map(ComparisonItem::getUnitPrice)
                    .min(Comparator.naturalOrder())
                    .orElse(BigDecimal.ONE);

            for (ComparisonItem item : group) {
                // price score (70%): lower is better
                BigDecimal priceScore = minPrice.divide(item.getUnitPrice(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(70));
                // base score (30%): placeholder
                BigDecimal baseScore = BigDecimal.valueOf(30);
                item.setScore(priceScore.add(baseScore).setScale(2, RoundingMode.HALF_UP));
            }

            group.sort(Comparator.comparing(ComparisonItem::getScore).reversed());
            for (int i = 0; i < group.size(); i++) {
                group.get(i).setPriceRank(i + 1);
                group.get(i).setIsSelected(i == 0 ? 1 : 0);
            }
        }
        return items;
    }
}
