package copper.bridge.jvm;

/** What the event handler needs from a branch's input. Same reason as {@link GraphicsHost}: no concrete type. */
public interface InputHost {
    void onPause();

    void onResume();

    void backTap();
}
