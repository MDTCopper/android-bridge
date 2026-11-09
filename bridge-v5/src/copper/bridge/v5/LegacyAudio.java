package copper.bridge.v5;

import io.anuke.arc.audio.*;
import io.anuke.arc.files.*;

import copper.bridge.gen.*;
import copper.bridge.v1.SilentAudioDevice;
import copper.bridge.v1.SilentAudioRecorder;

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

    /** Raw PCM streaming has no sink; Sound and Music are served by ART. A silent device keeps the non-null contract. */
    @Override
    public AudioDevice newAudioDevice(int samplingRate, boolean isMono) {
        return new SilentAudioDevice();
    }

    /** Nothing captures raw PCM; a silent recorder keeps the non-null contract. */
    @Override
    public AudioRecorder newAudioRecorder(int samplingRate, boolean isMono) {
        return new SilentAudioRecorder();
    }

    @Override
    public void dispose() {
        JvmCall.disposeAudio();
    }
}