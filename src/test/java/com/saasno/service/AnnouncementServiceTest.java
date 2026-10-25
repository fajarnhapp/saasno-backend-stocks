package com.saasno.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.saasno.utility.dto.announcement.AnnouncementDTO;
import com.saasno.utility.exception.ApiException;

class AnnouncementServiceTest {

	private AnnouncementDTO valid() {
		AnnouncementDTO dto = new AnnouncementDTO();
		dto.setTitle("Maintenance");
		dto.setMessage("The system is down on Saturday night.");
		dto.setLevel("WARNING");
		return dto;
	}

	@Test
	void aMinimalAnnouncementIsAccepted() {
		assertDoesNotThrow(() -> AnnouncementService.check(valid()));
	}

	@Test
	void titleAndMessageAreRequired() {
		AnnouncementDTO noTitle = valid();
		noTitle.setTitle(" ");
		assertThrows(ApiException.class, () -> AnnouncementService.check(noTitle));
		AnnouncementDTO noMessage = valid();
		noMessage.setMessage(null);
		assertThrows(ApiException.class, () -> AnnouncementService.check(noMessage));
	}

	@Test
	void textIsLimited() {
		AnnouncementDTO longTitle = valid();
		longTitle.setTitle("x".repeat(121));
		assertThrows(ApiException.class, () -> AnnouncementService.check(longTitle));
		AnnouncementDTO longMessage = valid();
		longMessage.setMessage("x".repeat(1001));
		assertThrows(ApiException.class, () -> AnnouncementService.check(longMessage));
	}

	@Test
	void unknownLevelIsRejected() {
		AnnouncementDTO dto = valid();
		dto.setLevel("PANIC");
		assertThrows(ApiException.class, () -> AnnouncementService.check(dto));
	}

	@Test
	void theEndMustComeAfterTheStart() {
		AnnouncementDTO dto = valid();
		LocalDateTime now = LocalDateTime.now();
		dto.setStartsAt(now);
		dto.setEndsAt(now.minusHours(1));
		assertThrows(ApiException.class, () -> AnnouncementService.check(dto));
		dto.setEndsAt(now.plusHours(1));
		assertDoesNotThrow(() -> AnnouncementService.check(dto));
	}
}
