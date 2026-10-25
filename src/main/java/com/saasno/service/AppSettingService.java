package com.saasno.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.saasno.repository.ConfigurationRepository;
import com.saasno.utility.ImageProcessor;
import com.saasno.utility.constant.ConfigurationKey;
import com.saasno.utility.dto.appsetting.AppSettingDTO;
import com.saasno.utility.dto.appsetting.AppSettingUpdateDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.model.ResponseModel;

import java.io.IOException;
import java.time.Duration;

// How the app presents itself: name, brand colour (both kept in the configuration table) and logo (branding/logo.png).
@Service
public class AppSettingService {

	public static final String DEFAULT_NAME = "Saasno";
	static final int DEFAULT_HUE = 293;
	static final double DEFAULT_CHROMA = 1.0;

	private static final String FOLDER = "branding";
	private static final String LOGO = "logo.png";
	private static final int LOGO_MAX_SIDE = 512;
	private static final long MAX_BYTES = 2L * 1024 * 1024;

	@Autowired
	private ConfigurationRepository configurationRepository;

	@Autowired
	private ConfigurationService configurationService;

	@Autowired
	private FileStorageService fileStorage;

	@Autowired
	private MaintenanceModeService maintenanceModeService;

	public AppSettingDTO read() {
		long logoVersion = fileStorage.version(FOLDER, LOGO);
		return new AppSettingDTO(appName(), hue(), chroma(), logoVersion > 0, logoVersion, maintenanceModeService.status());
	}

	public String appName() {
		String value = configurationRepository.get(ConfigurationKey.APP_NAME);
		return value == null || value.isBlank() ? DEFAULT_NAME : value.trim();
	}

	public int hue() {
		try {
			String value = configurationRepository.get(ConfigurationKey.BRAND_HUE);
			return value == null ? DEFAULT_HUE : Math.max(0, Math.min(360, (int) Math.round(Double.parseDouble(value.trim()))));
		} catch (NumberFormatException e) {
			return DEFAULT_HUE;
		}
	}

	public double chroma() {
		try {
			String value = configurationRepository.get(ConfigurationKey.BRAND_CHROMA);
			return value == null ? DEFAULT_CHROMA : Math.max(0.3, Math.min(1.2, Double.parseDouble(value.trim())));
		} catch (NumberFormatException e) {
			return DEFAULT_CHROMA;
		}
	}

	// One transaction: if any value is rejected, none of them is saved.
	@Transactional
	public ResponseModel<AppSettingDTO> update(AppSettingUpdateDTO request) {
		if (request == null) {
			throw new ApiException("400", "Request body is required");
		}
		if (request.appName() != null) {
			configurationService.set(ConfigurationKey.APP_NAME, request.appName());
		}
		if (request.brandHue() != null) {
			configurationService.set(ConfigurationKey.BRAND_HUE, request.brandHue());
		}
		if (request.brandChroma() != null) {
			configurationService.set(ConfigurationKey.BRAND_CHROMA, request.brandChroma());
		}
		return new ResponseModel<AppSettingDTO>().success(read());
	}

	public ResponseModel<AppSettingDTO> uploadLogo(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ApiException("400", "Choose a picture first");
		}
		if (file.getSize() > MAX_BYTES) {
			throw new ApiException("413", "The picture is too large (maximum 2 MB)");
		}
		try {
			fileStorage.save(FOLDER, LOGO, ImageProcessor.fit(file.getBytes(), LOGO_MAX_SIDE));
		} catch (IOException e) {
			throw new ApiException("400", "The file could not be read");
		}
		return new ResponseModel<AppSettingDTO>().success(read());
	}

	public ResponseModel<AppSettingDTO> deleteLogo() {
		fileStorage.delete(FOLDER, LOGO);
		return new ResponseModel<AppSettingDTO>().success(read());
	}

	public ResponseEntity<byte[]> logo() {
		byte[] png = fileStorage.read(FOLDER, LOGO);
		if (png == null) {
			throw new ApiException("404", "No logo");
		}
		return ResponseEntity.ok()
				.contentType(MediaType.IMAGE_PNG)
				.header("X-Content-Type-Options", "nosniff")
				.cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
				.body(png);
	}

}
