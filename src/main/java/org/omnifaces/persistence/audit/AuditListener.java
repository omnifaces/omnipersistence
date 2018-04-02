/*
 * Copyright 2018 OmniFaces
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
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
import javax.persistence.PostLoad;
import javax.persistence.PreUpdate;
import javax.persistence.metamodel.Attribute;

import org.omnifaces.persistence.model.BaseEntity;
import org.omnifaces.persistence.service.BaseEntityService;

/**
 * <p>
 * See {@link Audit} for usage instructions.
 *
 * @param <I> The generic ID type. This is used to associate auditable properties with a specific entity.
 * @author Bauke Scholtz
 * @see Audit
 */
public abstract class AuditListener<I extends Comparable<I> & Serializable> {

	private static final Map<Class<?>, Map<PropertyDescriptor, Map<?, Object>>> AUDITABLE_PROPERTIES = new ConcurrentHashMap<>();

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
		Map auditableProperties = AUDITABLE_PROPERTIES.computeIfAbsent(entity.getClass(), k -> {
			BaseEntityService<?, ?> baseEntityService = BaseEntityService.getCurrentInstance();
			Set<String> auditablePropertyNames = baseEntityService.getMetamodel(entity).getDeclaredAttributes().stream()
				.filter(a -> a.getJavaMember() instanceof Field && ((Field) a.getJavaMember()).isAnnotationPresent(Audit.class))
				.map(Attribute::getName)
				.collect(toSet());

			try {
				return stream(getBeanInfo(baseEntityService.getProvider().getEntityType(entity)).getPropertyDescriptors())
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