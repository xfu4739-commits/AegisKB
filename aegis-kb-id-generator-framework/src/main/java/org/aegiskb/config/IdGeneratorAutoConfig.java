package org.aegiskb.config;

import org.aegiskb.toolkit.SnowflakeIdGenerator;
import org.aegiskb.toolkit.WorkAndDataCenterIdHandler;
import org.aegiskb.toolkit.WorkDataCenterId;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

public class IdGeneratorAutoConfig {

    @Bean
    public WorkAndDataCenterIdHandler workAndDataCenterIdHandler(StringRedisTemplate stringRedisTemplate){
        return new WorkAndDataCenterIdHandler(stringRedisTemplate);
    }

    @Bean
    public WorkDataCenterId workDataCenterId(WorkAndDataCenterIdHandler workAndDataCenterIdHandler){
        return workAndDataCenterIdHandler.getWorkAndDataCenterId();
    }

    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator(WorkDataCenterId workDataCenterId){
        return new SnowflakeIdGenerator(workDataCenterId);
    }
}
