package com.saasno.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.entity.LoginHistoryEntity;
import com.saasno.service.LoginHistoryService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping(Path.API_LOGIN_HISTORY)
public class LoginHistoryController {

	@Autowired
	private LoginHistoryService loginHistoryService;

	@GetMapping
	public ResponseEntity<DataResult<LoginHistoryEntity>> read(HttpServletRequest request) {
		return loginHistoryService.read(request);
	}

	@GetMapping("/me")
	public ResponseModel<List<LoginHistoryEntity>> mine() {
		return loginHistoryService.mine();
	}
}
