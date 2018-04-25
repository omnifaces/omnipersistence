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
package org.omnifaces.persistence.model;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.persistence.EnumType;

import org.omnifaces.persistence.service.BaseEntityService;

/**
 * <p>
 * Basic annotation to change default behaviour of <code>@Enumerated</code>. When this
 * annotation is added to the declaration of declaration of custom <code>enum</code>
 * then persisting its value by using <code>@Enumerated</code> will be changed.
 * If <code>EnumType.ORDINAL</code> is used as <code>type</code> (default) then the enum will
 * be persisted basing on <code>fieldName</code> that's holding custom state of
 * <code>enum</code> that must correspond to the real property of <code>enum</code> and
 * must be of <code>int</code> type, otherwise default mapping will be applied.
 * If <code>EnumType.STRING</code> is used as <code>type</code> then the enum
 * will be persisted basing on <code>fieldName</code> that must correspond to the real
 * property of <code>enum</code> and  must be of <code>String</code> type, otherwise default
 * mapping will be applied.
 * <p>
 * Modified behaviour of <code>@Enumerated</code>
 * will be used when the entities are managed by {@link BaseEntityService}. Noted should
 * be that when any entity managed by {@link BaseEntityService} uses the <code>enum</code>
 * with <code>@EnumMapping</code> applied then it will be persisted in a modified way way even
 * when the other entity is not managed by {@link BaseEntityService} to entail consistency.
 * <p>
 * This is particularly useful if you want to avoid drawbacks of standard JPA enum mappings,
 * like reordering or renaming, by enforcing consistency between enum modifications during
 * application development, like addition of new, or removal of old enum values. By using
 * this annotation developer has full control of what part of the enum will be mapped to the database.
 *
 * <p>
 * Usage of this annotation is as follows:
 * <pre>
 * {@literal @}EnumMapping(fieldName = "id")
 * public enum ProductStatus {
 *
 *     IN_STOCK(1), OUT_OF_STOCK(10), DISCONTINUED(20);
 *
 *     private int id;
 *
 *     private ProductStatus(int id) {
 *         this.id = id;
 *     }
 *
 *     public int getId() {
 *         return id;
 *     }
 *
 * }
 * </pre>
 * <p>
 * and
 * <pre>
 * {@literal @}EnumMapping(type = EnumType.STRING, fieldName = "code")
 * public enum UserRole {
 *
 *     USER("USR"), EMPLOYEE("EMP"), MANAGER("MGR");
 *
 *     private String code;
 *
 *     private UserRole(String code) {
 *         this.code = code;
 *     }
 *
 *     public String getCode() {
 *         return code;
 *     }
 *
 * }
 * </pre>
 * <p>
 * with
 * <pre>
 * {@literal @}Entity
 * public class Product extends GeneratedIdEntity&lt;Long&gt; {
 *
 *     {@literal @}Enumerated
 *     private ProductStatus productStatus;
 *
 *     {@literal @}ElementCollection
 *     {@literal @}Enumerated(STRING)
 *     private Set&lt;UserRole&gt; userRoles = new HashSet&lt;&gt;();
 *
 *     // ...
 * }
 * </pre>
 *
 * In such setup persistence provider will save to the database the status as either
 * 1, 10 or 20 (as opposed to 0, 1 or 2) and role as either USR, EMP or MGR (as opposed to
 * USER, EMPLOYEE or MANAGER).
 *
 * @author Sergey Kuntsel
 */
@Target(TYPE)
@Retention(RUNTIME)
public @interface EnumMapping {

    /**
     * Specifies the enum mapping type. Defaults to {@link EnumType#ORDINAL}.
     * @return The enum mapping type.
     */
    public EnumType type() default EnumType.ORDINAL;

    /**
     * Specifies the corresponding field name of mapped enum.
     * @return The corresponding field name of mapped enum.
     */
    public String fieldName();

}
