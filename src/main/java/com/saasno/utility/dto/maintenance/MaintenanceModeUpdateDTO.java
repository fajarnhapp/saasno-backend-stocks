package com.saasno.utility.dto.maintenance;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MaintenanceModeUpdateDTO(boolean enabled, String message, String until, List<String> bypassRoles) {
}
