package copper.bridge.art;

import android.media.*;

import copper.bridge.*;
import copper.bridge.annotation.*;
import copper.bridge.gen.*;
import copper.bridge.util.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * The game's sound and music on the device. The JVM side cannot reach {@code android.media}, so it hands the bytes
 * over once and keeps the handles this class answers.
 *
 * <p>Sound calls are queued on one thread because {@code SoundPool.play} waits for a free stream and a bus call
 * answers synchronously; music runs inline because its state has to be readable when the call returns.</p>
 */
public class Audio {
    /** arc's Android backend uses the same count: enough voices for a busy frame, few enough to stay cheap. */
    private static final int MAX_STREAMS = 16;
    /** SoundPool keeps stream ids for as long as the stream lives, so a sound tracks a bounded number of them. */
    private static final int SOUND_STREAMS = 8;
    /** Under the bridge's cache folder, beside the natives and the tmp directory the host already owns. */
    private static final String DIR = "audio";

    private final File dir;
    private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();

    /** Handle to stream, and back, for the ids the game holds. */
    private final Map<Integer, Integer> streamOfHandle = new HashMap<>();
    private final Map<Integer, Integer> handleOfStream = new HashMap<>();
    /** The streams each sound started, newest first, so {@code stop()} can end all of them. */
    private final Map<Integer, Deque<Integer>> streamsOfSound = new HashMap<>();

    private final Map<Integer, MediaPlayer> musics = new HashMap<>();
    private final Set<Integer> prepared = new HashSet<>();

    private SoundPool pool;
    private int nextMusic = 1;
    private int nextHandle = 1;

    public Audio() {
        // the staged files outlive a run, so they belong with the bridge's cache, not with the app's throwaway one
        this.dir = new File(Bridge.options.cacheFolder, DIR);
        Thread worker = new Thread(this::drain, "copper-audio");
        worker.setDaemon(true);
        worker.start();
    }

    private void drain() {
        while (true) {
            try {
                Runnable run = queue.take();
                try {
                    run.run();
                } catch (Throwable t) {
                    Log.warn("audio: " + t);
                }
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /** Queues one sound operation: they run in order, so a volume set right after a play finds its stream. */
    private void run(Runnable operation) {
        queue.add(operation);
    }

    //region sound

    /** Stages the bytes and hands them to SoundPool; the id it answers is the sound's identity. */
    @ArtDirectHandler
    public int newSound(String name, byte[] data) {
        File file = stage(name, data);
        if (file == null)
            return -1;
        return pool().load(file.getAbsolutePath(), 1);
    }

    /** Starts a stream and answers the handle for it; the stream itself appears on the audio thread. */
    @ArtDirectHandler
    public int playSound(int sound, float volume, float pitch, float pan, boolean loop) {
        if (sound <= 0 || (!loop && volume <= 0.001f))
            return -1;

        int handle = nextHandle++;
        run(() -> {
            float[] gains = gains(pan, volume);
            int stream = pool().play(sound, gains[0], gains[1], 1, loop ? -1 : 0, pitch);
            if (stream == 0)
                return;
            streamOfHandle.put(handle, stream);
            handleOfStream.put(stream, handle);
            Deque<Integer> streams = streamsOfSound.computeIfAbsent(sound, key -> new ArrayDeque<>());
            streams.addFirst(stream);
            while (streams.size() > SOUND_STREAMS)
                streams.removeLast();
        });
        return handle;
    }

    @ArtDirectHandler
    public void stopSound(int sound) {
        run(() -> {
            Deque<Integer> streams = streamsOfSound.remove(sound);
            if (streams == null)
                return;
            for (int stream : streams) {
                Integer handle = handleOfStream.remove(stream);
                if (handle != null)
                    streamOfHandle.remove(handle);
                pool().stop(stream);
            }
        });
    }

    /** SoundPool pauses and resumes every stream at once; it has no per-sound switch. */
    @ArtDirectHandler
    public void pauseSound() {
        run(() -> pool().autoPause());
    }

    @ArtDirectHandler
    public void resumeSound() {
        run(() -> pool().autoResume());
    }

    @ArtDirectHandler
    public void stopSoundAt(int sound, int handle) {
        run(() -> {
            Integer stream = streamOfHandle.remove(handle);
            if (stream == null)
                return;
            handleOfStream.remove(stream);
            Deque<Integer> streams = streamsOfSound.get(sound);
            if (streams != null)
                streams.remove(stream);
            pool().stop(stream);
        });
    }

    @ArtDirectHandler
    public void pauseSoundAt(int handle) {
        run(() -> {
            Integer stream = streamOfHandle.get(handle);
            if (stream != null)
                pool().pause(stream);
        });
    }

    @ArtDirectHandler
    public void resumeSoundAt(int handle) {
        run(() -> {
            Integer stream = streamOfHandle.get(handle);
            if (stream != null)
                pool().resume(stream);
        });
    }

    @ArtDirectHandler
    public void setSoundVolume(int handle, float volume) {
        run(() -> {
            Integer stream = streamOfHandle.get(handle);
            if (stream != null)
                pool().setVolume(stream, volume, volume);
        });
    }

    @ArtDirectHandler
    public void setSoundPitch(int handle, float pitch) {
        run(() -> {
            Integer stream = streamOfHandle.get(handle);
            if (stream != null)
                pool().setRate(stream, pitch);
        });
    }

    @ArtDirectHandler
    public void setSoundPan(int handle, float pan, float volume) {
        run(() -> {
            Integer stream = streamOfHandle.get(handle);
            if (stream == null)
                return;
            float[] gains = gains(pan, volume);
            pool().setVolume(stream, gains[0], gains[1]);
        });
    }

    @ArtDirectHandler
    public void setSoundLooping(int handle, boolean looping) {
        run(() -> {
            Integer stream = streamOfHandle.get(handle);
            if (stream != null)
                pool().setLoop(stream, looping ? -1 : 0);
        });
    }

    @ArtDirectHandler
    public void disposeSound(int sound) {
        run(() -> disposeSoundNow(sound));
    }

    private void disposeSoundNow(int sound) {
        Deque<Integer> streams = streamsOfSound.remove(sound);
        if (streams != null) {
            for (int stream : streams) {
                Integer handle = handleOfStream.remove(stream);
                if (handle != null)
                    streamOfHandle.remove(handle);
                if (pool != null)
                    pool.stop(stream);
            }
        }
        if (pool != null)
            pool.unload(sound);
    }

    //endregion
    //region music

    /** Stages the bytes and answers the handle for them; the player is prepared on the first play. */
    @ArtDirectHandler
    public int newMusic(String name, byte[] data) {
        File file = stage(name, data);
        if (file == null)
            return -1;

        int music = nextMusic++;
        MediaPlayer player = new MediaPlayer();
        try {
            player.setDataSource(file.getAbsolutePath());
        } catch (Throwable t) {
            Log.warn("audio: cannot open " + name + ": " + t);
            player.release();
            return -1;
        }
        player.setOnCompletionListener(completed -> ArtCall.onMusicComplete(music));
        musics.put(music, player);
        return music;
    }

    @ArtDirectHandler
    public void playMusic(int music) {
        MediaPlayer player = musics.get(music);
        if (player == null)
            return;
        try {
            if (!prepared.contains(music)) {
                player.prepare();
                prepared.add(music);
            }
            player.start();
        } catch (Throwable t) {
            Log.warn("audio: cannot play music " + music + ": " + t);
        }
    }

    @ArtDirectHandler
    public void pauseMusic(int music) {
        MediaPlayer player = musics.get(music);
        if (player == null)
            return;
        try {
            if (player.isPlaying())
                player.pause();
        } catch (Throwable t) {
            Log.warn("audio: cannot pause music " + music + ": " + t);
        }
    }

    /** Stops and rewinds, which is what arc's own music does; a later play prepares it again. */
    @ArtDirectHandler
    public void stopMusic(int music) {
        MediaPlayer player = musics.get(music);
        if (player == null)
            return;
        try {
            if (prepared.contains(music))
                player.seekTo(0);
            player.stop();
        } catch (Throwable t) {
            Log.warn("audio: cannot stop music " + music + ": " + t);
        }
        prepared.remove(music);
    }

    @ArtDirectHandler
    public boolean isMusicPlaying(int music) {
        MediaPlayer player = musics.get(music);
        if (player == null)
            return false;
        try {
            return player.isPlaying();
        } catch (Throwable t) {
            return false;
        }
    }

    @ArtDirectHandler
    public void setMusicLooping(int music, boolean looping) {
        MediaPlayer player = musics.get(music);
        if (player == null)
            return;
        try {
            player.setLooping(looping);
        } catch (Throwable t) {
            Log.warn("audio: cannot loop music " + music + ": " + t);
        }
    }

    @ArtDirectHandler
    public void setMusicVolume(int music, float volume) {
        MediaPlayer player = musics.get(music);
        if (player == null)
            return;
        player.setVolume(volume, volume);
    }

    @ArtDirectHandler
    public void setMusicPan(int music, float pan, float volume) {
        MediaPlayer player = musics.get(music);
        if (player == null)
            return;
        float[] gains = gains(pan, volume);
        player.setVolume(gains[0], gains[1]);
    }

    @ArtDirectHandler
    public float getMusicPosition(int music) {
        MediaPlayer player = musics.get(music);
        if (player == null)
            return 0f;
        try {
            return player.getCurrentPosition() / 1000f;
        } catch (Throwable t) {
            return 0f;
        }
    }

    @ArtDirectHandler
    public void setMusicPosition(int music, float seconds) {
        MediaPlayer player = musics.get(music);
        if (player == null)
            return;
        try {
            if (!prepared.contains(music)) {
                player.prepare();
                prepared.add(music);
            }
            player.seekTo((int) (seconds * 1000));
        } catch (Throwable t) {
            Log.warn("audio: cannot seek music " + music + ": " + t);
        }
    }

    @ArtDirectHandler
    public void disposeMusic(int music) {
        MediaPlayer player = musics.remove(music);
        if (player != null) {
            player.release();
        }
        prepared.remove(music);
    }

    /** Called when the game disposes its audio: releases the pool and every player. The staged files stay. */
    @ArtDirectHandler
    public void disposeAudio() {
        run(() -> {
            for (int sound : new ArrayList<>(streamsOfSound.keySet()))
                disposeSoundNow(sound);
            if (pool != null) {
                pool.release();
                pool = null;
            }
        });
        for (int music : new ArrayList<>(musics.keySet()))
            disposeMusic(music);
    }

    //endregion
    //region shared

    /** SoundPool and MediaPlayer take the left and right gains, not a pan and a volume. */
    private static float[] gains(float pan, float volume) {
        float left = volume;
        float right = volume;
        if (pan < 0) {
            right *= 1 - Math.abs(pan);
        } else if (pan > 0) {
            left *= 1 - Math.abs(pan);
        }
        return new float[]{left, right};
    }

    /** The pool is created on the first sound: a game that never plays one never pays for it. */
    private SoundPool pool() {
        if (pool == null) {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            pool = new SoundPool.Builder()
                    .setAudioAttributes(attributes)
                    .setMaxStreams(MAX_STREAMS)
                    .build();
        }
        return pool;
    }

    /**
     * Writes the bytes where SoundPool and MediaPlayer can open them: both take a path or a descriptor, never the
     * game's own file objects, and an internal file of the game lives inside its jar rather than on the disk.
     *
     * <p>The file is the cache: a name that is already there with the same content is reused untouched, so the same
     * sound is written once per content change rather than once per run.</p>
     */
    private File stage(String name, byte[] data) {
        if (data == null || data.length == 0)
            return null;

        File file = new File(dir, fileName(name));
        try {
            if (file.isFile() && sameContent(file, data))
                return file;
            if (!dir.isDirectory() && !dir.mkdirs())
                return null;
            try (OutputStream output = new FileOutputStream(file)) {
                output.write(data);
            }
            return file;
        } catch (IOException e) {
            Log.warn("audio: cannot stage " + name + ": " + e);
            return null;
        }
    }

    /** Whether the staged file already holds exactly these bytes. */
    private static boolean sameContent(File file, byte[] data) throws IOException {
        if (file.length() != data.length)
            return false;
        try (InputStream input = new FileInputStream(file)) {
            byte[] staged = new byte[data.length];
            int read = 0;
            while (read < staged.length) {
                int count = input.read(staged, read, staged.length - read);
                if (count < 0)
                    return false;
                read += count;
            }
            return Arrays.equals(staged, data);
        }
    }

    /** The game's path flattened into one file name: two sounds of the same base name must not share a file. */
    private static String fileName(String path) {
        String text = path == null ? "audio" : path;
        return text.replace('/', '_').replace('\\', '_').replace(':', '_');
    }

    //endregion
}
