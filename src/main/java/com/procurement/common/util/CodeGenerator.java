package com.procurement.common.util;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Component
public class CodeGenerator {

    private final StringRedisTemplate redisTemplate;

    public CodeGenerator(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String generate(String prefix) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String key = "seq:" + prefix + ":" + date;
        Long seq = redisTemplate.opsForValue().increment(key);
        if (seq != null && seq == 1) {
            redisTemplate.expire(key, 24, TimeUnit.HOURS);
        }
        return String.format("%s-%s-%04d", prefix, date, seq);
    }
}
