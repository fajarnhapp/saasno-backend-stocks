package com.saasno.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.AnnouncementService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.announcement.AnnouncementDTO;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping(Path.API_ANNOUNCEMENT)
public class AnnouncementController {

	@Autowired
	private AnnouncementService announcementService;

	@PostMapping
	public ResponseModel<AnnouncementDTO> create(@RequestBody AnnouncementDTO announcement) {
		return announcementService.create(announcement);
	}

	@GetMapping
	public ResponseEntity<DataResult<AnnouncementDTO>> read(HttpServletRequest request) {
		return announcementService.read(request);
	}

	@PutMapping
	public ResponseModel<AnnouncementDTO> update(@RequestBody AnnouncementDTO announcement) {
		return announcementService.update(announcement);
	}

	@DeleteMapping
	public ResponseModel<AnnouncementDTO> delete(@RequestParam("id") String id) {
		return announcementService.delete(id);
	}

	// The announcements showing now, for the banner every signed-in user sees.
	@GetMapping("/active")
	public ResponseModel<List<AnnouncementDTO>> showing() {
		return announcementService.showing();
	}
}
