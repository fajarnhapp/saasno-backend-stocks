package com.saasno.utility.model.data;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import com.saasno.filter.ColumnFilter;

import jakarta.servlet.http.HttpServletRequest;

public class DataFilter {

	public static final String CUSTOM = "custom";
	public static final String LIMIT = "limit";
	public static final String OFFSET = "offset";
	public static final String GROUP = "group";
	public static final String ORDER = "order";
	public static final String SORTING = "sorting";
	public static final String STARTDATE = "startDate";
	public static final String ENDDATE = "endDate";

	private List<ColumnFilter> column;
	private String custom;
	private String order;
	private String group;
	private String limit;
	private String offset;
	private boolean descending;

	public DataFilter() {
		this.column = new ArrayList<ColumnFilter>();
	}

	public DataFilter(HttpServletRequest request) {
		this.column = new ArrayList<ColumnFilter>();
		Map<String, String> params = new java.util.LinkedHashMap<>();
		Enumeration<String> parameters = request.getParameterNames();
		while (parameters.hasMoreElements()) {
			String parameter = parameters.nextElement();
			params.put(parameter, request.getParameter(parameter));
		}
		apply(params);
	}

	// Sama seperti DataFilter(HttpServletRequest), tapi dari Map biasa — dipakai saat filter
	// perlu disimpan/dikirim ulang di luar siklus hidup satu request (mis. job download async
	// yang query aslinya sudah tidak ada saat diproses worker RabbitMQ).
	public DataFilter(Map<String, String> params) {
		this.column = new ArrayList<ColumnFilter>();
		apply(params);
	}

	private void apply(Map<String, String> params) {
		String orderValue = null;
		String sortingValue = null;
		for (Map.Entry<String, String> entry : params.entrySet()) {
			String parameter = entry.getKey();
			String value = entry.getValue();
			if (parameter.equals(LIMIT)) {
				setLimit(value);
			} else if (parameter.equals(OFFSET)) {
				setOffset(value);
			} else if (parameter.equals(GROUP)) {
				setGroup(value);
			} else if (parameter.equals(STARTDATE)) {
				this.column.add(new ColumnFilter("registered", value, ">="));
			} else if (parameter.equals(ENDDATE)) {
				this.column.add(new ColumnFilter("registered", value, "<="));
			} else if (parameter.equals(CUSTOM)) {
				// Deliberately ignored: custom is inserted raw into JPQL, so it must never come from a request.
				// setCustom() is for internal code only.
			} else if (parameter.equals(ORDER)) {
				orderValue = value;
			} else if (parameter.equals(SORTING)) {
				sortingValue = value;
			} else {
				if (!parameter.equals(SORTING)) {
					if (parameter.equals("referenceType")) {
						this.column.add(new ColumnFilter(parameter, value, "ILIKE"));
					} else {
						this.column.add(new ColumnFilter(parameter, "%" + value + "%", "ILIKE"));
					}
				}
			}
		}

		if (orderValue != null) {
			setOrder(orderValue);
			if (sortingValue != null && sortingValue.equalsIgnoreCase("ASC")) {
				setDescending(false);
			} else {
				setDescending(true);
			}
		}
	}

	public List<ColumnFilter> getColumn() {
		return column;
	}

	public void setColumn(List<ColumnFilter> column) {
		this.column = column;
	}

	public String getCustom() {
		return custom;
	}

	public void setCustom(String custom) {
		this.custom = custom;
	}

	public String getOrder() {
		return order;
	}

	public void setOrder(String order) {
		this.order = order;
	}

	public String getGroup() {
		return group;
	}

	public void setGroup(String group) {
		this.group = group;
	}

	public String getLimit() {
		return limit;
	}

	public void setLimit(String limit) {
		this.limit = limit;
	}

	public String getOffset() {
		return offset;
	}

	public void setOffset(String offset) {
		this.offset = offset;
	}

	public boolean isDescending() {
		return descending;
	}

	public void setDescending(boolean descending) {
		this.descending = descending;
	}
}
