package com.saasno.utility.dto.appsetting;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Strings so the same checks as the Configuration screen apply (see ConfigurationService.set).
@JsonIgnoreProperties(ignoreUnknown = true)
public record AppSettingUpdateDTO(String appName, String brandHue, String brandChroma) {
}
