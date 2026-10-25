package com.saasno.service;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.entity.NotificationEntity;
import com.saasno.filter.ColumnFilter;
import com.saasno.repository.NotificationRepository;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.dto.notification.NotificationDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;
import com.saasno.utility.mapper.NotificationMapper;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class NotificationService {

	// Age limit of the notifications shown; older ones are not returned and are cleaned daily by NotificationCleanupJob.
	public static final int MAX_AGE_DAYS = 30;

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private NotificationMapper notificationMapper;

	@Autowired
	private SessionUtility sessionUtility;

	@Transactional(readOnly = true)
	public ResponseEntity<DataResult<NotificationDTO>> read(HttpServletRequest request) {
		try {
			ColumnFilter columnFilterUser = new ColumnFilter();
			columnFilterUser.setColumn("userId");
			columnFilterUser.setOperator("=");
			columnFilterUser.setValue(sessionUtility.getUserId());

			DataFilter dataFilter = new DataFilter(request);
			dataFilter.getColumn().add(columnFilterUser);
			// Only the last month of notifications is ever returned, whatever the client filter says (a client start date can only narrow
			// it, because all filters are combined with AND).
			dataFilter.getColumn().add(new ColumnFilter("registered", LocalDateTime.now().minusDays(MAX_AGE_DAYS).toString(), ">="));
			// Without an order the row order is not guaranteed and the next page can repeat or skip rows: newest first by default.
			if (dataFilter.getOrder() == null) {
				dataFilter.setOrder("registered");
				dataFilter.setDescending(true);
			}

			DataResult<NotificationEntity> result = notificationRepository.dataTable(dataFilter);
			List<NotificationDTO> dto = notificationMapper.dto(result.getData());
			return new ResponseEntity<DataResult<NotificationDTO>>(
					new DataResult<>(dto, result.getTotal(), result.getGrandTotal()), HttpStatus.OK);
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
			return new ResponseEntity<DataResult<NotificationDTO>>(
					new DataResult<NotificationDTO>(BigInteger.ZERO, BigInteger.ZERO), HttpStatus.OK);
		}
	}

	@Transactional
	public ResponseModel<NotificationDTO> update(NotificationDTO notification) {
		NotificationEntity existingNotification = notificationRepository.find(notification.getId());
		// Own notifications only; someone else's are answered as if they did not exist.
		if (existingNotification == null || !existingNotification.getUserId().equals(sessionUtility.getUserId())) {
			throw new ApiException("404", "Notification does not exist");
		}

		existingNotification.setStatus(1);
		notificationRepository.update(existingNotification);
		return new ResponseModel<NotificationDTO>().success(notificationMapper.dto(existingNotification));
	}
}
