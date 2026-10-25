package com.saasno.utility.dto.download;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;

public class DownloadJobRequestDTO {

    @NotBlank(message = "Type must not be empty")
    private String type;

    @NotBlank(message = "Format must not be empty")
    private String format;

    private Map<String, String> filter;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public Map<String, String> getFilter() {
        return filter;
    }

    public void setFilter(Map<String, String> filter) {
        this.filter = filter;
    }
}
