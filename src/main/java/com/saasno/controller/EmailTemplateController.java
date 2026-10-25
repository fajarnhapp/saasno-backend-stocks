package com.saasno.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.EmailTemplateService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.emailtemplate.EmailTemplateDTO;
import com.saasno.utility.model.ResponseModel;

@RestController
@RequestMapping(Path.API_EMAIL_TEMPLATE)
public class EmailTemplateController {

	@Autowired
	private EmailTemplateService emailTemplateService;

	@GetMapping
	public ResponseModel<List<EmailTemplateDTO>> list() {
		return emailTemplateService.list();
	}

	@PutMapping
	public ResponseModel<EmailTemplateDTO> update(@RequestBody EmailTemplateDTO template) {
		return emailTemplateService.update(template);
	}

	@DeleteMapping
	public ResponseModel<EmailTemplateDTO> reset(@RequestParam("id") String id) {
		return emailTemplateService.reset(id);
	}
}
