package com.saasno.utility.dto.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Received in the request body (not the query string): "password" and "oldPassword" are credentials
// and must stay out of access logs. The field names match LogMasker's sensitive keys, so the audit
// log masks them.
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChangePasswordDTO(String password, String oldPassword) {
}
