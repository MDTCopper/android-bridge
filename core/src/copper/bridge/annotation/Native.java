package copper.bridge.annotation;

import java.lang.annotation.*;

/**
 * Names the native entry that performs a {@code native} method: a C++ function's qualified name below
 * {@code copper::bridge}, for example {@code "jre::Loader::LoadJreLibraries"}.
 *
 * <p>Native functions take the declaration's parameters, each widened to {@code JNIEnv*}, {@code jclass} and the
 * JNI type the bridge maps the Java type to ({@code jint}, {@code jstring}, ...): the C++ side reads them exactly as
 * the declaration wrote them, so a signature change is a compile error there rather than a wrong call at run
 * time.</p>
 *
 * <p>Declared on a {@code native} method; the annotation is required - a native method without it fails the
 * processor ("'x' is native and names no entry"). The entry name must end in a C++ function name, and one entry may
 * only be claimed by one declaration. The processor turns the whole set into the generated binding tables, one per
 * declaring class, registered when that VM loads the library; the name therefore must agree with a real function in
 * {@code native/src/}.</p>
 *
 * <p>Examples: {@code BridgeActivity#nativeWindow}, {@code Bootstrap#loadJreLibraries}, {@code NativeLogBackend}.</p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Native {
    /** The C++ entry, written as {@code "<namespace>::<function>"} below {@code copper::bridge}. */
    String value();
}
