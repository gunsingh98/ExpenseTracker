package com.expensetracker.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;

/**
 * Like {@code @ConditionalOnProperty}, but only matches when the property is
 * present <em>and non-blank</em>. Needed because our env-var-backed config emits
 * empty-string defaults (e.g. {@code ${DRIVE_FILE_ID:}}), which the stock
 * {@code @ConditionalOnProperty} treats as "present" — activating both the Drive
 * and local-file stores at once.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(ConditionalOnNonBlankProperty.OnNonBlankPropertyCondition.class)
public @interface ConditionalOnNonBlankProperty {

    /** The property name to check (e.g. {@code app.drive.file-id}). */
    String value();

    class OnNonBlankPropertyCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Map<String, Object> attrs =
                    metadata.getAnnotationAttributes(ConditionalOnNonBlankProperty.class.getName());
            if (attrs == null) {
                return false;
            }
            String name = (String) attrs.get("value");
            String value = context.getEnvironment().getProperty(name);
            return value != null && !value.isBlank();
        }
    }
}
