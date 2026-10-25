package com.saasno.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.ApiTokenService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.token.ApiTokenDTO;
import com.saasno.utility.model.ResponseModel;

@RestController
@RequestMapping(Path.API_TOKEN)
public class ApiTokenController {

	@Autowired
	private ApiTokenService apiTokenService;

	@GetMapping
	public ResponseModel<List<ApiTokenDTO>> mine() {
		return apiTokenService.mine();
	}

	@PostMapping
	public ResponseModel<ApiTokenDTO> create(@RequestBody ApiTokenDTO request) {
		return apiTokenService.create(request);
	}

	@DeleteMapping
	public ResponseModel<Void> revoke(@RequestParam("id") String id) {
		return apiTokenService.revoke(id);
	}
}
