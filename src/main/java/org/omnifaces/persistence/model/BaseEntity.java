package org.omnifaces.persistence.model;

import java.io.Serializable;

import javax.persistence.EntityListeners;

import org.omnifaces.persistence.listener.PersistenceEventEntityListener;

@EntityListeners(PersistenceEventEntityListener.class)
public abstract class BaseEntity<T> implements Serializable {

	private static final long serialVersionUID = 1L;

	public abstract T getId();

	public abstract void setId(T id);

	@Override
    public int hashCode() {
        return (getId() != null)
            ? (getClass().getSimpleName().hashCode() + getId().hashCode())
            : super.hashCode();
    }

	@Override
    public boolean equals(Object other) {
        return (other != null && getId() != null
                && other.getClass().isAssignableFrom(getClass())
                && getClass().isAssignableFrom(other.getClass()))
            ? getId().equals(((BaseEntity<?>) other).getId())
            : (other == this);
    }

	@Override
	public String toString() {
		return String.format("%s[id=%d]", getClass().getSimpleName(), getId());
	}

}
