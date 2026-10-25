package com.saasno.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.saasno.service.AvatarService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.user.AvatarDTO;
import com.saasno.utility.model.ResponseModel;

@RestController
@RequestMapping(Path.API_USER_AVATAR)
public class AvatarController {

	@Autowired
	private AvatarService avatarService;

	@GetMapping
	public ResponseEntity<byte[]> read(@RequestParam(value = "id", required = false) String id) {
		return avatarService.read(id);
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseModel<AvatarDTO> upload(@RequestParam("file") MultipartFile file) {
		return avatarService.upload(file);
	}

	@DeleteMapping
	public ResponseModel<Void> delete() {
		return avatarService.delete();
	}

}
