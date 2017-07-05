package org.omnifaces.persistence.model.dto;

import static java.lang.Integer.MAX_VALUE;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static java.util.Collections.unmodifiableMap;
import static org.omnifaces.persistence.model.Identifiable.ID;
import static org.omnifaces.utils.Lang.isEmpty;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.omnifaces.persistence.service.BaseEntityService;

/**
 * This class basically defines a paged view of a database based on a given offset, limit, ordering, required criteria
 * and optional criteria.
 */
public final class Page { // This class should NOT be mutable!

	public final static Page ALL = new Page(MAX_VALUE);
	public final static Page ONE = new Page(1);

	private final int offset;
	private final int limit;
	private final Map<String, Boolean> ordering;
	private final Map<String, Object> requiredCriteria;
	private final Map<String, Object> optionalCriteria;

	private Page(int limit) {
		this(null, limit, null, null, null);
	}

	/**
	 * Creates a new Page.
	 * @param offset Zero-based offset of the page. May not be negative. Defaults to 0.
	 * @param limit Maximum amount of records to be matched. May not be less than 1. Defaults to {@link Integer#MAX_VALUE}.
	 * @param ordering Ordering of results. Map key represents property name and map value represents whether to sort ascending. Defaults to <code>{"id",false}</code>.
	 * @param requiredCriteria Required criteria. Map key represents property name and map value represents criteria. Each entity must match all of given criteria.
	 * @param optionalCriteria Optional criteria. Map key represents property name and map value represents criteria. Each entity must match at least one of given criteria.
	 */
	public Page(Integer offset, Integer limit, LinkedHashMap<String, Boolean> ordering, Map<String, Object> requiredCriteria, Map<String, Object> optionalCriteria) {
		this.offset = validateIntegerArgument("offset", offset, 0, 0);
		this.limit = validateIntegerArgument("limit", limit, 1, MAX_VALUE);
		this.ordering = !isEmpty(ordering) ? unmodifiableMap(ordering) : singletonMap(ID, false);
		this.requiredCriteria = requiredCriteria != null ? unmodifiableMap(requiredCriteria) : emptyMap();
		this.optionalCriteria = optionalCriteria != null ? unmodifiableMap(optionalCriteria) : emptyMap();
	}

	private static int validateIntegerArgument(String argumentName, Integer argumentValue, int minValue, int defaultValue) {
		if (argumentValue == null) {
			return defaultValue;
		}

		if (argumentValue < minValue) {
			throw new IllegalArgumentException("Argument " + argumentName + " may not be less than " + minValue);
		}

		return argumentValue;
	}

	/**
	 * Returns the offset. Defaults to 0.
	 * @return The offset.
	 */
	public int getOffset() {
		return offset;
	}

	/**
	 * Returns the limit. Defaults to {@link Integer#MAX_VALUE}.
	 * @return The limit.
	 */
	public int getLimit() {
		return limit;
	}

	/**
	 * Returns the ordering. Map key represents property name and map value represents whether to sort ascending. Defaults to <code>{"id",false}</code>.
	 * @return The ordering.
	 */
	public Map<String, Boolean> getOrdering() {
		return ordering;
	}

	/**
	 * Returns the required criteria. Map key represents property name and map value represents criteria. Each entity must match all of given criteria.
	 * @return The required criteria.
	 */
	public Map<String, Object> getRequiredCriteria() {
		return requiredCriteria;
	}

	/**
	 * Returns the optional criteria. Map key represents property name and map value represents criteria. Each entity must match at least one of given criteria.
	 * @return The optional criteria.
	 */
	public Map<String, Object> getOptionalCriteria() {
		return optionalCriteria;
	}

	@Override
	public boolean equals(Object object) {
		if (!(object instanceof Page)) {
			return false;
		}

		if (object == this) {
			return true;
		}

		Page other = (Page) object;

		return Objects.equals(offset, other.offset)
			&& Objects.equals(limit, other.limit)
			&& Objects.equals(ordering, other.ordering)
			&& Objects.equals(requiredCriteria, other.requiredCriteria)
			&& Objects.equals(optionalCriteria, other.optionalCriteria);
	}

	@Override
	public int hashCode() {
		return Objects.hash(Page.class, offset, limit, ordering, requiredCriteria, optionalCriteria);
	}

	/**
	 * Identity is important as this is used as value of <code>org.hibernate.cacheRegion</code> hint in
	 * {@link BaseEntityService#getPage(Page, boolean)}. Hence the criteria hashmaps are printed as treemaps.
	 */
	@Override
	public String toString() {
		return new StringBuilder("Page[")
			.append(offset).append(",")
			.append(limit).append(",")
			.append(ordering).append(",")
			.append(new TreeMap<>(requiredCriteria)).append(",")
			.append(new TreeMap<>(optionalCriteria)).append("]").toString();
	}

}