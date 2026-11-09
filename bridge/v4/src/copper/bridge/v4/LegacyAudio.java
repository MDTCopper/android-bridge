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
    public Sound newSound(FileHandle file) {
        return new LegacySound(file);
    }

    @Override
    public Music newMusic(FileHandle file) {
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

    private static class SilentAudioDevice implements AudioDevice {
        @Override
        public boolean isMono() {
            return false;
        }

        @Override
        public void writeSamples(short[] samples, int offset, int numSamples) {
        }

        @Override
        public void writeSamples(float[] samples, int offset, int numSamples) {
        }

        @Override
        public int getLatency() {
            return 0;
        }

        @Override
        public void dispose() {
        }

        @Override
        public void setVolume(float volume) {
        }
    }

    private static class SilentAudioRecorder implements AudioRecorder {
        @Override
        public void read(short[] samples, int offset, int numSamples) {
            java.util.Arrays.fill(samples, offset, offset + numSamples, (short)0);
        }

        @Override
        public void dispose() {
        }
    }

    @Override
    public void dispose() {
        JvmCall.disposeAudio();
    }
}