package com.saasno.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.saasno.entity.UserEntity;
import com.saasno.repository.ConfigurationRepository;
import com.saasno.utility.constant.ConfigurationKey;

// Whether every account has to use two-factor authentication (TWO_FACTOR_REQUIRED, default on). An account without it is
// sent to the setup screen right after login and cannot use anything else until it is done.
@Service
public class TwoFactorPolicyService {

	@Autowired
	private ConfigurationRepository configurationRepository;

	public boolean isRequired() {
		return !"false".equalsIgnoreCase(configurationRepository.get(ConfigurationKey.TWO_FACTOR_REQUIRED));
	}

	public boolean isSetupRequired(UserEntity user) {
		return isRequired() && !Boolean.TRUE.equals(user.getTwoFactorAuth());
	}
}
