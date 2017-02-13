package org.omnifaces.persistence.model.dto;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.omnifaces.utils.collection.PartialResultList;

/**
 * List that implements the sort, filter and paging operations of a given
 * input list at construction time.
 *
 * @param <E>
 */
public class SortFilterPageList<E> extends PartialResultList<E> {
	
	public SortFilterPageList(Set<E> wrappedSet, SortFilterPage page) {
		super((List<E>) new ArrayList<>(wrappedSet), page.getOffset(), wrappedSet.size());
	}

	public SortFilterPageList(List<E> wrappedList, SortFilterPage page) {
		super((List<E>) wrappedList, page.getOffset(), wrappedList.size());
	}
	
}
