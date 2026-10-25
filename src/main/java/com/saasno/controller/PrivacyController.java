package com.saasno.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.PrivacyService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.privacy.PrivacyDTO;
import com.saasno.utility.model.ResponseModel;

@RestController
@RequestMapping(Path.API_PRIVACY)
public class PrivacyController {

	@Autowired
	private PrivacyService privacyService;

	@GetMapping("/policy")
	public ResponseModel<PrivacyDTO.Admin> admin() {
		return privacyService.admin();
	}

	@PostMapping("/policy")
	public ResponseModel<PrivacyDTO.Policy> publish(@RequestBody PrivacyDTO.Publish request) {
		return privacyService.publish(request);
	}

	@GetMapping("/me")
	public ResponseModel<PrivacyDTO.Status> status() {
		return privacyService.status();
	}

	@PostMapping("/accept")
	public ResponseModel<Void> accept() {
		return privacyService.accept();
	}

	@PostMapping("/delete-request")
	public ResponseModel<Void> requestDeletion() {
		return privacyService.requestDeletion();
	}

	@GetMapping("/delete-requests")
	public ResponseModel<List<PrivacyDTO.DeletionRequest>> requests() {
		return privacyService.requests();
	}

	@PutMapping("/delete-requests")
	public ResponseModel<Void> handle(@RequestParam("id") String id, @RequestParam("action") String action) {
		return privacyService.handle(id, action);
	}
}
