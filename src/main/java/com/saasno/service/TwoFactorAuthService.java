package com.saasno.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.saasno.entity.SessionEntity;
import com.saasno.entity.UserEntity;
import com.saasno.repository.UserRepository;
import com.saasno.utility.RecoveryCodeUtility;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.cryptography.Totp;
import com.saasno.utility.dto.user.RecoveryCodesDTO;
import com.saasno.utility.dto.user.TwoFactorAuthDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.verification.StringVerification;

import jakarta.transaction.Transactional;

@Service
public class TwoFactorAuthService {

	private static final String ISSUER = "Saasno";

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private SessionUtility sessionUtility;

	@Autowired
	private SessionService sessionService;

	@Autowired
	private TwoFactorPolicyService twoFactorPolicyService;

	@Transactional
	public ResponseModel<TwoFactorAuthDTO> setup() {
		UserEntity user = currentUser();
		if (Boolean.TRUE.equals(user.getTwoFactorAuth())) {
			throw new ApiException("409", "Two-factor authentication is already active");
		}

		String secret = Totp.generateSecret();
		user.setTwoFactorAuthSecret(secret);
		userRepository.store(user);

		String setupUri = Totp.buildUri(ISSUER, user.getUsername(), secret);
		return new ResponseModel<TwoFactorAuthDTO>().success(new TwoFactorAuthDTO(setupUri));
	}

	@Transactional
	public ResponseModel<RecoveryCodesDTO> enable(String code) {
		UserEntity user = currentUser();
		if (StringVerification.nullOrEmpty(user.getTwoFactorAuthSecret())) {
			throw new ApiException("400", "Start the OTP setup process first");
		}
		if (!Totp.verify(user.getTwoFactorAuthSecret(), code)) {
			throw new ApiException("400", "Invalid OTP code");
		}

		user.setTwoFactorAuth(true);
		List<String> codes = RecoveryCodeUtility.generate();
		user.storeRecoveryHashes(RecoveryCodeUtility.hashAll(codes));
		userRepository.store(user);

		// The current session is no longer restricted to the setup.
		SessionEntity session = sessionUtility.get();
		if (session != null && session.isTwoFactorSetupRequired()) {
			session.setTwoFactorSetupRequired(false);
			sessionService.create(session);
		}
		return new ResponseModel<RecoveryCodesDTO>().success(new RecoveryCodesDTO(codes));
	}

	// Replaces all recovery codes (the old ones stop working). Needs a current authenticator code.
	@Transactional
	public ResponseModel<RecoveryCodesDTO> regenerateRecoveryCodes(String code) {
		UserEntity user = currentUser();
		if (!Boolean.TRUE.equals(user.getTwoFactorAuth())) {
			throw new ApiException("409", "Two-factor authentication is not active yet");
		}
		if (!Totp.verify(user.getTwoFactorAuthSecret(), code)) {
			throw new ApiException("400", "Invalid OTP code");
		}
		List<String> codes = RecoveryCodeUtility.generate();
		user.storeRecoveryHashes(RecoveryCodeUtility.hashAll(codes));
		userRepository.store(user);
		return new ResponseModel<RecoveryCodesDTO>().success(new RecoveryCodesDTO(codes));
	}

	@Transactional
	public ResponseModel<Void> disable(String code) {
		UserEntity user = currentUser();
		if (!Boolean.TRUE.equals(user.getTwoFactorAuth())) {
			throw new ApiException("409", "Two-factor authentication is not active yet");
		}
		if (twoFactorPolicyService.isRequired()) {
			throw new ApiException("409", "Two-factor authentication is required for all accounts and cannot be turned off");
		}
		if (!Totp.verify(user.getTwoFactorAuthSecret(), code)) {
			throw new ApiException("400", "Invalid OTP code");
		}

		user.setTwoFactorAuth(false);
		user.setTwoFactorAuthSecret(null);
		user.clearRecoveryCodes();
		userRepository.store(user);
		return new ResponseModel<Void>().success();
	}

	private UserEntity currentUser() {
		UserEntity user = userRepository.findByUsername(sessionUtility.getUsername());
		if (user == null) {
			throw new ApiException("404", "User not found");
		}
		return user;
	}

}
