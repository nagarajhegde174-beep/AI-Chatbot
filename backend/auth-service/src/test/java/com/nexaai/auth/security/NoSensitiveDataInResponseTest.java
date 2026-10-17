package com.nexaai.auth.security;

import com.nexaai.auth.dto.response.AuthResponse;
import com.nexaai.auth.dto.response.RegisterResponse;
import com.nexaai.auth.dto.response.ServiceInfoResponse;
import com.nexaai.auth.dto.response.TokenIntrospectResponse;
import com.nexaai.auth.dto.response.UserInfoResponse;
import com.nexaai.auth.dto.response.UserStatusResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class NoSensitiveDataInResponseTest {

    private final List<Class<?>> responseClasses = List.of(
            RegisterResponse.class,
            AuthResponse.class,
            AuthResponse.UserSummary.class,
            UserInfoResponse.class,
            UserStatusResponse.class,
            TokenIntrospectResponse.class,
            ServiceInfoResponse.class
    );

    @Test
    void testNoPasswordFieldsInResponseDtos() {
        for (Class<?> clazz : responseClasses) {
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                String fieldName = field.getName().toLowerCase();
                assertFalse(fieldName.contains("password"),
                        "Response class " + clazz.getSimpleName() + " contains password-related field: " + field.getName());
                assertFalse(fieldName.contains("secret"),
                        "Response class " + clazz.getSimpleName() + " contains secret-related field: " + field.getName());
                assertFalse(fieldName.contains("privatekey"),
                        "Response class " + clazz.getSimpleName() + " contains privatekey field: " + field.getName());
            }
        }
    }
}
