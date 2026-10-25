package com.saasno.service;

import java.math.BigInteger;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.entity.ConfigurationEntity;
import com.saasno.entity.SessionEntity;
import com.saasno.repository.ConfigurationRepository;
import com.saasno.utility.NetworkUtility;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.dto.configuration.ConfigurationDTO;
import com.saasno.utility.constant.ConfigurationKey;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;
import com.saasno.utility.mapper.ConfigurationMapper;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class ConfigurationService {

    @Autowired
    private ConfigurationRepository configurationRepository;

    @Autowired
    private ConfigurationMapper configurationMapper;

    @Autowired
    private SessionUtility sessionUtility;

    @Autowired
    private ChangeLogService changeLogService;

    @Transactional
    public ResponseModel<ConfigurationDTO> create(ConfigurationDTO configuration) {
        configuration.setValue(normalize(configuration));
        configurationRepository.store(configurationMapper.entity(configuration));
        changeLogService.created("CONFIGURATION", configuration.getId().name(), configuration.getId().name());
        return new ResponseModel<ConfigurationDTO>().success(masked(configuration));
    }

    // Writes one setting from code (checked like the Configuration screen does), creating the row when it does not exist yet.
    @Transactional
    public void set(ConfigurationKey key, String value) {
        ConfigurationDTO dto = new ConfigurationDTO();
        dto.setId(key);
        dto.setValue(value);
        String normalized = normalize(dto);
        ConfigurationEntity existing = configurationRepository.find(key.name());
        if (existing == null) {
            configurationRepository.store(new ConfigurationEntity(key.name(), normalized));
        } else {
            String before = existing.getValue();
            existing.setValue(normalized);
            configurationRepository.store(existing);
            changeLogService.updated("CONFIGURATION", key.name(), key.name(), ChangeLogService.changes().add(key.name(), before, normalized));
        }
    }

    @Transactional(readOnly = true)
    public ResponseEntity<DataResult<ConfigurationDTO>> read(HttpServletRequest request) {
        try {
            DataResult<ConfigurationEntity> result = configurationRepository.dataTable(new DataFilter(request));
            List<ConfigurationDTO> dto = configurationMapper.dto(result.getData());
            dto.forEach(ConfigurationService::masked);
            return new ResponseEntity<DataResult<ConfigurationDTO>>(
                    new DataResult<>(dto, result.getTotal(), result.getGrandTotal()), HttpStatus.OK);
        } catch (Exception e) {
            Log.error(e.getMessage(), e);
            return new ResponseEntity<DataResult<ConfigurationDTO>>(
                    new DataResult<ConfigurationDTO>(BigInteger.ZERO, BigInteger.ZERO), HttpStatus.OK);
        }
    }

    @Transactional(readOnly = true)
    public ResponseModel<ConfigurationDTO> find(String id) {
        ConfigurationEntity existingConfiguration = configurationRepository.find(id);
        if (existingConfiguration == null) {
            throw new ApiException("404", "Configuration data does not exist");
        }

        return new ResponseModel<ConfigurationDTO>().success(masked(configurationMapper.dto(existingConfiguration)));
    }

    @Transactional
    public ResponseModel<ConfigurationDTO> update(ConfigurationDTO configuration) {
        ConfigurationEntity existingConfiguration = configurationRepository.find(configuration.getId().name());
        if (existingConfiguration == null) {
            throw new ApiException("404", "Configuration does not exist");
        }

        // The password is shown as ********; sending that back means "leave it as it is".
        if (configuration.getId() == ConfigurationKey.MAIL_PASSWORD && SECRET_MASK.equals(configuration.getValue())) {
            configuration.setValue(existingConfiguration.getValue());
        } else {
            configuration.setValue(normalize(configuration));
        }
        String before = existingConfiguration.getValue();
        existingConfiguration.setValue(configuration.getValue());
        configurationRepository.store(existingConfiguration);
        changeLogService.updated("CONFIGURATION", existingConfiguration.getId(), existingConfiguration.getId(),
                ChangeLogService.changes().add(existingConfiguration.getId(), before, configuration.getValue()));
        return new ResponseModel<ConfigurationDTO>().success(masked(configuration));
    }

    @Transactional
    public ResponseModel<Void> remove(String id) {
        ConfigurationEntity existingConfiguration = configurationRepository.find(id);
        if (existingConfiguration == null) {
            throw new ApiException("404", "Configuration does not exist");
        }

        changeLogService.deleted("CONFIGURATION", existingConfiguration.getId(), existingConfiguration.getId());
        configurationRepository.delete(existingConfiguration);
        return new ResponseModel<Void>().success(null);
    }

    // Whole-number settings are validated before they are saved.
    private String normalize(ConfigurationDTO configuration) {
        ConfigurationKey id = configuration.getId();
        if (!MAY_BE_EMPTY.contains(id) && (configuration.getValue() == null || configuration.getValue().isBlank())) {
            throw new ApiException("400", "Value must not be empty");
        }
        if (id == ConfigurationKey.NOTIFICATION_RETENTION_DAYS || id == ConfigurationKey.MAX_LOGIN_ATTEMPTS
                || id == ConfigurationKey.LOGIN_LOCK_MINUTES) {
            return wholeNumber(configuration.getValue(), id.name());
        }
        if (id == ConfigurationKey.PASSWORD_MIN_LENGTH || id == ConfigurationKey.PASSWORD_RESET_MINUTES
                || id == ConfigurationKey.PASSWORD_RESET_MAX_PER_HOUR || id == ConfigurationKey.DOWNLOAD_RETENTION_DAYS) {
            return wholeNumber(configuration.getValue(), id.name());
        }
        if (id == ConfigurationKey.PASSWORD_EXPIRY_DAYS || id == ConfigurationKey.AUDIT_LOG_RETENTION_DAYS) {
            return zeroOrWholeNumber(configuration.getValue(), id.name());
        }
        if (id == ConfigurationKey.PASSWORD_HISTORY_COUNT) {
            return ranged(configuration.getValue(), id.name(), 0, PasswordPolicyService.MAX_HISTORY, 0);
        }
        if (id == ConfigurationKey.APP_NAME) {
            return appName(configuration.getValue());
        }
        if (id == ConfigurationKey.BRAND_HUE) {
            return ranged(configuration.getValue(), id.name(), 0, 360, 0);
        }
        if (id == ConfigurationKey.BRAND_CHROMA) {
            return ranged(configuration.getValue(), id.name(), 0.3, 1.2, 2);
        }
        if (id == ConfigurationKey.LOGIN_IP_ALLOWLIST) {
            return ipAllowlist(configuration.getValue());
        }
        if (id == ConfigurationKey.ALLOWED_ORIGINS) {
            return allowedOrigins(configuration.getValue());
        }
        if (id == ConfigurationKey.MAIL_HOST) {
            return mailHost(configuration.getValue());
        }
        if (id == ConfigurationKey.MAIL_PORT) {
            return mailPort(configuration.getValue());
        }
        if (id == ConfigurationKey.MAIL_AUTH || id == ConfigurationKey.MAIL_STARTTLS) {
            return flag(configuration.getValue(), id.name());
        }
        if (id == ConfigurationKey.MAIL_FROM) {
            return mailFrom(configuration.getValue());
        }
        if (id == ConfigurationKey.MAIL_FROM_NAME) {
            return mailFromName(configuration.getValue());
        }
        if (id == ConfigurationKey.MAIL_USERNAME || id == ConfigurationKey.MAIL_PASSWORD) {
            return mailCredential(configuration.getValue(), id.name());
        }
        if (id == ConfigurationKey.MAINTENANCE_MODE) {
            return flag(configuration.getValue(), id.name());
        }
        if (id == ConfigurationKey.MAINTENANCE_MESSAGE) {
            return maintenanceMessage(configuration.getValue());
        }
        if (id == ConfigurationKey.MAINTENANCE_UNTIL) {
            return maintenanceUntil(configuration.getValue());
        }
        if (id == ConfigurationKey.MAINTENANCE_BYPASS_ROLES) {
            return MaintenanceModeService.bypassRolesValue(NetworkUtility.entries(configuration.getValue()));
        }
        if (id == ConfigurationKey.PASSWORD_REQUIRE_COMPLEXITY || id == ConfigurationKey.TWO_FACTOR_REQUIRED) {
            return flag(configuration.getValue(), id.name());
        }
        return configuration.getValue();
    }

    // Settings that are switched off or reset by emptying them.
    private static final java.util.Set<ConfigurationKey> MAY_BE_EMPTY = java.util.Set.of(ConfigurationKey.LOGIN_IP_ALLOWLIST, ConfigurationKey.ALLOWED_ORIGINS,
            ConfigurationKey.MAIL_HOST, ConfigurationKey.MAIL_USERNAME, ConfigurationKey.MAIL_PASSWORD, ConfigurationKey.MAIL_FROM_NAME,
            ConfigurationKey.MAINTENANCE_MESSAGE, ConfigurationKey.MAINTENANCE_UNTIL, ConfigurationKey.MAINTENANCE_BYPASS_ROLES);

    // What the API shows instead of the SMTP password.
    static final String SECRET_MASK = "********";

    // The real password never leaves the server: every answer shows the mask (or nothing when no password is set).
    static ConfigurationDTO masked(ConfigurationDTO configuration) {
        if (configuration != null && configuration.getId() == ConfigurationKey.MAIL_PASSWORD
                && configuration.getValue() != null && !configuration.getValue().isEmpty()) {
            configuration.setValue(SECRET_MASK);
        }
        return configuration;
    }

    // A host name or an IP address, nothing else (no scheme, no port, no path).
    private static final java.util.regex.Pattern HOST = java.util.regex.Pattern.compile("^[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$");

    private String mailHost(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!value.isEmpty() && !HOST.matcher(value).matches()) {
            throw new ApiException("400", "MAIL_HOST must be a host name or IP address only, such as smtp.example.com (no https://, port or path)");
        }
        return value;
    }

    private static final java.util.regex.Pattern EMAIL = java.util.regex.Pattern.compile("^[^@\\s<>\"]+@[^@\\s<>\"]+\\.[^@\\s<>\"]+$");

    private String mailFrom(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() > 254 || !EMAIL.matcher(value).matches()) {
            throw new ApiException("400", "MAIL_FROM must be an email address, such as no-reply@example.com");
        }
        return value;
    }

    private String mailFromName(String raw) {
        String value = raw == null ? "" : raw.replaceAll("\\p{Cntrl}", "").trim();
        if (value.length() > 60) {
            throw new ApiException("400", "MAIL_FROM_NAME must be at most 60 characters");
        }
        return value;
    }

    private String mailPort(String raw) {
        try {
            int port = Integer.parseInt(raw == null ? "" : raw.trim());
            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }
            return String.valueOf(port);
        } catch (NumberFormatException e) {
            throw new ApiException("400", "MAIL_PORT must be a port number between 1 and 65535");
        }
    }

    private String mailCredential(String raw, String name) {
        boolean password = ConfigurationKey.MAIL_PASSWORD.name().equals(name);
        // A password is kept exactly as typed (spaces can be part of it); a user name is trimmed.
        String value = raw == null ? "" : (password ? raw : raw.trim());
        if (password && SECRET_MASK.equals(value)) {
            throw new ApiException("400", "Enter the real SMTP password, not the ******** placeholder");
        }
        if (value.length() > 512) {
            throw new ApiException("400", name + " must be at most 512 characters");
        }
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new ApiException("400", name + " must not contain line breaks or control characters");
        }
        return value;
    }

    private String maintenanceMessage(String raw) {
        String value = raw == null ? "" : raw.replaceAll("[\\p{Cntrl}&&[^\\n]]", "").trim();
        if (value.length() > MaintenanceModeService.MAX_MESSAGE) {
            throw new ApiException("400", "MAINTENANCE_MESSAGE must be at most " + MaintenanceModeService.MAX_MESSAGE + " characters");
        }
        return value;
    }

    private String maintenanceUntil(String raw) {
        java.time.LocalDateTime until = MaintenanceModeService.parseUntil(raw);
        if (raw != null && !raw.isBlank() && until == null) {
            throw new ApiException("400", "MAINTENANCE_UNTIL must look like 2026-10-23T18:00");
        }
        return until == null ? "" : MaintenanceModeService.formatUntil(until);
    }

    private String appName(String raw) {
        String value = raw == null ? "" : raw.replaceAll("\\p{Cntrl}", "").trim();
        if (value.isEmpty() || value.length() > 60) {
            throw new ApiException("400", "APP_NAME must be 1 to 60 characters");
        }
        return value;
    }

    // A number between min and max (inclusive), kept with at most `decimals` decimal places.
    private String ranged(String raw, String name, double min, double max, int decimals) {
        try {
            java.math.BigDecimal value = new java.math.BigDecimal(raw == null ? "" : raw.trim());
            if (value.doubleValue() < min || value.doubleValue() > max) {
                throw new NumberFormatException();
            }
            return value.setScale(decimals, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            throw new ApiException("400", name + " must be a number from " + min + " to " + max);
        }
    }

    // Every entry must be an IP or CIDR range. The administrator saving the list must be inside it, or they would lock themselves out.
    private String ipAllowlist(String raw) {
        java.util.List<String> entries = NetworkUtility.entries(raw);
        for (String entry : entries) {
            if (!NetworkUtility.validEntry(entry)) {
                throw new ApiException("400", "LOGIN_IP_ALLOWLIST has an invalid entry: " + entry);
            }
        }
        SessionEntity session = sessionUtility.get();
        String current = session == null ? null : session.getIp();
        if (!entries.isEmpty() && current != null && !NetworkUtility.allowed(current, String.join(",", entries))) {
            throw new ApiException("400", "Your current IP (" + current + ") is not in the list. Add it first, or you would be locked out");
        }
        return String.join(", ", entries);
    }

    private String allowedOrigins(String raw) {
        java.util.Set<String> origins = new java.util.LinkedHashSet<>();
        for (String entry : com.saasno.utility.OriginUtility.entries(raw)) {
            String origin = com.saasno.utility.OriginUtility.normalize(entry);
            if (origin == null) {
                throw new ApiException("400", "ALLOWED_ORIGINS has an invalid entry: " + entry + " (use https://host[:port], no path)");
            }
            origins.add(origin);
        }
        return String.join(", ", origins);
    }

    // Integer >= 0 (0 turns the setting off).
    private String zeroOrWholeNumber(String raw, String name) {
        try {
            java.math.BigDecimal value = new java.math.BigDecimal(raw == null ? "" : raw.trim());
            if (value.signum() < 0 || value.stripTrailingZeros().scale() > 0) {
                throw new NumberFormatException();
            }
            return value.toBigInteger().toString();
        } catch (NumberFormatException e) {
            throw new ApiException("400", name + " must be a whole number, 0 or more");
        }
    }

    private String flag(String raw, String name) {
        String value = raw == null ? "" : raw.trim().toLowerCase();
        if (!value.equals("true") && !value.equals("false")) {
            throw new ApiException("400", name + " must be true or false");
        }
        return value;
    }

    // Integer >= 1 (NOTIFICATION_RETENTION_DAYS also <= 30, because notifications are only kept for a month).
    private String wholeNumber(String raw, String name) {
        try {
            java.math.BigDecimal value = new java.math.BigDecimal(raw == null ? "" : raw.trim());
            if (value.signum() <= 0 || value.stripTrailingZeros().scale() > 0
                    || (ConfigurationKey.NOTIFICATION_RETENTION_DAYS.name().equals(name)
                            && value.compareTo(new java.math.BigDecimal(30)) > 0)) {
                throw new NumberFormatException();
            }
            return value.toBigInteger().toString();
        } catch (NumberFormatException e) {
            if (ConfigurationKey.NOTIFICATION_RETENTION_DAYS.name().equals(name)) {
                throw new ApiException("400", name + " must be a whole number from 1 to 30 (notifications are only kept for a month)");
            }
            throw new ApiException("400", name + " must be a whole number, 1 or more");
        }
    }
}
