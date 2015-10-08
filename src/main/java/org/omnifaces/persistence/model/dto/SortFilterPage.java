package org.omnifaces.persistence.model.dto;

import java.util.Map;

public class SortFilterPage {

	private int offset; 
	private int limit; 
	private String sortField; 
	private String sortOrder;
	private String filterOperator;
	private Map<String, Object> filters;
	
	public SortFilterPage(int offset, int limit, String sortField, String sortOrder, Map<String, Object> filters, String operator) {
		this.offset = offset;
		this.limit = limit;
		this.sortField = sortField;
		this.sortOrder = sortOrder;
		this.filters = filters;
		this.filterOperator = operator;
	}
	
	public int getOffset() {
		return offset;
	}

	public void setOffset(int offset) {
		this.offset = offset;
	}

	public int getLimit() {
		return limit;
	}

	public void setLimit(int limit) {
		this.limit = limit;
	}

	public String getSortField() {
		return sortField;
	}

	public void setSortField(String sortField) {
		this.sortField = sortField;
	}

	public String getSortOrder() {
		return sortOrder;
	}

	public void setSortOrder(String sortOrder) {
		this.sortOrder = sortOrder;
	}

	public Map<String, Object> getFilters() {
		return filters;
	}

	public void setFilters(Map<String, Object> filters) {
		this.filters = filters;
	}
	
	public String getFilterOperator() {
		return filterOperator;
	}

	public void setFilterOperator(String filterOperator) {
		this.filterOperator = filterOperator;
	}
	
	public SortFilterPage sortField(String sortField) {
		this.sortField = sortField;
		return this;
	}
	
	
	
}
