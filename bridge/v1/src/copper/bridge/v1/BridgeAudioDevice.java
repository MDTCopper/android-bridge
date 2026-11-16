package copper.bridge.v1;

import copper.bridge.annotation.BridgeOverride;

import com.badlogic.gdx.audio.AudioDevice;

/** A direct audio device that discards samples; the bridge has no audio-device caller. */
public class BridgeAudioDevice implements AudioDevice {
    private final boolean mono;

    public BridgeAudioDevice(boolean mono) {
        this.mono = mono;
    }

    @Override
    @BridgeOverride
    public boolean isMono() {
        return mono;
    }

    @Override
    @BridgeOverride
    public void writeSamples(short[] samples, int offset, int numSamples) {
    }

    @Override
    @BridgeOverride
    public void writeSamples(float[] samples, int offset, int numSamples) {
    }

    @Override
    @BridgeOverride
    public int getLatency() {
        return 0;
    }

    @Override
    @BridgeOverride
    public void dispose() {
    }

    @Override
    @BridgeOverride
    public void setVolume(float volume) {
    }
}
