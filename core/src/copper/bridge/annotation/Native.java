package copper.bridge.annotation;

import java.lang.annotation.*;

/**
 * Names the native entry that performs a native method: a C++ function's qualified name below {@code
 * copper::bridge}, e.g. {@code "jre::Loader::LoadJreLibraries"}, taking this declaration's parameters with
 * the JNI prefix.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Native {
    String value();
}
