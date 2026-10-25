package com.saasno.service;

import java.math.BigInteger;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.saasno.entity.LoginHistoryEntity;
import com.saasno.entity.UserEntity;
import com.saasno.repository.LoginHistoryRepository;
import com.saasno.repository.UserRepository;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

// The sign-in history: every attempt (successful or not) with time, address and device. Admins see all of it; users see their own.
@Service
public class LoginHistoryService {

	static final int OWN_LIMIT = 20;

	@Autowired
	private LoginHistoryRepository loginHistoryRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private SessionUtility sessionUtility;

	// In its own transaction, and never throwing: keeping the history must not make a sign-in fail.
	@Transactional(TxType.REQUIRES_NEW)
	public void record(UserEntity user, String typed, boolean success, String reason, String ip, String userAgent) {
		try {
			String name = typed != null && !typed.isBlank() ? typed.trim() : user != null ? user.getUsername() : "";
			loginHistoryRepository.store(new LoginHistoryEntity(user == null ? null : user.getId(), name, success, reason, ip, userAgent));
		} catch (Exception e) {
			Log.error("Failed to record the login history: " + e.getMessage());
		}
	}

	@Transactional
	public ResponseEntity<DataResult<LoginHistoryEntity>> read(HttpServletRequest request) {
		try {
			return new ResponseEntity<>(loginHistoryRepository.dataTable(new DataFilter(request)), HttpStatus.OK);
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
			return new ResponseEntity<>(new DataResult<LoginHistoryEntity>(BigInteger.ZERO, BigInteger.ZERO), HttpStatus.OK);
		}
	}

	@Transactional
	public ResponseModel<List<LoginHistoryEntity>> mine() {
		UserEntity user = userRepository.findByUsername(sessionUtility.getUsername());
		if (user == null) {
			throw new ApiException("404", "User not found");
		}
		return new ResponseModel<List<LoginHistoryEntity>>().success(loginHistoryRepository.latest(user.getId(), OWN_LIMIT));
	}
}
