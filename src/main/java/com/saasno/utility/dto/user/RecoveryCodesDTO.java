package com.saasno.utility.dto.user;

import java.util.List;

// The plain recovery codes, shown to the user once; only their hashes are kept.
public record RecoveryCodesDTO(List<String> codes) {
}
