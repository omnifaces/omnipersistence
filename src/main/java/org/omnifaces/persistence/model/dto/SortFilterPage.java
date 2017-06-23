package org.omnifaces.persistence.model.dto;

import static java.lang.Integer.MAX_VALUE;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;

import java.util.List;
import java.util.Map;

/**
 * @deprecated Use {@link Page} instead.
 */
@Deprecated
public final class SortFilterPage {

	public final static SortFilterPage ONE = new SortFilterPage(0, 1, null, null, emptyList(), emptyMap(), true);
	public final static SortFilterPage ALL = new SortFilterPage(0, MAX_VALUE, null, null, emptyList(), emptyMap(), true);

	private final int offset;
	private final int limit;
	private final String sortField;
	private final String sortOrder;
	private final List<String> filterableFields;
	private final Map<String, Object> filterValues;
	private final boolean filterWithAND;

	public SortFilterPage(int offset, int limit, String sortField, String sortOrder, List<String> filterableFields, Map<String, Object> filterValues, boolean filterWithAND) {
		this.offset = offset;
		this.limit = limit;
		this.sortField = sortField;
		this.sortOrder = sortOrder;
		this.filterableFields = unmodifiableList(filterableFields);
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

	public List<String> getFilterableFields() {
		return filterableFields;
	}

	public Map<String, Object> getFilterValues() {
		return filterValues;
	}

	public boolean isFilterWithAND() {
		return filterWithAND;
	}

	public static SortFilterPageBuilder with() {
		return new SortFilterPageBuilder();
	}

	public static class SortFilterPageBuilder {

		private int offset;
		private int limit;
		private String sortField;
		private String sortOrder;
		private List<String> filterableFields;
		private Map<String, Object> filterValues;
		private boolean filterWithAND;

		public SortFilterPageBuilder offset(int offset) {
			this.offset = offset;
			return this;
		}

		public SortFilterPageBuilder limit(int limit) {
			this.limit = limit;
			return this;
		}

		public SortFilterPageBuilder sortField(String sortField) {
			this.sortField = sortField;
			return this;
		}

		public SortFilterPageBuilder sortOrder(String sortOrder) {
			this.sortOrder = sortOrder;
			return this;
		}

		public SortFilterPageBuilder filterableFields(List<String> filterableFields) {
			this.filterableFields = filterableFields;
			return this;
		}

		public SortFilterPageBuilder filterValues(Map<String, Object> filterValues) {
			this.filterValues = filterValues;
			return this;
		}

		public SortFilterPageBuilder filterWithAND(boolean filterWithAND) {
			this.filterWithAND = filterWithAND;
			return this;
		}

		public SortFilterPage build() {
			return new SortFilterPage(
						offset, limit, sortField, sortOrder,
						filterableFields == null? emptyList() : filterableFields,
						filterValues == null? emptyMap() : filterValues,
						filterWithAND);
		}

	}

}