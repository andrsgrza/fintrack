package com.fintrack.app.service.mapper;

import static com.fintrack.app.domain.UserDashboardPreferenceAsserts.*;
import static com.fintrack.app.domain.UserDashboardPreferenceTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserDashboardPreferenceMapperTest {

    private UserDashboardPreferenceMapper userDashboardPreferenceMapper;

    @BeforeEach
    void setUp() {
        userDashboardPreferenceMapper = new UserDashboardPreferenceMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getUserDashboardPreferenceSample1();
        var actual = userDashboardPreferenceMapper.toEntity(userDashboardPreferenceMapper.toDto(expected));
        assertUserDashboardPreferenceAllPropertiesEquals(expected, actual);
    }
}
