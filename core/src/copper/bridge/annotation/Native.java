package copper.bridge.annotation;

import java.lang.annotation.*;

/**
 * Names the native entry that performs a native method: the C++ function's qualified name below
 * {@code copper::bridge}, e.g. {@code "jre::Loader::LoadJreLibraries"}, whose parameters are this
 * declaration's with the JNI prefix added.
 *
 * <p>The generator declares that function from this declaration before taking its address, so a
 * definition whose signature disagrees is a link error rather than a native method that silently
 * binds nothing. {@code CLASS} retention, like {@link UsedByNative}, for tooling rather than the VM.</p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Native {
    /** The entry's qualified name below {@code copper::bridge}. */
    String value();
}
