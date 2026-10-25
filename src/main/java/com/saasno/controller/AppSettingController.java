package com.saasno.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.saasno.service.AppSettingService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.appsetting.AppSettingDTO;
import com.saasno.utility.dto.appsetting.AppSettingUpdateDTO;
import com.saasno.utility.model.ResponseModel;

// Admin side of the application settings (name, brand colour, logo). The public read side is in PublicController.
@RestController
@RequestMapping(Path.API_APP_SETTING)
public class AppSettingController {

	@Autowired
	private AppSettingService appSettingService;

	@GetMapping
	public ResponseModel<AppSettingDTO> read() {
		return new ResponseModel<AppSettingDTO>().success(appSettingService.read());
	}

	@PutMapping
	public ResponseModel<AppSettingDTO> update(@RequestBody AppSettingUpdateDTO request) {
		return appSettingService.update(request);
	}

	@PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseModel<AppSettingDTO> uploadLogo(@RequestParam("file") MultipartFile file) {
		return appSettingService.uploadLogo(file);
	}

	@DeleteMapping("/logo")
	public ResponseModel<AppSettingDTO> deleteLogo() {
		return appSettingService.deleteLogo();
	}

}
