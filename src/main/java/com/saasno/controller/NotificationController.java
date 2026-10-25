package com.saasno.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.NotificationService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.notification.NotificationDTO;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping(Path.API_NOTIFICATION)
public class NotificationController {

	@Autowired
	private NotificationService notificationService;

	@GetMapping("/me")
	public ResponseEntity<DataResult<NotificationDTO>> read(HttpServletRequest request) {
		return notificationService.read(request);
	}

	@PutMapping("/me")
	public ResponseModel<NotificationDTO> update(@RequestBody NotificationDTO notification) {
		return notificationService.update(notification);
	}

}
