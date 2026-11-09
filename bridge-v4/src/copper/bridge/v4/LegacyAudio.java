package copper.bridge.v4;

import io.anuke.arc.audio.*;
import io.anuke.arc.files.*;

import copper.bridge.gen.*;

/**
 * The abstract arc.Audio of the 6 line's low end. Only ever constructed below 113, where the class still exists -
 * which is why the branch compiles at v112 - so its references are checked against v112, not the range's top.
 */
public class LegacyAudio extends io.anuke.arc.Audio {

    public LegacyAudio() {
    }

    @Override
    public Sound newSound(Fi file) {
        return new LegacySound(file);
    }

    @Override
    public Music newMusic(Fi file) {
        return new LegacyMusic(file);
    }

    /** Nothing on this platform records audio; the interface still wants an answer, and arc's own mock answers null. */
    @Override
    public AudioDevice newAudioDevice(int samplingRate, boolean isMono) {
        return null;
    }

    @Override
    public AudioRecorder newAudioRecorder(int samplingRate, boolean isMono) {
        return null;
    }

    @Override
    public void dispose() {
        JvmCall.disposeAudio();
    }
}