package io.github.dinganzhi.gunfumetro.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.dinganzhi.gunfumetro.config.ModClientConfig;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.minecraft.client.Minecraft;
import org.lwjgl.openal.AL10;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MusicManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(MusicManager.class);
    private static final int NETWORK_TIMEOUT_MS = 4000;

    private static MusicManager instance;
    private volatile List<MusicData> playlist = List.of();
    private final Random random = new Random();
    private volatile int currentIndex = -1;
    private volatile boolean paused = false;
    private volatile boolean playing = false;
    private volatile boolean stopFlag = false;
    private Thread playerThread = null;

    private int source = -1;
    private final int[] buffers = new int[8];
    private boolean openalInit = false;

    /** 最近一次播放/列表加载失败的原因（客户端线程读取，播放线程写入） */
    private volatile String lastError = null;

    private int sampleRate = 44100;
    private int channels = 2;
    private volatile long currentFrameCount = 0;
    private static final int SAMPLES_PER_FRAME = 1152;

    private final Object playLock = new Object();

    public static MusicManager getInstance() {
        if (instance == null)
            instance = new MusicManager();
        return instance;
    }

    private MusicManager() {
    }

    private void initOpenAL() {
        if (openalInit)
            return;
        source = AL10.alGenSources();
        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = AL10.alGenBuffers();
        }
        // 设为相对音源，避免干扰游戏声音
        AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
        AL10.alSource3f(source, AL10.AL_POSITION, 0, 0, 0);
        // 从配置读取音量
        float volume = ModClientConfig.INSTANCE.instance().musicVolume;
        AL10.alSourcef(source, AL10.AL_GAIN, clampVolume(volume));
        AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE);
        openalInit = true;
        checkALError("initOpenAL");
    }

    private void cleanupOpenAL() {
        if (!openalInit)
            return;
        if (AL10.alIsSource(source)) {
            AL10.alSourceStop(source);
            int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
            if (processed > 0) {
                int[] unqueued = new int[processed];
                AL10.alSourceUnqueueBuffers(source, unqueued);
            }
            AL10.alDeleteSources(source);
        }
        source = -1;
        for (int i = 0; i < buffers.length; i++) {
            if (AL10.alIsBuffer(buffers[i]))
                AL10.alDeleteBuffers(buffers[i]);
            buffers[i] = -1;
        }
        openalInit = false;
    }

    private void checkALError(String operation) {
        int error = AL10.alGetError();
        if (error != AL10.AL_NO_ERROR) {
            LOGGER.warn("OpenAL error after {}: {}", operation, error);
        }
    }

    private static float clampVolume(float volume) {
        return Math.max(0.0f, Math.min(1.0f, volume));
    }

    // 音量控制方法
    public void setVolume(float volume) {
        volume = clampVolume(volume);
        if (openalInit && AL10.alIsSource(source)) {
            AL10.alSourcef(source, AL10.AL_GAIN, volume);
        }
        // 同时保存到配置
        ModClientConfig.INSTANCE.instance().musicVolume = volume;
        ModClientConfig.INSTANCE.save();
    }

    public float getVolume() {
        if (openalInit && AL10.alIsSource(source)) {
            return AL10.alGetSourcef(source, AL10.AL_GAIN);
        }
        return clampVolume(ModClientConfig.INSTANCE.instance().musicVolume);
    }

    private void playFile(String filePath) {
        synchronized (playLock) {
            stopPlayingOnly(true);
            stopFlag = false;
            playing = true;
            paused = false;
            lastError = null;

            playerThread = new Thread(() -> {
                FileInputStream fis = null;
                Bitstream bitstream = null;
                boolean normalEnd = false;
                try {
                    File audioFile = new File(filePath);
                    if (!audioFile.exists()) {
                        lastError = "File not found: " + filePath;
                        LOGGER.error("File not found: {}", filePath);
                        return;
                    }
                    fis = new FileInputStream(audioFile);
                    bitstream = new Bitstream(fis);
                    Decoder decoder = new Decoder();

                    long framesToSkip = currentFrameCount;
                    if (framesToSkip > 0) {
                        Header skipHeader;
                        while (framesToSkip > 0 && (skipHeader = bitstream.readFrame()) != null) {
                            bitstream.closeFrame();
                            framesToSkip--;
                        }
                    }

                    Header firstHeader = bitstream.readFrame();
                    if (firstHeader == null)
                        return;
                    sampleRate = firstHeader.frequency();
                    channels = (firstHeader.mode() == Header.SINGLE_CHANNEL) ? 1 : 2;
                    SampleBuffer firstOutput = (SampleBuffer) decoder.decodeFrame(firstHeader, bitstream);
                    bitstream.closeFrame();

                    cleanupOpenAL();
                    initOpenAL();

                    ByteBuffer pcm0 = toPCM(firstOutput);
                    AL10.alBufferData(buffers[0], getALFormat(channels), pcm0, sampleRate);
                    checkALError("alBufferData first");
                    AL10.alSourceQueueBuffers(source, buffers[0]);
                    currentFrameCount++;

                    for (int i = 1; i < buffers.length; i++) {
                        Header header = bitstream.readFrame();
                        if (header == null)
                            break;
                        SampleBuffer samp = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                        ByteBuffer pcm = toPCM(samp);
                        AL10.alBufferData(buffers[i], getALFormat(channels), pcm, sampleRate);
                        AL10.alSourceQueueBuffers(source, buffers[i]);
                        currentFrameCount++;
                        bitstream.closeFrame();
                    }

                    AL10.alSourcePlay(source);

                    while (playing && !stopFlag) {
                        if (paused) {
                            AL10.alSourcePause(source);
                            while (paused && !stopFlag)
                                Thread.sleep(100);
                            if (!paused && !stopFlag)
                                AL10.alSourcePlay(source);
                            continue;
                        }

                        int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
                        while (processed > 0 && !stopFlag) {
                            int buf = AL10.alSourceUnqueueBuffers(source);
                            Header header = bitstream.readFrame();
                            if (header == null) {
                                normalEnd = true;
                                stopFlag = true;
                                break;
                            }
                            SampleBuffer samp = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                            ByteBuffer pcm = toPCM(samp);
                            AL10.alBufferData(buf, getALFormat(channels), pcm, sampleRate);
                            AL10.alSourceQueueBuffers(source, buf);
                            currentFrameCount++;
                            bitstream.closeFrame();
                            processed--;
                        }
                        if (normalEnd)
                            break;

                        if (AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED) == 0 && !stopFlag && playing) {
                            Header header = bitstream.readFrame();
                            if (header == null) {
                                normalEnd = true;
                                stopFlag = true;
                                break;
                            }
                            SampleBuffer samp = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                            ByteBuffer pcm = toPCM(samp);
                            int freeBuf = buffers[0];
                            AL10.alBufferData(freeBuf, getALFormat(channels), pcm, sampleRate);
                            AL10.alSourceQueueBuffers(source, freeBuf);
                            currentFrameCount++;
                            bitstream.closeFrame();
                        }

                        if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING && !paused
                                && !stopFlag) {
                            AL10.alSourcePlay(source);
                        }
                        Thread.sleep(5);
                    }
                } catch (InterruptedException e) {
                    if (playing && !stopFlag)
                        LOGGER.info("Player thread interrupted");
                } catch (Exception e) {
                    lastError = e.getMessage() == null ? e.toString() : e.getMessage();
                    if (playing && !stopFlag)
                        LOGGER.error("Error while playing '{}'", filePath, e);
                } finally {
                    try {
                        if (bitstream != null)
                            bitstream.close();
                    } catch (Exception ignored) {
                    }
                    try {
                        if (fis != null)
                            fis.close();
                    } catch (Exception ignored) {
                    }
                    synchronized (playLock) {
                        if (Thread.currentThread() == playerThread) {
                            cleanupOpenAL();
                            playing = false;
                            paused = false;
                            if (lastError != null)
                                LOGGER.info("Playback ended with error: {}", lastError);
                            if (normalEnd) {
                                handleTrackEnd();
                            }
                        }
                    }
                }
            }, "Gunfu-Music-Player");
            playerThread.setDaemon(true);
            playerThread.start();
        }
    }

    private ByteBuffer toPCM(SampleBuffer sampleBuffer) {
        short[] samples = sampleBuffer.getBuffer();
        int len = sampleBuffer.getBufferLength();
        ByteBuffer buf = ByteBuffer.allocateDirect(len * 2);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        ShortBuffer shortBuf = buf.asShortBuffer();
        shortBuf.put(samples, 0, len);
        buf.position(0);
        buf.limit(len * 2);
        return buf;
    }

    private int getALFormat(int ch) {
        return ch == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
    }

    private void stopPlayingOnly(boolean waitForThread) {
        stopFlag = true;
        Thread oldThread = playerThread;
        if (oldThread != null && oldThread.isAlive()) {
            oldThread.interrupt();
            if (waitForThread) {
                try {
                    oldThread.join(100);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    public void stop() {
        synchronized (playLock) {
            stopFlag = true;
            if (playerThread != null && playerThread.isAlive()) {
                playerThread.interrupt();
                try {
                    playerThread.join(50);
                } catch (InterruptedException ignored) {
                }
            }
            playing = false;
            paused = false;
            currentIndex = -1;
            cleanupOpenAL();
        }
    }

    /**
     * 从配置指定的源（本地文件或远程 URL）读取播放列表。
     * 远程请求设置了连接/读取超时，避免卡死游戏主线程，并限制为 http/https 协议。
     */
    public void loadPlaylist() {
        List<MusicData> loaded = readPlaylist();
        synchronized (playLock) {
            stop();
            playlist = (loaded == null) ? List.of() : Collections.unmodifiableList(loaded);
        }
        LOGGER.info("Loaded {} tracks.", playlist.size());
    }

    /** 后台线程异步加载播放列表，避免阻塞渲染/输入线程 */
    public void loadPlaylistAsync() {
        Thread t = new Thread(() -> {
            try {
                loadPlaylist();
            } catch (Exception e) {
                LOGGER.error("Failed to load playlist in background", e);
            }
        }, "Gunfu-Playlist-Loader");
        t.setDaemon(true);
        t.start();
    }

    private List<MusicData> readPlaylist() {
        ModClientConfig config = ModClientConfig.INSTANCE.instance();
        try (InputStream stream = openPlaylistStream(config)) {
            if (stream == null)
                return null;
            JsonArray entries = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonArray();
            List<MusicData> result = new ArrayList<>(entries.size());
            for (JsonElement element : entries) {
                if (!element.isJsonObject())
                    continue;
                JsonObject o = element.getAsJsonObject();
                result.add(new MusicData(
                        stringOf(o, "id"),
                        stringOf(o, "title"),
                        stringOf(o, "author"),
                        intOf(o, "length"),
                        stringOf(o, "path")));
            }
            return result;
        } catch (Exception e) {
            LOGGER.error("Failed to read playlist", e);
            return null;
        }
    }

    private static String stringOf(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return (e != null && !e.isJsonNull()) ? e.getAsString() : "";
    }

    private static int intOf(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber())
            return e.getAsInt();
        return 0;
    }

    private InputStream openPlaylistStream(ModClientConfig config) throws IOException {
        if (config.musicSourceType == ModClientConfig.MusicSourceType.REMOTE) {
            URL url = new URL(config.musicRemoteUrl);
            String protocol = url.getProtocol().toLowerCase(Locale.ROOT);
            if (!"http".equals(protocol) && !"https".equals(protocol)) {
                LOGGER.error("Unsupported playlist URL protocol: '{}'", protocol);
                return null;
            }
            URLConnection conn = url.openConnection();
            conn.setConnectTimeout(NETWORK_TIMEOUT_MS);
            conn.setReadTimeout(NETWORK_TIMEOUT_MS);
            conn.setUseCaches(false);
            return conn.getInputStream();
        }
        Path localFile = Minecraft.getInstance().gameDirectory.toPath().resolve(config.musicLocalPath);
        if (!Files.exists(localFile)) {
            LOGGER.error("Playlist file not found: {}", localFile);
            return null;
        }
        return Files.newInputStream(localFile);
    }

    public void play() {
        List<MusicData> tracks = playlist;
        if (tracks.isEmpty()) {
            loadPlaylist();
            tracks = playlist;
            if (tracks.isEmpty())
                return;
        }
        if (paused) {
            paused = false;
            return;
        }
        int targetIndex = currentIndex;
        if (targetIndex < 0)
            targetIndex = 0;
        if (targetIndex >= tracks.size())
            targetIndex = 0;
        synchronized (playLock) {
            stopPlayingOnly(true);
            currentIndex = targetIndex;
            playing = true;
            currentFrameCount = 0;
            MusicData data = tracks.get(currentIndex);
            playFile(resolvePath(data.path));
        }
    }

    public boolean gotoTrack(String id) {
        List<MusicData> tracks = playlist;
        for (int i = 0; i < tracks.size(); i++) {
            if (tracks.get(i).id.equals(id)) {
                playTrackAtIndex(i);
                return true;
            }
        }
        return false;
    }

    private void playTrackAtIndex(int index) {
        List<MusicData> tracks = playlist;
        if (index < 0 || index >= tracks.size())
            return;
        synchronized (playLock) {
            stopPlayingOnly(true);
            currentIndex = index;
            playing = true;
            currentFrameCount = 0;
            MusicData data = tracks.get(index);
            playFile(resolvePath(data.path));
        }
    }

    private String resolvePath(String rawPath) {
        ModClientConfig config = ModClientConfig.INSTANCE.instance();
        if (config.musicSourceType == ModClientConfig.MusicSourceType.REMOTE)
            return rawPath;
        return Minecraft.getInstance().gameDirectory.toPath().resolve(rawPath).toString();
    }

    public void playNext() {
        List<MusicData> tracks = playlist;
        if (tracks.isEmpty())
            return;
        ModClientConfig config = ModClientConfig.INSTANCE.instance();
        switch (config.playMode) {
            case SHUFFLE -> currentIndex = randomIndex();
            case SINGLE_LOOP -> { /* 保持当前索引 */ }
            default -> currentIndex = (currentIndex + 1) % tracks.size();
        }
        playTrackAtIndex(currentIndex);
    }

    public void playPrevious() {
        List<MusicData> tracks = playlist;
        if (tracks.isEmpty())
            return;
        ModClientConfig config = ModClientConfig.INSTANCE.instance();
        switch (config.playMode) {
            case SHUFFLE -> currentIndex = randomIndex();
            case SINGLE_LOOP -> {
            }
            default -> currentIndex = (currentIndex - 1 + tracks.size()) % tracks.size();
        }
        playTrackAtIndex(currentIndex);
    }

    /** 随机选曲，尽量避免与当前曲目重复 */
    private int randomIndex() {
        int size = playlist.size();
        if (size <= 1)
            return 0;
        int next;
        do {
            next = random.nextInt(size);
        } while (next == currentIndex);
        return next;
    }

    public void pause() {
        if (playing && !paused)
            paused = true;
    }

    public void seekForward(int seconds) {
        seekRelative(seconds);
    }

    public void seekBackward(int seconds) {
        seekRelative(-seconds);
    }

    private void seekRelative(int deltaSeconds) {
        if (currentIndex < 0 || !playing)
            return;
        synchronized (playLock) {
            long newFrame = currentFrameCount + (long) (deltaSeconds * sampleRate / SAMPLES_PER_FRAME);
            if (newFrame < 0)
                newFrame = 0;
            MusicData data = getCurrentMusic();
            if (data != null && data.length > 0) {
                long maxFrame = (long) (data.length * sampleRate / SAMPLES_PER_FRAME);
                if (deltaSeconds > 0 && newFrame >= maxFrame) {
                    playNext();
                    return;
                } else if (deltaSeconds < 0 && newFrame <= 0) {
                    newFrame = 0;
                }
            }
            List<MusicData> tracks = playlist;
            String currentFile = resolvePath(tracks.get(currentIndex).path);
            stopPlayingOnly(true);
            currentFrameCount = newFrame;
            playing = true;
            paused = false;
            playFile(currentFile);
        }
    }

    private void handleTrackEnd() {
        ModClientConfig config = ModClientConfig.INSTANCE.instance();
        switch (config.playMode) {
            case SINGLE_LOOP:
                play();
                break;
            case LIST_LOOP:
                List<MusicData> tracks = playlist;
                if (!tracks.isEmpty())
                    currentIndex = (currentIndex + 1) % tracks.size();
                playTrackAtIndex(currentIndex);
                break;
            case SHUFFLE:
            case ORDER:
                // 随机 / 顺序模式播完暂停
                LOGGER.info("Playback finished");
                break;
        }
    }

    public boolean isPlaying() {
        return playing && !paused;
    }

    public int getPlaylistSize() {
        return playlist.size();
    }

    /** 最近一次播放失败的原因；无失败时为 null（只读，供命令反馈使用） */
    public String getLastError() {
        return lastError;
    }

    public MusicData getCurrentMusic() {
        List<MusicData> tracks = playlist;
        return (currentIndex >= 0 && currentIndex < tracks.size()) ? tracks.get(currentIndex) : null;
    }

    public long getCurrentPosition() {
        if (sampleRate > 0)
            return Math.max(0, currentFrameCount * SAMPLES_PER_FRAME / sampleRate);
        return 0;
    }

    public int getTotalLength() {
        MusicData data = getCurrentMusic();
        return data != null ? data.length : 0;
    }
}
