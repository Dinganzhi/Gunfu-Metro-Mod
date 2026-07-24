package io.github.dinganzhi.gunfumetro.music;

import io.github.dinganzhi.gunfumetro.config.ModClientConfig;
import javazoom.jl.decoder.*;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.file.*;
import java.util.*;

public class MusicManager {
    private static MusicManager instance;
    private final List<MusicData> playlist = new ArrayList<>();
    private volatile int currentIndex = -1;
    private volatile boolean paused = false;
    private volatile boolean playing = false;
    private volatile boolean stopFlag = false;
    private Thread playerThread = null;

    private int source = -1;
    private final int[] buffers = new int[8];
    private boolean openalInit = false;

    private int sampleRate = 44100;
    private int channels = 2;
    private volatile long currentFrameCount = 0;
    private static final int SAMPLES_PER_FRAME = 1152;

    private final Object playLock = new Object();

    public static MusicManager getInstance() {
        if (instance == null) instance = new MusicManager();
        return instance;
    }

    private MusicManager() {}

    private void initOpenAL() {
        if (openalInit) return;
        source = AL10.alGenSources();
        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = AL10.alGenBuffers();
        }
        // 设为相对音源，避免干扰游戏声音
        AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
        AL10.alSource3f(source, AL10.AL_POSITION, 0, 0, 0);
        // 从配置读取音量
        float volume = ModClientConfig.INSTANCE.instance().musicVolume;
        AL10.alSourcef(source, AL10.AL_GAIN, Math.max(0.0f, Math.min(1.0f, volume)));
        AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE);
        openalInit = true;
        checkALError("initOpenAL");
    }

    private void cleanupOpenAL() {
        if (!openalInit) return;
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
            if (AL10.alIsBuffer(buffers[i])) AL10.alDeleteBuffers(buffers[i]);
            buffers[i] = -1;
        }
        openalInit = false;
    }

    private void checkALError(String operation) {
        int error = AL10.alGetError();
        if (error != AL10.AL_NO_ERROR) {
            System.err.println("[OpenAL] Error after " + operation + ": " + error);
        }
    }

    // 音量控制方法
    public void setVolume(float volume) {
        volume = Math.max(0.0f, Math.min(1.0f, volume));
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
        return ModClientConfig.INSTANCE.instance().musicVolume;
    }

    private void playFile(String filePath) {
        synchronized (playLock) {
            stopPlayingOnly(true);
            stopFlag = false;
            playing = true;
            paused = false;

            playerThread = new Thread(() -> {
                FileInputStream fis = null;
                Bitstream bitstream = null;
                boolean normalEnd = false;
                try {
                    File audioFile = new File(filePath);
                    if (!audioFile.exists()) {
                        System.err.println("[GunfuMetro] File not found: " + filePath);
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
                    if (firstHeader == null) return;
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
                        if (header == null) break;
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
                            while (paused && !stopFlag) Thread.sleep(100);
                            if (!paused && !stopFlag) AL10.alSourcePlay(source);
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
                        if (normalEnd) break;

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

                        if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING && !paused && !stopFlag) {
                            AL10.alSourcePlay(source);
                        }
                        Thread.sleep(5);
                    }
                } catch (Exception e) {
                    if (playing && !stopFlag) e.printStackTrace();
                } finally {
                    try { if (bitstream != null) bitstream.close(); } catch (Exception ignored) {}
                    try { if (fis != null) fis.close(); } catch (Exception ignored) {}
                    synchronized (playLock) {
                        if (Thread.currentThread() == playerThread) {
                            cleanupOpenAL();
                            playing = false;
                            paused = false;
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
                try { oldThread.join(100); } catch (InterruptedException ignored) {}
            }
        }
    }

    public void stop() {
        synchronized (playLock) {
            stopFlag = true;
            if (playerThread != null && playerThread.isAlive()) {
                playerThread.interrupt();
                try { playerThread.join(50); } catch (InterruptedException ignored) {}
            }
            playing = false;
            paused = false;
            currentIndex = -1;
            cleanupOpenAL();
        }
    }

    public void loadPlaylist() {
        synchronized (playLock) {
            stop();
            playlist.clear();
            ModClientConfig config = ModClientConfig.INSTANCE.instance();
            try {
                InputStream stream;
                if (config.musicSourceType == ModClientConfig.MusicSourceType.REMOTE) {
                    stream = new URL(config.musicRemoteUrl).openStream();
                } else {
                    Path localFile = Minecraft.getInstance().gameDirectory.toPath().resolve(config.musicLocalPath);
                    if (!Files.exists(localFile)) {
                        System.err.println("[GunfuMetro] Playlist file not found: " + localFile);
                        return;
                    }
                    stream = Files.newInputStream(localFile);
                }
                Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream);
                NodeList musicNodes = doc.getElementsByTagName("music");
                for (int i = 0; i < musicNodes.getLength(); i++) {
                    Element e = (Element) musicNodes.item(i);
                    String id = getText(e, "id");
                    String title = getText(e, "title");
                    String author = getText(e, "author");
                    int length = 0;
                    try { length = Integer.parseInt(getText(e, "length")); } catch (NumberFormatException ignored) {}
                    String path = getText(e, "path");
                    playlist.add(new MusicData(id, title, author, length, path));
                }
                stream.close();
                System.out.println("[GunfuMetro] Loaded " + playlist.size() + " tracks.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private String getText(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        return list.getLength() > 0 ? list.item(0).getTextContent().trim() : "";
    }

    public void play() {
        if (playlist.isEmpty()) {
            loadPlaylist();
            if (playlist.isEmpty()) return;
        }
        if (paused) {
            paused = false;
            return;
        }
        int targetIndex = currentIndex;
        if (targetIndex < 0) targetIndex = 0;
        if (targetIndex >= playlist.size()) targetIndex = 0;
        synchronized (playLock) {
            stopPlayingOnly(true);
            currentIndex = targetIndex;
            playing = true;
            currentFrameCount = 0;
            MusicData data = playlist.get(currentIndex);
            playFile(resolvePath(data.path));
        }
    }

    public boolean gotoTrack(String id) {
        for (int i = 0; i < playlist.size(); i++) {
            if (playlist.get(i).id.equals(id)) {
                playTrackAtIndex(i);
                return true;
            }
        }
        return false;
    }

    private void playTrackAtIndex(int index) {
        if (index < 0 || index >= playlist.size()) return;
        synchronized (playLock) {
            stopPlayingOnly(true);
            currentIndex = index;
            playing = true;
            currentFrameCount = 0;
            MusicData data = playlist.get(index);
            playFile(resolvePath(data.path));
        }
    }

    private String resolvePath(String rawPath) {
        ModClientConfig config = ModClientConfig.INSTANCE.instance();
        if (config.musicSourceType == ModClientConfig.MusicSourceType.REMOTE) return rawPath;
        return Minecraft.getInstance().gameDirectory.toPath().resolve(rawPath).toString();
    }

    public void playNext() {
        if (playlist.isEmpty()) return;
        ModClientConfig config = ModClientConfig.INSTANCE.instance();
        switch (config.playMode) {
            case SHUFFLE -> currentIndex = new Random().nextInt(playlist.size());
            case SINGLE_LOOP -> { /* 保持当前索引 */ }
            default -> currentIndex = (currentIndex + 1) % playlist.size();
        }
        playTrackAtIndex(currentIndex);
    }

    public void playPrevious() {
        if (playlist.isEmpty()) return;
        ModClientConfig config = ModClientConfig.INSTANCE.instance();
        switch (config.playMode) {
            case SHUFFLE -> currentIndex = new Random().nextInt(playlist.size());
            case SINGLE_LOOP -> {}
            default -> currentIndex = (currentIndex - 1 + playlist.size()) % playlist.size();
        }
        playTrackAtIndex(currentIndex);
    }

    public void pause() {
        if (playing && !paused) paused = true;
    }

    public void seekForward(int seconds) { seekRelative(seconds); }
    public void seekBackward(int seconds) { seekRelative(-seconds); }

    private void seekRelative(int deltaSeconds) {
        if (currentIndex < 0 || !playing) return;
        synchronized (playLock) {
            long newFrame = currentFrameCount + (long)(deltaSeconds * sampleRate / SAMPLES_PER_FRAME);
            if (newFrame < 0) newFrame = 0;
            MusicData data = getCurrentMusic();
            if (data != null && data.length > 0) {
                long maxFrame = (long)(data.length * sampleRate / SAMPLES_PER_FRAME);
                if (deltaSeconds > 0 && newFrame >= maxFrame) {
                    playNext();
                    return;
                } else if (deltaSeconds < 0 && newFrame <= 0) {
                    newFrame = 0;
                }
            }
            String currentFile = resolvePath(playlist.get(currentIndex).path);
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
                currentIndex = (currentIndex + 1) % playlist.size();
                playTrackAtIndex(currentIndex);
                break;
            case SHUFFLE:
                // 随机模式播完暂停
                pause();
                break;
            case ORDER:
                // 顺序模式播完暂停
                pause();
                break;
        }
    }

    public boolean isPlaying() { return playing && !paused; }

    public MusicData getCurrentMusic() {
        return (currentIndex >= 0 && currentIndex < playlist.size()) ? playlist.get(currentIndex) : null;
    }

    public long getCurrentPosition() {
        if (sampleRate > 0) return (long) (currentFrameCount * SAMPLES_PER_FRAME / sampleRate);
        return 0;
    }

    public int getTotalLength() {
        MusicData data = getCurrentMusic();
        return data != null ? data.length : 0;
    }
}