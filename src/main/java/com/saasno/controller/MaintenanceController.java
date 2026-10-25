package com.saasno.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.MaintenanceModeService;
import com.saasno.service.MaintenanceService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.maintenance.CleanupResultDTO;
import com.saasno.utility.dto.maintenance.MaintenanceModeDTO;
import com.saasno.utility.dto.maintenance.MaintenanceModeUpdateDTO;
import com.saasno.utility.dto.maintenance.MaintenanceSummaryDTO;
import com.saasno.utility.model.ResponseModel;

// Admin housekeeping. Needs its own role_api entries, so only roles given them (SUPER) can use it.
@RestController
@RequestMapping(Path.API_MAINTENANCE)
public class MaintenanceController {

	@Autowired
	private MaintenanceService maintenanceService;

	@Autowired
	private MaintenanceModeService maintenanceModeService;

	@GetMapping
	public ResponseModel<MaintenanceSummaryDTO> summary() {
		return new ResponseModel<MaintenanceSummaryDTO>().success(maintenanceService.summary());
	}

	// Maintenance mode (who may still work, the message, when it ends). Only roles linked to these in role_api (SUPER) can read or change it.
	@GetMapping("/mode")
	public ResponseModel<MaintenanceModeDTO> mode() {
		return new ResponseModel<MaintenanceModeDTO>().success(maintenanceModeService.read(true));
	}

	@PutMapping("/mode")
	public ResponseModel<MaintenanceModeDTO> updateMode(@RequestBody MaintenanceModeUpdateDTO request) {
		return new ResponseModel<MaintenanceModeDTO>().success(maintenanceModeService.update(request));
	}

	// Runs the cleanup now, using the retention settings: all of them, or one kind with ?type=downloads|audit-logs|notifications.
	@PostMapping("/cleanup")
	public ResponseModel<CleanupResultDTO> cleanup(@RequestParam(value = "type", required = false) String type) {
		return new ResponseModel<CleanupResultDTO>().success(maintenanceService.run(type));
	}

	// Deletes every download of every user, whatever its age.
	@DeleteMapping("/download")
	public ResponseModel<Long> purgeDownloads() {
		return new ResponseModel<Long>().success(maintenanceService.purgeAllDownloads());
	}

}
