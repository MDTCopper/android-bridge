package copper.bridge.v1;

import io.anuke.arc.audio.AudioDevice;

/** Raw PCM streaming has no sink; Sound and Music are served by ART. A silent device keeps the non-null contract. */
public class SilentAudioDevice implements AudioDevice {
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
