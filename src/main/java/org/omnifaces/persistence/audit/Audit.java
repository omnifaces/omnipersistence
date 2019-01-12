/*
 * Copyright 2019 OmniFaces
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
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.persistence.EntityListeners;

/**
 * <p>
 * Instructions:
 * <ol>
 * <li>Extend {@link AuditListener}
 * <li>Declare that listener as {@link EntityListeners} on your entity.
 * <li>Put {@link Audit} annotation on column of interest.
 * <li>Profit.
 * </ol>
 * <p>
 * Usage example:
 * <pre>
 * &#64;Entity
 * &#64;EntityListeners(YourAuditListener.class)
 * public class YourEntity extends BaseEntity&lt;Long&gt; {
 *
 *     &#64;Audit
 *     &#64;Column
 *     private String email;
 *
 *     // ...
 * }
 * </pre>
 *
 * @author Bauke Scholtz
 */
@Retention(RUNTIME)
@Target(FIELD)
public @interface Audit {
	//
}