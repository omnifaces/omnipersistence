package org.omnifaces.persistence.audit;

import static java.beans.Introspector.getBeanInfo;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toConcurrentMap;
import static java.util.stream.Collectors.toSet;
import static org.omnifaces.utils.reflect.Reflections.invokeMethod;
import static org.omnifaces.utils.stream.Streams.stream;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.enterprise.inject.spi.CDI;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.PostLoad;
import javax.persistence.PreUpdate;
import javax.persistence.metamodel.Attribute;

import org.omnifaces.persistence.model.BaseEntity;

/**
 * <p>
 * See {@link Audit} for usage instructions.
 *
 * @param <I> The generic ID type, usually {@link Long}.
 * @author Bauke Scholtz
 * @see Audit
 */
public abstract class AuditListener<I extends Comparable<I> & Serializable> {

	private static final Map<Class<?>, Map<PropertyDescriptor, Map<?, Object>>> AUDITABLE_PROPERTIES = new ConcurrentHashMap<>();

	@PersistenceContext
	private EntityManager entityManager;

	@PostLoad
	public void beforeUpdate(BaseEntity<I> entity) {
		getAuditableProperties(entity).forEach((property, values) -> values.put(entity.getId(), invokeMethod(entity, property.getReadMethod())));
	}

	@PreUpdate
	public void afterUpdate(BaseEntity<I> entity) {
		getAuditableProperties(entity).forEach((property, values) -> {
			if (values.containsKey(entity.getId())) {
				Object newValue = invokeMethod(entity, property.getReadMethod());
				Object oldValue = values.remove(entity.getId());

				if (!Objects.equals(oldValue, newValue)) {
					saveAuditedChange(entity, property, oldValue, newValue);
				}
			}
		});
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Map<PropertyDescriptor, Map<I, Object>> getAuditableProperties(BaseEntity<I> entity) {
		Map auditableProperties = AUDITABLE_PROPERTIES.computeIfAbsent(entity.getClass(), type -> {
			Set<String> auditablePropertyNames = entityManager.getMetamodel().entity(type).getDeclaredAttributes().stream()
				.filter(a -> a.getJavaMember() instanceof Field && ((Field) a.getJavaMember()).getAnnotation(Audit.class) != null)
				.map(Attribute::getName)
				.collect(toSet());

			try {
				return stream(getBeanInfo(type).getPropertyDescriptors())
					.filter(p -> auditablePropertyNames.contains(p.getName()))
					.collect(toConcurrentMap(identity(), v -> new ConcurrentHashMap<>()));
			}
			catch (IntrospectionException e) {
				throw new UnsupportedOperationException(e);
			}
		});

		return auditableProperties;
	}

	/**
	 * <p>
	 * Example implementation:
	 * <pre>
	 * YourAuditedChange yourAuditedChange = new YourAuditedChange();
	 * yourAuditedChange.setTimestamp(Instant.now());
	 * yourAuditedChange.setUser(activeUser);
	 * yourAuditedChange.setEntityName(entityManager.getMetamodel().entity(entity.getClass()).getName());
	 * yourAuditedChange.setEntityId(entity.getId());
	 * yourAuditedChange.setPropertyName(property.getName());
	 * yourAuditedChange.setOldValue(oldValue != null ? oldValue.toString() : null);
	 * yourAuditedChange.setNewValue(newValue != null ? newValue.toString() : null);
	 * inject(YourAuditedChangeService.class).persist(yourAuditedChange);
	 * </pre>
	 *
	 * @param entity The parent entity.
	 * @param property The audited property.
	 * @param oldValue The old value.
	 * @param newValue The new value.
	 */
	protected abstract void saveAuditedChange(BaseEntity<I> entity, PropertyDescriptor property, Object oldValue, Object newValue);

	/**
	 * <p>
	 * Work around for CDI inject not working in JPA EntityListener. Usage:
	 * <pre>
	 * YourAuditedChangeService service = inject(YourAuditedChangeService.class);
	 * </pre>
	 *
	 * @param <T> The generic CDI managed bean type.
	 * @param type The CDI managed bean type.
	 * @return The CDI managed instance.
	 */
	protected static <T> T inject(Class<T> type) {
		return CDI.current().select(type).get();
	}

}