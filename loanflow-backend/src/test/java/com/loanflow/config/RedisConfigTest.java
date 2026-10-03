package com.loanflow.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@org.springframework.boot.test.context.SpringBootTest(classes = {
        com.loanflow.config.RedisConfig.class
})
@ActiveProfiles("local")
public class RedisConfigTest {

    @org.springframework.boot.test.mock.mockito.MockBean
    private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    public void contextLoads() {
        assertThat(redisTemplate).isNotNull();
    }

    @Test
    public void verifySerializers() {
        assertThat(redisTemplate.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(redisTemplate.getHashKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(redisTemplate.getValueSerializer()).isInstanceOf(GenericJackson2JsonRedisSerializer.class);
        assertThat(redisTemplate.getHashValueSerializer()).isInstanceOf(GenericJackson2JsonRedisSerializer.class);
    }

    @Test
    public void testSerializationOfComplexObjects() {
        GenericJackson2JsonRedisSerializer serializer = (GenericJackson2JsonRedisSerializer) redisTemplate.getValueSerializer();
        
        com.loanflow.product.dto.LoanProductDetailResponse response = new com.loanflow.product.dto.LoanProductDetailResponse();
        response.setId(1L);
        response.setName("Test");
        response.setInterestRate(new java.math.BigDecimal("5.99"));
        
        byte[] serialized = serializer.serialize(response);
        assertThat(serialized).isNotNull();
        
        Object deserialized = serializer.deserialize(serialized);
        assertThat(deserialized).isInstanceOf(com.loanflow.product.dto.LoanProductDetailResponse.class);
        
        com.loanflow.product.dto.LoanProductDetailResponse casted = (com.loanflow.product.dto.LoanProductDetailResponse) deserialized;
        assertThat(casted.getName()).isEqualTo("Test");
        assertThat(casted.getInterestRate()).isEqualTo(new java.math.BigDecimal("5.99"));
    }
}
