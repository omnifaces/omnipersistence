package org.omnifaces.persistence.model.dto;

import static java.util.Collections.unmodifiableMap;

import java.util.Map;

public final class SortFilterPage {

	private final int offset;
	private final int limit;
	private final String sortField;
	private final String sortOrder;
	private final Map<String, Object> filterValues;
	private final boolean filterWithAND;

	public SortFilterPage(int offset, int limit, String sortField, String sortOrder, Map<String, Object> filterValues, boolean filterWithAND) {
		this.offset = offset;
		this.limit = limit;
		this.sortField = sortField;
		this.sortOrder = sortOrder;
		this.filterValues = unmodifiableMap(filterValues);
		this.filterWithAND = filterWithAND;
	}

	public int getOffset() {
		return offset;
	}

	public int getLimit() {
		return limit;
	}

	public String getSortField() {
		return sortField;
	}

	public String getSortOrder() {
		return sortOrder;
	}

	public Map<String, Object> getFilterValues() {
		return filterValues;
	}

	public boolean isFilterWithAND() {
		return filterWithAND;
	}

}