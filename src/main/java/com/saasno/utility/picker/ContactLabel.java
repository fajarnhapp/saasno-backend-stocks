package com.saasno.utility.picker;

import com.saasno.utility.exception.ApiException;

public enum ContactLabel {
    PERSONAL,
    WORK,
    OTHER;

    public static ContactLabel fromString(String value) {
        if (value == null || value.isBlank()) {
            return PERSONAL;
        }
        for (ContactLabel label : ContactLabel.values()) {
            if (label.name().equalsIgnoreCase(value)) {
                return label;
            }
        }
        throw new ApiException("400", "Contact label must be PERSONAL, WORK or OTHER");
    }
}
