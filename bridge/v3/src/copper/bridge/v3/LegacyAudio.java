package copper.bridge.v3;

import copper.bridge.annotation.BridgeOverride;

import io.anuke.arc.audio.*;
import io.anuke.arc.files.*;

import copper.bridge.gen.*;

/**
 * arc.Audio for the 6 line's low end: 84 turned it from an interface into an abstract class, which this branch
 * extends. The Sound implementation carries both handle shapes itself, so there is nothing to choose here.
 */
public class LegacyAudio extends io.anuke.arc.Audio {

    public LegacyAudio() {
    }

    @BridgeOverride
    public Sound newSound(FileHandle file) {
        return new LegacySound(file);
    }

    @BridgeOverride
    public Music newMusic(FileHandle file) {
        return new LegacyMusic(file);
    }

    /** Raw PCM streaming has no sink; Sound and Music are served by ART. A silent device keeps the non-null contract. */
    @BridgeOverride
    public AudioDevice newAudioDevice(int samplingRate, boolean isMono) {
        return new SilentAudioDevice();
    }

    /** Nothing captures raw PCM; a silent recorder keeps the non-null contract. */
    @BridgeOverride
    public AudioRecorder newAudioRecorder(int samplingRate, boolean isMono) {
        return new SilentAudioRecorder();
    }

    private static class SilentAudioDevice implements AudioDevice {
        @BridgeOverride
        public boolean isMono() {
            return false;
        }

        @BridgeOverride
        public void writeSamples(short[] samples, int offset, int numSamples) {
        }

        @BridgeOverride
        public void writeSamples(float[] samples, int offset, int numSamples) {
        }

        @BridgeOverride
        public int getLatency() {
            return 0;
        }

        @BridgeOverride
        public void dispose() {
        }

        @BridgeOverride
        public void setVolume(float volume) {
        }
    }

    private static class SilentAudioRecorder implements AudioRecorder {
        @BridgeOverride
        public void read(short[] samples, int offset, int numSamples) {
            java.util.Arrays.fill(samples, offset, offset + numSamples, (short)0);
        }

        @BridgeOverride
        public void dispose() {
        }
    }

    @BridgeOverride
    public void dispose() {
        JvmCall.disposeAudio();
    }
}