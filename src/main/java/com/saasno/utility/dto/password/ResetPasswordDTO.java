package com.saasno.utility.dto.password;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ResetPasswordDTO(String token, String password) {
}
