package com.saasno.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasno.configuration.RabbitMQConfiguration;
import com.saasno.entity.ApiEntity;
import com.saasno.entity.DownloadJobEntity;
import com.saasno.entity.LogEntity;
import com.saasno.entity.MenuEntity;
import com.saasno.entity.RoleEntity;
import com.saasno.entity.UserEntity;
import com.saasno.repository.ApiRepository;
import com.saasno.repository.DownloadJobRepository;
import com.saasno.repository.LogRepository;
import com.saasno.repository.MenuRepository;
import com.saasno.repository.RoleRepository;
import com.saasno.repository.UserRepository;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.dto.download.DownloadJobDTO;
import com.saasno.utility.dto.download.DownloadJobRequestDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;
import com.saasno.utility.mapper.DownloadJobMapper;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.picker.DownloadJobStatus;
import com.saasno.utility.verification.StringVerification;

@Service
public class DownloadJobService {

	private static final List<String> SUPPORTED_TYPES = List.of("USER_EXPORT", "ROLE_EXPORT", "MENU_EXPORT",
			"API_EXPORT", "LOG_EXPORT");

	private static final List<String> SUPPORTED_FORMATS = List.of("csv", "excel");

	// A download is only allowed for those who may open its data on the page: download type -> the API that reads that page.
	private static final Map<String, String> REQUIRED_API = Map.of(
			"USER_EXPORT", "GET_API_USER", "ROLE_EXPORT", "GET_API_ROLE", "MENU_EXPORT", "GET_API_MENU",
			"API_EXPORT", "GET_API_API", "LOG_EXPORT", "GET_API_LOG");

	private static final String[] USER_EXPORT_HEADERS = { "Username", "Name", "Email", "Roles", "Status", "OTP", "Registered",
			"Modified" };

	private static final String[] ROLE_EXPORT_HEADERS = { "ID", "Name", "Menu Count", "API Count", "Registered",
			"Modified" };

	private static final String[] MENU_EXPORT_HEADERS = { "ID", "Name", "Icon", "URL", "Parent", "Order",
			"Registered", "Modified" };

	private static final String[] API_EXPORT_HEADERS = { "ID", "Name", "Method", "Path", "Registered", "Modified" };

	private static final String[] LOG_EXPORT_HEADERS = { "Date", "User ID", "Username", "Method", "URI", "IP",
			"Status", "Execution Time (ms)", "Trace ID", "Error Message", "Request Param", "Request Body" };

	// Excel rejects cell text longer than 32,767 characters; long request bodies are cut to fit
	private static final int EXCEL_MAX_CELL_LENGTH = 32767;

	private static final int MAX_JOBS_PER_USER = 20;

	@Autowired
	private DownloadJobRepository downloadJobRepository;

	@Autowired
	private DownloadJobMapper downloadJobMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private MenuRepository menuRepository;

	@Autowired
	private ApiRepository apiRepository;

	@Autowired
	private LogRepository logRepository;

	@Autowired
	private SessionUtility sessionUtility;

	@Autowired
	private PermissionService permissionService;

	@Autowired
	private NotificationPublisher notificationPublisher;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Value("${app.upload.dir}")
	private String uploadDir;

	@Transactional
	public ResponseModel<DownloadJobDTO> create(DownloadJobRequestDTO request) {
		if (!SUPPORTED_TYPES.contains(request.getType())) {
			throw new ApiException("400", "Unrecognized download type");
		}
		if (!permissionService.has(REQUIRED_API.get(request.getType()))) {
			throw new ApiException("403", "You do not have access to this data");
		}
		if (!SUPPORTED_FORMATS.contains(request.getFormat() == null ? "" : request.getFormat().toLowerCase())) {
			throw new ApiException("400", "Unrecognized download format");
		}

		DownloadJobEntity job = new DownloadJobEntity();
		job.setType(request.getType());
		job.setFormat(request.getFormat().toLowerCase());
		job.setRequestedBy(sessionUtility.getUserId());
		job.setFilterJson(toFilterJson(request.getFilter()));
		downloadJobRepository.store(job);

		return new ResponseModel<DownloadJobDTO>().success(downloadJobMapper.dto(job));
	}

	// Sengaja TIDAK di dalam transaksi create() di atas: kalau di-publish sebelum transaksi
	// commit, consumer lokal (sama-sama di mesin ini) bisa saja sudah menerima & memproses
	// pesannya sebelum baris download_job benar-benar ter-commit ke Postgres — DownloadConsumer
	// akan find(jobId) dan dapat null, lalu diam-diam tidak melakukan apa-apa, sehingga status
	// job selamanya nyangkut di QUEUED. Controller memanggil ini SETELAH create() selesai
	// (dan proxy @Transactional-nya sudah commit), supaya urutannya selalu: commit dulu, baru publish.
	public void publish(String jobId) {
		rabbitTemplate.convertAndSend(
				RabbitMQConfiguration.DOWNLOAD_EXCHANGE_NAME,
				RabbitMQConfiguration.DOWNLOAD_ROUTING_KEY,
				jobId);
	}

	@Transactional(readOnly = true)
	public ResponseModel<List<DownloadJobDTO>> readMine() {
		List<DownloadJobEntity> jobs = downloadJobRepository.findByRequestedBy(sessionUtility.getUserId(),
				MAX_JOBS_PER_USER);
		return new ResponseModel<List<DownloadJobDTO>>().success(downloadJobMapper.dto(jobs));
	}

	@Transactional
	public ResponseModel<Void> delete(String id) {
		DownloadJobEntity job = findOwned(id);
		deleteFileIfExists(job);
		downloadJobRepository.delete(job);
		return new ResponseModel<Void>().success();
	}

	// Clears the whole list of the signed-in user (rows and files).
	@Transactional
	public ResponseModel<Integer> deleteAllMine() {
		List<DownloadJobEntity> jobs = downloadJobRepository.findByRequestedBy(sessionUtility.getUserId(), Integer.MAX_VALUE);
		for (DownloadJobEntity job : jobs) {
			deleteFileIfExists(job);
			downloadJobRepository.delete(job);
		}
		return new ResponseModel<Integer>().success(jobs.size());
	}

	@Transactional(readOnly = true)
	public ResponseEntity<Resource> file(String id) {
		DownloadJobEntity job = findOwned(id);
		if (job.getStatus() != DownloadJobStatus.DONE || StringVerification.nullOrEmpty(job.getFileName())) {
			throw new ApiException("400", "File is not ready yet");
		}

		Path filePath = Paths.get(uploadDir, "downloads", job.getFileName());
		if (!Files.exists(filePath)) {
			throw new ApiException("404", "File not found");
		}

		String extension = "excel".equals(job.getFormat()) ? "xlsx" : "csv";
		String contentType = "excel".equals(job.getFormat())
				? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
				: "text/csv";
		String downloadName = job.getType().toLowerCase() + "-" + job.getId().substring(0, 8) + "." + extension;

		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(contentType))
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadName + "\"")
				.body(new FileSystemResource(filePath));
	}

	// ==== Dipanggil oleh DownloadConsumer — masing-masing transaksi sengaja terpisah supaya
	// status PROCESSING langsung ter-commit dan terlihat oleh polling client selagi proses
	// export (yang bisa lama) masih berjalan. ====

	@Transactional
	public void markProcessing(String jobId) {
		DownloadJobEntity job = downloadJobRepository.find(jobId);
		if (job == null) {
			return;
		}
		job.setStatus(DownloadJobStatus.PROCESSING);
		downloadJobRepository.store(job);
	}

	@Transactional
	public void exportAndComplete(String jobId) throws IOException {
		DownloadJobEntity job = downloadJobRepository.find(jobId);
		if (job == null) {
			return;
		}

		String[] headers;
		List<String[]> rows;
		DataFilter dataFilter = buildFullFilter(job.getFilterJson());

		switch (job.getType()) {
			case "USER_EXPORT":
				headers = USER_EXPORT_HEADERS;
				dataFilter.setCustom("AND e.deletedAt IS NULL");
				rows = userRepository.dataTable(dataFilter).getData().stream().map(this::toUserRow).toList();
				break;
			case "ROLE_EXPORT":
				headers = ROLE_EXPORT_HEADERS;
				rows = roleRepository.dataTable(dataFilter).getData().stream().map(this::toRoleRow).toList();
				break;
			case "MENU_EXPORT":
				headers = MENU_EXPORT_HEADERS;
				rows = menuRepository.dataTable(dataFilter).getData().stream().map(this::toMenuRow).toList();
				break;
			case "API_EXPORT":
				headers = API_EXPORT_HEADERS;
				rows = apiRepository.dataTable(dataFilter).getData().stream().map(this::toApiRow).toList();
				break;
			case "LOG_EXPORT":
				headers = LOG_EXPORT_HEADERS;
				rows = logRepository.dataTable(dataFilter).getData().stream().map(this::toLogRow).toList();
				break;
			default:
				throw new ApiException("400", "Unrecognized download type");
		}

		Path directory = Paths.get(uploadDir, "downloads");
		Files.createDirectories(directory);

		String extension = "excel".equals(job.getFormat()) ? "xlsx" : "csv";
		String fileName = job.getId() + "." + extension;
		Path filePath = directory.resolve(fileName);

		if ("excel".equals(job.getFormat())) {
			writeExcel(headers, rows, filePath);
		} else {
			writeCsv(headers, rows, filePath);
		}

		job.setFileName(fileName);
		job.setStatus(DownloadJobStatus.DONE);
		downloadJobRepository.store(job);
		notificationPublisher.notify(List.of(job.getRequestedBy()), "DOWNLOAD_DONE", "Download ready",
				"Your " + exportLabel(job.getType()) + " download (" + ("excel".equals(job.getFormat()) ? "Excel" : "CSV")
						+ ") is ready.",
				null);
	}

	// Name of the exported data for the notification text, e.g. USER_EXPORT -> "user".
	private String exportLabel(String type) {
		if (type == null) {
			return "data";
		}
		return switch (type) {
			case "USER_EXPORT" -> "user";
			case "ROLE_EXPORT" -> "role";
			case "MENU_EXPORT" -> "menu";
			case "API_EXPORT" -> "API";
			case "LOG_EXPORT" -> "audit log";
			default -> "data";
		};
	}

	private DataFilter buildFullFilter(String filterJson) {
		Map<String, String> filterParams = parseFilterJson(filterJson);
		filterParams.remove(DataFilter.LIMIT);
		filterParams.remove(DataFilter.OFFSET);
		DataFilter dataFilter = new DataFilter(filterParams);
		dataFilter.setLimit(String.valueOf(Integer.MAX_VALUE));
		dataFilter.setOffset("0");
		return dataFilter;
	}

	@Transactional
	public void markFailed(String jobId, String message) {
		DownloadJobEntity job = downloadJobRepository.find(jobId);
		if (job == null) {
			return;
		}
		job.setStatus(DownloadJobStatus.FAILED);
		job.setErrorMessage(StringVerification.nullOrEmpty(message) ? "Failed to process download" : message);
		notificationPublisher.notify(List.of(job.getRequestedBy()), "DOWNLOAD_FAILED", "Download failed",
				"Your " + exportLabel(job.getType()) + " download could not be processed. Please try again.", null);
		downloadJobRepository.store(job);
	}

	private String[] toUserRow(UserEntity user) {
		String roles = user.getRoles() == null ? "" : user.getRoles().stream()
				.map(RoleEntity::getName)
				.collect(Collectors.joining(", "));
		return new String[] {
				user.getUsername(),
				user.getPerson() == null ? "" : user.getPerson().getFullName(),
				user.getPerson() == null ? "" : orEmpty(user.getPerson().primaryEmail()),
				roles,
				user.getStatus() != null ? user.getStatus().name() : "",
				Boolean.TRUE.equals(user.getTwoFactorAuth()) ? "Active" : "Inactive",
				user.getRegistered() != null ? user.getRegistered().toString() : "",
				user.getModificated() != null ? user.getModificated().toString() : "",
		};
	}

	private String[] toRoleRow(RoleEntity role) {
		return new String[] {
				role.getId(),
				role.getName(),
				String.valueOf(role.getMenus() == null ? 0 : role.getMenus().size()),
				String.valueOf(role.getApis() == null ? 0 : role.getApis().size()),
				role.getRegistered() != null ? role.getRegistered().toString() : "",
				role.getModificated() != null ? role.getModificated().toString() : "",
		};
	}

	private String[] toMenuRow(MenuEntity menu) {
		return new String[] {
				menu.getId(),
				menu.getName(),
				menu.getIcon() != null ? menu.getIcon() : "",
				menu.getUrl(),
				menu.getParent() != null ? menu.getParent().getId() : "",
				menu.getOrderIndex() != null ? menu.getOrderIndex().toString() : "",
				menu.getRegistered() != null ? menu.getRegistered().toString() : "",
				menu.getModificated() != null ? menu.getModificated().toString() : "",
		};
	}

	private String[] toApiRow(ApiEntity api) {
		return new String[] {
				api.getId(),
				api.getName(),
				api.getMethod(),
				api.getPath(),
				api.getRegistered() != null ? api.getRegistered().toString() : "",
				api.getModificated() != null ? api.getModificated().toString() : "",
		};
	}

	private String[] toLogRow(LogEntity log) {
		return new String[] {
				log.getRegistered() != null ? log.getRegistered().toString() : "",
				orEmpty(log.getUserId()),
				orEmpty(log.getUsername()),
				orEmpty(log.getMethod()),
				orEmpty(log.getUri()),
				orEmpty(log.getClientIp()),
				String.valueOf(log.getStatusCode()),
				String.valueOf(log.getExecutionTimeMs()),
				orEmpty(log.getTraceId()),
				orEmpty(log.getErrorMessage()),
				orEmpty(log.getRequestParam()),
				orEmpty(log.getRequestBody()),
		};
	}

	private String orEmpty(String value) {
		return value != null ? value : "";
	}

	private void writeCsv(String[] headers, List<String[]> rows, Path filePath) throws IOException {
		try (var writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
			writer.write(String.join(",", headers));
			writer.newLine();
			for (String[] row : rows) {
				writer.write(Arrays.stream(row).map(this::csvEscape).collect(Collectors.joining(",")));
				writer.newLine();
			}
		}
	}

	private String csvEscape(String value) {
		if (value == null) {
			return "";
		}
		value = neutralizeFormula(value);
		if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}
		return value;
	}

	// A CSV cell starting with = + - @ is read by Excel as a formula; names or notes from untrusted data could abuse that. Plain numbers
	// (including negative ones) are left alone, anything else gets a leading apostrophe.
	private String neutralizeFormula(String value) {
		if (value.isEmpty()) {
			return value;
		}
		char first = value.charAt(0);
		boolean risky = first == '=' || first == '@' || first == '\t' || first == '\r'
				|| ((first == '+' || first == '-') && !value.matches("[+-]?\\d+(\\.\\d+)?"));
		return risky ? "'" + value : value;
	}

	private void writeExcel(String[] headers, List<String[]> rows, Path filePath) throws IOException {
		try (XSSFWorkbook workbook = new XSSFWorkbook(); OutputStream os = Files.newOutputStream(filePath)) {
			Sheet sheet = workbook.createSheet("Data");

			CellStyle headerStyle = workbook.createCellStyle();
			Font headerFont = workbook.createFont();
			headerFont.setBold(true);
			headerStyle.setFont(headerFont);

			Row headerRow = sheet.createRow(0);
			for (int i = 0; i < headers.length; i++) {
				Cell cell = headerRow.createCell(i);
				cell.setCellValue(headers[i]);
				cell.setCellStyle(headerStyle);
			}

			int rowIndex = 1;
			for (String[] values : rows) {
				Row row = sheet.createRow(rowIndex++);
				for (int i = 0; i < values.length; i++) {
					String value = values[i];
					if (value != null && value.length() > EXCEL_MAX_CELL_LENGTH) {
						value = value.substring(0, EXCEL_MAX_CELL_LENGTH);
					}
					row.createCell(i).setCellValue(value);
				}
			}

			for (int i = 0; i < headers.length; i++) {
				sheet.autoSizeColumn(i);
			}

			workbook.write(os);
		}
	}

	private DownloadJobEntity findOwned(String id) {
		DownloadJobEntity job = downloadJobRepository.find(id);
		if (job == null || !job.getRequestedBy().equals(sessionUtility.getUserId())) {
			throw new ApiException("404", "Download not found");
		}
		return job;
	}

	private void deleteFileIfExists(DownloadJobEntity job) {
		if (StringVerification.nullOrEmpty(job.getFileName())) {
			return;
		}
		try {
			Files.deleteIfExists(Paths.get(uploadDir, "downloads", job.getFileName()));
		} catch (IOException e) {
			Log.error("Failed to delete download file " + job.getId(), e);
		}
	}

	private String toFilterJson(Map<String, String> filter) {
		try {
			return objectMapper.writeValueAsString(filter != null ? filter : Map.of());
		} catch (JsonProcessingException e) {
			return "{}";
		}
	}

	private Map<String, String> parseFilterJson(String filterJson) {
		if (StringVerification.nullOrEmpty(filterJson)) {
			return new HashMap<>();
		}
		try {
			return objectMapper.readValue(filterJson, new TypeReference<Map<String, String>>() {
			});
		} catch (Exception e) {
			return new HashMap<>();
		}
	}

}
