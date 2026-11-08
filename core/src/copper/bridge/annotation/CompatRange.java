package copper.bridge.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides the API check's range for the file that declares the annotated class.
 *
 * <p>For a branch class that is only loaded from a newer version behind a probe: that file's references need
 * not exist at the branch floor. Each value is a game ref (tag or commit); {@code "default"} keeps that end
 * from the branch's gradle.properties.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface CompatRange {
    String floor() default "default";
    String ceiling() default "default";
}
