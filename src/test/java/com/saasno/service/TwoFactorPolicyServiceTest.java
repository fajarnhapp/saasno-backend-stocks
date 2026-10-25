package com.saasno.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.saasno.entity.UserEntity;
import com.saasno.repository.ConfigurationRepository;
import com.saasno.utility.constant.ConfigurationKey;

class TwoFactorPolicyServiceTest {

	private ConfigurationRepository configuration;
	private TwoFactorPolicyService service;

	@BeforeEach
	void setUp() {
		configuration = mock(ConfigurationRepository.class);
		service = new TwoFactorPolicyService();
		ReflectionTestUtils.setField(service, "configurationRepository", configuration);
	}

	@Test
	void isRequiredByDefaultAndWhenSetToTrue() {
		assertTrue(service.isRequired());
		when(configuration.get(ConfigurationKey.TWO_FACTOR_REQUIRED)).thenReturn("true");
		assertTrue(service.isRequired());
	}

	@Test
	void canBeSwitchedOff() {
		when(configuration.get(ConfigurationKey.TWO_FACTOR_REQUIRED)).thenReturn("false");
		assertFalse(service.isRequired());
		assertFalse(service.isSetupRequired(new UserEntity()));
	}

	@Test
	void setupIsOnlyRequiredForAccountsWithoutTwoFactor() {
		UserEntity without = new UserEntity();
		UserEntity with = new UserEntity();
		with.setTwoFactorAuth(true);
		assertTrue(service.isSetupRequired(without));
		assertFalse(service.isSetupRequired(with));
	}
}
