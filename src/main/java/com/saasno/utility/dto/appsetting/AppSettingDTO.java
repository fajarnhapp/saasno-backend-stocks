package com.saasno.utility.dto.appsetting;

import com.saasno.utility.dto.maintenance.MaintenanceStatusDTO;

public record AppSettingDTO(String appName, int brandHue, double brandChroma, boolean hasLogo, long logoVersion,
		MaintenanceStatusDTO maintenance) {
}
