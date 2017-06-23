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