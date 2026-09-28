package copper.bridge.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Labels a generated call queued to the peer's main thread; reading it on a generated method tells the caller
 * which channel a call takes.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Post {
}
