package copper.bridge.annotation;

import java.lang.annotation.*;

/**
 * The name the packer writes for this method. It exists because Java has no way to declare two methods that differ
 * only in return type: a branch written against one game shape declares the other shape under another name here, and
 * the packer renames it before the API check and packing.
 *
 * <p>The declared parameter and return types are kept, so two methods annotated with the same name become JVM
 * overloads that differ in their return type. Calls to the source name are rewritten too, so a caller may use either
 * name.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface BridgeRename {
    /** The method name the branch must carry in the class file. */
    String value();
}
