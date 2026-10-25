package com.saasno.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.entity.ChangeLogEntity;
import com.saasno.service.ChangeLogService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping(Path.API_CHANGE_LOG)
public class ChangeLogController {

	@Autowired
	private ChangeLogService changeLogService;

	@GetMapping
	public ResponseEntity<DataResult<ChangeLogEntity>> read(HttpServletRequest request) {
		return changeLogService.read(request);
	}
}
