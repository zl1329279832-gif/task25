package com.procurement.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.procurement.common.enums.OrderStatus;
import com.procurement.module.order.entity.PurchaseOrder;
import com.procurement.module.order.mapper.PurchaseOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Reminds about orders that have been APPROVED but not confirmed by supplier for over 48 hours.
 * Runs daily at 9:00 AM.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutReminderJob {

    private static final int TIMEOUT_HOURS = 48;
    private static final String REMINDER_KEY_PREFIX = "order:reminder:";

    private final PurchaseOrderMapper orderMapper;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(cron = "0 0 9 * * ?")
    public void remindUnconfirmedOrders() {
        log.info("OrderTimeoutReminderJob: checking for unconfirmed orders...");

        LocalDateTime threshold = LocalDateTime.now().minusHours(TIMEOUT_HOURS);

        // Find orders that are APPROVED and created more than 48 hours ago
        LambdaQueryWrapper<PurchaseOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PurchaseOrder::getStatus, OrderStatus.APPROVED.name())
                .le(PurchaseOrder::getCreateTime, threshold);

        List<PurchaseOrder> timeoutOrders = orderMapper.selectList(wrapper);

        int reminded = 0;
        for (PurchaseOrder order : timeoutOrders) {
            String reminderKey = REMINDER_KEY_PREFIX + order.getId();

            // Check if we already sent a reminder today
            if (Boolean.TRUE.equals(redisTemplate.hasKey(reminderKey))) {
                continue;
            }

            // Log the reminder (in production, this would send notifications)
            log.warn("OrderTimeoutReminderJob: Order {} (supplier={}) has not been confirmed for over {}h. Created at: {}",
                    order.getOrderNo(),
                    order.getSupplierId(),
                    TIMEOUT_HOURS,
                    order.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

            // Store notification in Redis to track sent reminders
            redisTemplate.opsForValue().set(reminderKey,
                    "reminded:" + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    24, TimeUnit.HOURS);

            reminded++;
        }

        log.info("OrderTimeoutReminderJob: found {} timeout orders, sent {} reminders",
                timeoutOrders.size(), reminded);
    }
}
