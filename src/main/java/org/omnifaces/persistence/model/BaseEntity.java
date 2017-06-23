package org.omnifaces.persistence.model;

import static javax.persistence.GenerationType.IDENTITY;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.EntityListeners;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;

import org.omnifaces.persistence.listener.BaseEntityListener;
import org.omnifaces.persistence.service.BaseEntityService;

/**
 * <p>
 * Let all your entities extend from this. Then you can make use of {@link BaseEntityService}. This mapped superclass
 * already automatically takes care of the <code>id</code> column.
 * <p>
 * There are two more mapped superclasses which may also be of interest.
 * <ul>
 * <li>{@link TimestampedEntity} - extends {@link BaseEntity} with <code>created</code> and <code>lastModified</code>
 * columns and automatically takes care of them.
 * <li>{@link VersionedEntity} - extends {@link TimestampedEntity} with a <code>@Version</code> column.
 * </ul>
 *
 * @param <I> The generic ID type, usually {@link Long}.
 * @author Bauke Scholtz
 */
@MappedSuperclass
@EntityListeners(BaseEntityListener.class)
public abstract class BaseEntity<I extends Comparable<I> & Serializable> implements Comparable<BaseEntity<I>>, Identifiable<I>, Serializable {

	private static final long serialVersionUID = 1L;

	@Id @GeneratedValue(strategy = IDENTITY)
	private I id;

	@Override
	public I getId() {
		return id;
	}

	@Override
	public void setId(I id) {
		this.id = id;
	}

	/**
	 * Hashes by default the classname and ID.
	 */
	@Override
    public int hashCode() {
        return (getId() != null)
        	? Objects.hash(getClass().getSimpleName(), getId())
        	: super.hashCode();
    }

	/**
	 * Compares by default by entity class (proxies taken into account) and ID.
	 */
	@Override
    public boolean equals(Object other) {
        return (other != null && getId() != null && other.getClass().isAssignableFrom(getClass()) && getClass().isAssignableFrom(other.getClass()))
            ? getId().equals(((BaseEntity<?>) other).getId())
            : (other == this);
    }

	/**
	 * Orders by default with "nulls last".
	 */
	@Override
	public int compareTo(BaseEntity<I> other) {
		return (other == null)
			? -1
			: (getId() == null)
				? (other.getId() == null ? 0 : 1)
				: getId().compareTo(other.getId());
	}

	/**
	 * The default format is <code>ClassName[id={id}]</code>.
	 */
	@Override
	public String toString() {
		return String.format("%s[id=%d]", getClass().getSimpleName(), getId());
	}

}
