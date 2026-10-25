package com.saasno.utility.dto.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Received in the request body (not the query string): "password" is a credential and must stay out
// of access logs. The field name matches LogMasker's sensitive keys, so the audit log masks it.
@JsonIgnoreProperties(ignoreUnknown = true)
public record VerifyPasswordDTO(String password) {
}
