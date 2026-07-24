package io.github.dinganzhi.gunfumetro.config;

import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Path;

public class ModClientConfig {
    public static final ConfigClassHandler<ModClientConfig> INSTANCE = ConfigClassHandler.createBuilder(ModClientConfig.class)
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(Path.of(FabricLoader.getInstance().getConfigDir().toString(), "gunfu-metro-client.json"))
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
    public String musicRemoteUrl = "https://example.com/musics.xml";

    @SerialEntry
    public String musicLocalPath = "gunfu-metro/musics.xml";

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
}