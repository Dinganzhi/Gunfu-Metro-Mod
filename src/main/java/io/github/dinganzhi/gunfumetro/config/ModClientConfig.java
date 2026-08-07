package io.github.dinganzhi.gunfumetro.config;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModClientConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModClientConfig.class);
    public static final Path CONFIG_PATH = Path.of(
            FabricLoader.getInstance().getConfigDir().toString(), "gunfu-metro-client.json");

    public static final ConfigClassHandler<ModClientConfig> INSTANCE = ConfigClassHandler.createBuilder(ModClientConfig.class)
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(CONFIG_PATH)
                    .setJson5(true)
                    .build())
            .build();

    @SerialEntry
    public boolean showHud = true;

    @SerialEntry
    public boolean showTitleBar = true;

    @SerialEntry
    public float uiScale = 1.0f;

    @SerialEntry
    public MusicSourceType musicSourceType = MusicSourceType.LOCAL;

    @SerialEntry
    public String musicRemoteUrl = "https://example.com/musics.json";

    @SerialEntry
    public String musicLocalPath = "gunfu-metro/musics.json";

    @SerialEntry
    public PlayMode playMode = PlayMode.ORDER;

    @SerialEntry
    public boolean showMusicHud = true;

    @SerialEntry
    public float musicVolume = 0.5f; // 0.0 ~ 1.0

    public enum MusicSourceType {
        LOCAL, REMOTE
    }

    public enum PlayMode {
        SINGLE_LOOP, LIST_LOOP, SHUFFLE, ORDER
    }

    public void save() {
        INSTANCE.save();
    }

    public void load() {
        INSTANCE.load();
    }

    public ModClientConfig instance() {
        return INSTANCE.instance();
    }

    /** 将另一个实例的全部字段拷贝到当前实例，保持实例对象不变（避免 YACL load() 替换实例）。 */
    public void applyFrom(ModClientConfig source) {
        this.showHud = source.showHud;
        this.showTitleBar = source.showTitleBar;
        this.uiScale = source.uiScale;
        this.musicSourceType = source.musicSourceType;
        this.musicRemoteUrl = source.musicRemoteUrl;
        this.musicLocalPath = source.musicLocalPath;
        this.playMode = source.playMode;
        this.showMusicHud = source.showMusicHud;
        this.musicVolume = source.musicVolume;
    }

    /** 将配置恢复为默认值（字段初始值）并保存到磁盘。 */
    public void resetToDefaults() {
        applyFrom(new ModClientConfig());
        save();
    }

    /**
     * 从磁盘重新读取配置到当前实例（保持实例对象不变）。
     * 通过全新实例 + 字段拷贝实现，不使用 YACL 的 load()，因此不会替换实例对象，
     * 正在打开的配置界面 / 其它已持有实例引用的代码仍能读到最新值。
     *
     * @return 是否成功读取到文件内容（文件不存在或解析失败时为 false）
     */
    public boolean reloadFromDisk() {
        if (!Files.exists(CONFIG_PATH))
            return false;
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            ModClientConfig fresh = new Gson().fromJson(reader, ModClientConfig.class);
            if (fresh == null)
                return false;
            applyFrom(fresh);
            return true;
        } catch (JsonSyntaxException | IOException e) {
            LOGGER.error("Failed to reload config from {}", CONFIG_PATH, e);
            return false;
        }
    }
}
