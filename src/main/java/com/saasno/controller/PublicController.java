package com.saasno.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.AppSettingService;
import com.saasno.service.EmailVerificationService;
import com.saasno.utility.dto.person.VerifyEmailDTO;
import com.saasno.service.HealthService;
import com.saasno.service.PasswordPolicyService;
import com.saasno.service.PasswordResetService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.appsetting.AppSettingDTO;
import com.saasno.utility.dto.password.ForgotPasswordDTO;
import com.saasno.utility.dto.password.PasswordPolicyDTO;
import com.saasno.utility.dto.password.ResetPasswordDTO;
import com.saasno.utility.model.ResponseModel;

import io.swagger.v3.oas.annotations.tags.Tag;

// Endpoints that need no login (the whole /api/public path skips the session check).
@Tag(name = "Public", description = "Endpoints that need no login")
@RestController
@RequestMapping(Path.API_PUBLIC)
public class PublicController {

	@Autowired
	private PasswordResetService passwordResetService;

	@Autowired
	private EmailVerificationService emailVerificationService;

	@Autowired
	private com.saasno.service.PrivacyService privacyService;

	@Autowired
	private PasswordPolicyService passwordPolicyService;

	@Autowired
	private HealthService healthService;

	@Autowired
	private AppSettingService appSettingService;

	@Autowired
	private com.saasno.repository.ConfigurationRepository configurationRepository;

	@Value("${app.version:unknown}")
	private String version;

	@PostMapping("/password/forgot")
	public ResponseModel<Void> forgotPassword(@RequestBody ForgotPasswordDTO request) {
		passwordResetService.forgot(request.username());
		return new ResponseModel<Void>().success();
	}

	@PostMapping("/password/reset")
	public ResponseModel<Void> resetPassword(@RequestBody ResetPasswordDTO request) {
		passwordResetService.reset(request.token(), request.password());
		return new ResponseModel<Void>().success();
	}

	@PostMapping("/email/verify")
	public ResponseModel<Void> verifyEmail(@RequestBody VerifyEmailDTO request) {
		emailVerificationService.confirm(request.token());
		return new ResponseModel<Void>().success();
	}

	// The privacy policy (the latest published version, or the built-in text in the asked language). Anyone may read it.
	@GetMapping("/privacy")
	public ResponseModel<com.saasno.utility.dto.privacy.PrivacyDTO.Policy> privacy(
			@org.springframework.web.bind.annotation.RequestParam(value = "lang", defaultValue = "en") String language) {
		return new ResponseModel<com.saasno.utility.dto.privacy.PrivacyDTO.Policy>().success(privacyService.current(language));
	}

	@GetMapping("/password/policy")
	public ResponseModel<PasswordPolicyDTO> passwordPolicy() {
		return new ResponseModel<PasswordPolicyDTO>().success(passwordPolicyService.policy());
	}

	// What the login page and the theme need before anyone is signed in: name, brand colour and whether there is a logo.
	@GetMapping("/app-setting")
	public ResponseModel<AppSettingDTO> appSetting() {
		return new ResponseModel<AppSettingDTO>().success(appSettingService.read());
	}

	// The origins the frontend answers cross-origin (CORS) calls from. Not secret: every browser request already shows its own origin.
	@GetMapping("/allowed-origins")
	public ResponseModel<java.util.List<String>> allowedOrigins() {
		return new ResponseModel<java.util.List<String>>().success(
				com.saasno.utility.OriginUtility.parse(configurationRepository.get(com.saasno.utility.constant.ConfigurationKey.ALLOWED_ORIGINS)));
	}

	@GetMapping("/app-logo")
	public ResponseEntity<byte[]> appLogo() {
		return appSettingService.logo();
	}

	// 200 when every dependency is up, 503 otherwise (used by the container health check and monitoring).
	@GetMapping("/health")
	public ResponseEntity<Map<String, Object>> health() {
		Map<String, String> components = healthService.check();
		boolean up = HealthService.isUp(components);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("status", up ? "UP" : "DOWN");
		body.put("version", version);
		body.put("components", components);
		return ResponseEntity.status(up ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
	}

}
