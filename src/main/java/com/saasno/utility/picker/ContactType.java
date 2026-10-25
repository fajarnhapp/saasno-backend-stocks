package com.saasno.utility.picker;

import com.saasno.utility.exception.ApiException;

public enum ContactType {
    EMAIL,
    PHONE;

    public static ContactType fromString(String value) {
        for (ContactType type : ContactType.values()) {
            if (type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new ApiException("400", "Contact type must be EMAIL or PHONE");
    }
}
