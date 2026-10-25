package com.saasno.service;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.saasno.entity.AnnouncementEntity;
import com.saasno.repository.AnnouncementRepository;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.dto.announcement.AnnouncementDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;
import com.saasno.utility.mapper.AnnouncementMapper;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;

// Notices for everyone who is signed in (planned downtime, a new feature). Admins manage them; everyone reads the ones showing now.
@Service
public class AnnouncementService {

	static final Set<String> LEVELS = Set.of("INFO", "WARNING", "CRITICAL");

	@Autowired
	private AnnouncementRepository announcementRepository;

	@Autowired
	private AnnouncementMapper announcementMapper;

	@Autowired
	private ChangeLogService changeLogService;

	@Autowired
	private SessionUtility sessionUtility;

	@Transactional
	public ResponseModel<AnnouncementDTO> create(AnnouncementDTO dto) {
		AnnouncementEntity entity = new AnnouncementEntity();
		apply(entity, dto);
		entity.setCreatedBy(sessionUtility.getUsername());
		announcementRepository.store(entity);
		changeLogService.created("ANNOUNCEMENT", entity.getId(), entity.getTitle());
		return new ResponseModel<AnnouncementDTO>().success(announcementMapper.dto(entity));
	}

	@Transactional
	public ResponseEntity<DataResult<AnnouncementDTO>> read(HttpServletRequest request) {
		try {
			DataResult<AnnouncementEntity> result = announcementRepository.dataTable(new DataFilter(request));
			return new ResponseEntity<>(new DataResult<>(announcementMapper.dto(result.getData()), result.getTotal(), result.getGrandTotal()),
					HttpStatus.OK);
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
			return new ResponseEntity<>(new DataResult<AnnouncementDTO>(BigInteger.ZERO, BigInteger.ZERO), HttpStatus.OK);
		}
	}

	@Transactional
	public ResponseModel<AnnouncementDTO> update(AnnouncementDTO dto) {
		AnnouncementEntity entity = announcementRepository.find(dto.getId());
		if (entity == null) {
			throw new ApiException("404", "Announcement does not exist");
		}
		ChangeLogService.Changes changes = ChangeLogService.changes();
		String title = entity.getTitle();
		String message = entity.getMessage();
		String level = entity.getLevel();
		LocalDateTime startsAt = entity.getStartsAt();
		LocalDateTime endsAt = entity.getEndsAt();
		boolean active = entity.isActive();
		apply(entity, dto);
		changes.add("title", title, entity.getTitle()).add("message", message, entity.getMessage()).add("level", level, entity.getLevel())
				.add("startsAt", startsAt, entity.getStartsAt()).add("endsAt", endsAt, entity.getEndsAt()).add("active", active, entity.isActive());
		announcementRepository.update(entity);
		changeLogService.updated("ANNOUNCEMENT", entity.getId(), entity.getTitle(), changes);
		return new ResponseModel<AnnouncementDTO>().success(announcementMapper.dto(entity));
	}

	@Transactional
	public ResponseModel<AnnouncementDTO> delete(String id) {
		AnnouncementEntity entity = announcementRepository.find(id);
		if (entity == null) {
			throw new ApiException("404", "Announcement does not exist");
		}
		changeLogService.deleted("ANNOUNCEMENT", entity.getId(), entity.getTitle());
		announcementRepository.delete(entity);
		return new ResponseModel<AnnouncementDTO>().success();
	}

	// The announcements showing right now (for the banner).
	@Transactional
	public ResponseModel<List<AnnouncementDTO>> showing() {
		return new ResponseModel<List<AnnouncementDTO>>().success(announcementMapper.dto(announcementRepository.showing(LocalDateTime.now())));
	}

	// Checks and copies the editable fields.
	static void check(AnnouncementDTO dto) {
		if (dto.getTitle() == null || dto.getTitle().isBlank()) {
			throw new ApiException("400", "Title must not be empty");
		}
		if (dto.getTitle().trim().length() > 120) {
			throw new ApiException("400", "Title must be at most 120 characters");
		}
		if (dto.getMessage() == null || dto.getMessage().isBlank()) {
			throw new ApiException("400", "Message must not be empty");
		}
		if (dto.getMessage().trim().length() > 1000) {
			throw new ApiException("400", "Message must be at most 1000 characters");
		}
		if (dto.getLevel() != null && !LEVELS.contains(dto.getLevel().toUpperCase())) {
			throw new ApiException("400", "Level must be INFO, WARNING or CRITICAL");
		}
		if (dto.getStartsAt() != null && dto.getEndsAt() != null && !dto.getEndsAt().isAfter(dto.getStartsAt())) {
			throw new ApiException("400", "The end time must be after the start time");
		}
	}

	private void apply(AnnouncementEntity entity, AnnouncementDTO dto) {
		check(dto);
		entity.setTitle(dto.getTitle().trim());
		entity.setMessage(dto.getMessage().trim());
		entity.setLevel(dto.getLevel() == null ? "INFO" : dto.getLevel().toUpperCase());
		entity.setStartsAt(dto.getStartsAt());
		entity.setEndsAt(dto.getEndsAt());
		entity.setActive(dto.getActive() == null || dto.getActive());
	}
}
