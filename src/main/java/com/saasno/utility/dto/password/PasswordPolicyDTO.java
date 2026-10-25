package com.saasno.utility.dto.password;

// Rules the client applies to the plain password before it is hashed (the backend only ever receives the hash).
public record PasswordPolicyDTO(int minLength, boolean requireComplexity) {
}
