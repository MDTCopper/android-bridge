package copper.bridge.v1;

import copper.bridge.annotation.BridgeOverride;
import copper.bridge.gen.JvmCall;

import com.badlogic.gdx.*;
import com.badlogic.gdx.audio.*;
import com.badlogic.gdx.files.*;

/**
 * libGDX audio for the bridge. Sound effects cross to ART, which owns the audio device; music and direct audio
 * devices stay silent: uCore's music class is disabled and the game only loads sound effects.
 */
public class BridgeAudio implements Audio {

    @Override
    @BridgeOverride
    public AudioDevice newAudioDevice(int samplingRate, boolean isMono) {
        return new BridgeAudioDevice(isMono);
    }

    @Override
    @BridgeOverride
    public AudioRecorder newAudioRecorder(int samplingRate, boolean isMono) {
        return new BridgeAudioRecorder();
    }

    @Override
    @BridgeOverride
    public Sound newSound(FileHandle file) {
        return new BridgeSound(JvmCall.newSound(file.name(), file.readBytes()));
    }

    @Override
    @BridgeOverride
    public Music newMusic(FileHandle file) {
        return new BridgeMusic();
    }
}
