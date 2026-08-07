package io.github.dinganzhi.gunfumetro.command;

import io.github.dinganzhi.gunfumetro.MetroData;
import io.github.dinganzhi.gunfumetro.MetroMapTexture;
import io.github.dinganzhi.gunfumetro.config.GunfuMetroConfigScreen;
import io.github.dinganzhi.gunfumetro.config.ModClientConfig;
import io.github.dinganzhi.gunfumetro.music.MusicManager;
import io.github.dinganzhi.gunfumetro.screen.MetroMapScreen;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class GunfuMetroCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(GunfuMetroCommand.class);
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(literal("gunfumetro")
                    // ---------- hud ----------
                    .then(literal("hud")
                            .then(literal("on").executes(ctx -> {
                                try {
                                    ModClientConfig.INSTANCE.instance().showHud = true;
                                    ModClientConfig.INSTANCE.save();
                                    sendFeedback("command.gunfu-metro.hud_on");
                                    return 1;
                                } catch (Exception e) {
                                    sendError("command.gunfu-metro.hud_on_fail", e.getMessage());
                                    LOGGER.error("Command failed", e);
                                    return 0;
                                }
                            }))
                            .then(literal("off").executes(ctx -> {
                                try {
                                    ModClientConfig.INSTANCE.instance().showHud = false;
                                    ModClientConfig.INSTANCE.save();
                                    sendFeedback("command.gunfu-metro.hud_off");
                                    return 1;
                                } catch (Exception e) {
                                    sendError("command.gunfu-metro.hud_off_fail", e.getMessage());
                                    LOGGER.error("Command failed", e);
                                    return 0;
                                }
                            }))
                            .then(literal("reload").executes(ctx -> {
                                try {
                                    MetroData.reload();
                                    sendFeedback("command.gunfu-metro.hud_reload_done");
                                    return 1;
                                } catch (Exception e) {
                                    sendError("command.gunfu-metro.reload_fail", e.getMessage());
                                    LOGGER.error("Command failed", e);
                                    return 0;
                                }
                            }))
                    )
                    // ---------- music ----------
                    .then(literal("music")
                            .then(literal("play").executes(ctx -> {
                                try {
                                    if (MusicManager.getInstance().getPlaylistSize() == 0)
                                        MusicManager.getInstance().loadPlaylist();
                                    if (MusicManager.getInstance().getPlaylistSize() == 0) {
                                        sendError("command.gunfu-metro.music_play_empty");
                                        return 1;
                                    }
                                    MusicManager.getInstance().play();
                                    sendFeedback("command.gunfu-metro.music_play");
                                    return 1;
                                } catch (Exception e) {
                                    sendError("command.gunfu-metro.music_play_fail", e.getMessage());
                                    LOGGER.error("Command failed", e);
                                    return 0;
                                }
                            }))
                            .then(literal("pause").executes(ctx -> {
                                try {
                                    MusicManager.getInstance().pause();
                                    sendFeedback("command.gunfu-metro.music_pause");
                                    return 1;
                                } catch (Exception e) {
                                    sendError("command.gunfu-metro.music_pause_fail", e.getMessage());
                                    LOGGER.error("Command failed", e);
                                    return 0;
                                }
                            }))
                            .then(literal("next").executes(ctx -> {
                                try {
                                    MusicManager.getInstance().playNext();
                                    sendFeedback("command.gunfu-metro.music_next");
                                    return 1;
                                } catch (Exception e) {
                                    sendError("command.gunfu-metro.music_next_fail", e.getMessage());
                                    LOGGER.error("Command failed", e);
                                    return 0;
                                }
                            }))
                            .then(literal("previous").executes(ctx -> {
                                try {
                                    MusicManager.getInstance().playPrevious();
                                    sendFeedback("command.gunfu-metro.music_previous");
                                    return 1;
                                } catch (Exception e) {
                                    sendError("command.gunfu-metro.music_previous_fail", e.getMessage());
                                    LOGGER.error("Command failed", e);
                                    return 0;
                                }
                            }))
                            .then(literal("forward").then(argument("seconds", IntegerArgumentType.integer(1)).executes(ctx -> {
                                try {
                                    int sec = IntegerArgumentType.getInteger(ctx, "seconds");
                                    MusicManager.getInstance().seekForward(sec);
                                    sendFeedback("command.gunfu-metro.music_forward", sec);
                                    return 1;
                                } catch (Exception e) {
                                    sendError("command.gunfu-metro.music_forward_fail", e.getMessage());
                                    LOGGER.error("Command failed", e);
                                    return 0;
                                }
                            })))
                            .then(literal("backward").then(argument("seconds", IntegerArgumentType.integer(1)).executes(ctx -> {
                                try {
                                    int sec = IntegerArgumentType.getInteger(ctx, "seconds");
                                    MusicManager.getInstance().seekBackward(sec);
                                    sendFeedback("command.gunfu-metro.music_backward", sec);
                                    return 1;
                                } catch (Exception e) {
                                    sendError("command.gunfu-metro.music_backward_fail", e.getMessage());
                                    LOGGER.error("Command failed", e);
                                    return 0;
                                }
                            })))
                            .then(literal("goto").then(argument("id", StringArgumentType.word()).executes(ctx -> {
                                try {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    boolean found = MusicManager.getInstance().gotoTrack(id);
                                    if (found) {
                                        sendFeedback("command.gunfu-metro.music_goto_found", id);
                                    } else {
                                        sendError("command.gunfu-metro.music_goto_not_found", id);
                                    }
                                    return 1;
                                } catch (Exception e) {
                                    sendError("command.gunfu-metro.music_goto_fail", e.getMessage());
                                    LOGGER.error("Command failed", e);
                                    return 0;
                                }
                            })))
                            .then(literal("mode").then(argument("mode", StringArgumentType.word()).suggests((ctx, builder) -> {
                                for (ModClientConfig.PlayMode mode : ModClientConfig.PlayMode.values()) {
                                    builder.suggest(mode.name().toLowerCase());
                                }
                                return builder.buildFuture();
                            }).executes(ctx -> {
                                try {
                                    String modeStr = StringArgumentType.getString(ctx, "mode").toUpperCase();
                                    ModClientConfig.PlayMode mode = ModClientConfig.PlayMode.valueOf(modeStr);
                                    ModClientConfig.INSTANCE.instance().playMode = mode;
                                    ModClientConfig.INSTANCE.save();
                                    sendFeedback("command.gunfu-metro.music_mode_set", mode.name());
                                    return 1;
                                } catch (IllegalArgumentException e) {
                                    sendError("command.gunfu-metro.music_mode_invalid", "single_loop, list_loop, shuffle, order");
                                    return 0;
                                } catch (Exception e) {
                                    sendError("command.gunfu-metro.music_mode_fail", e.getMessage());
                                    LOGGER.error("Command failed", e);
                                    return 0;
                                }
                            })))
                            .then(literal("hud")
                                    .then(literal("on").executes(ctx -> {
                                        try {
                                            ModClientConfig.INSTANCE.instance().showMusicHud = true;
                                            ModClientConfig.INSTANCE.save();
                                            sendFeedback("command.gunfu-metro.music_hud_on");
                                            return 1;
                                        } catch (Exception e) {
                                            sendError("command.gunfu-metro.music_hud_on_fail", e.getMessage());
                                            LOGGER.error("Command failed", e);
                                            return 0;
                                        }
                                    }))
                                    .then(literal("off").executes(ctx -> {
                                        try {
                                            ModClientConfig.INSTANCE.instance().showMusicHud = false;
                                            ModClientConfig.INSTANCE.save();
                                            sendFeedback("command.gunfu-metro.music_hud_off");
                                            return 1;
                                        } catch (Exception e) {
                                            sendError("command.gunfu-metro.music_hud_off_fail", e.getMessage());
                                            LOGGER.error("Command failed", e);
                                            return 0;
                                        }
                                    }))
                            )
                            .then(literal("reload").executes(ctx -> {
                                try {
                                    MusicManager.getInstance().loadPlaylist();
                                    sendFeedback("command.gunfu-metro.music_reload_done");
                                    return 1;
                                } catch (Exception e) {
                                    sendError("command.gunfu-metro.music_reload_fail", e.getMessage());
                                    LOGGER.error("Command failed", e);
                                    return 0;
                                }
                            }))
                            // 音量控制
                            .then(literal("volume").then(argument("value", IntegerArgumentType.integer(0, 100)).executes(ctx -> {
                                try {
                                    int val = IntegerArgumentType.getInteger(ctx, "value");
                                    float volume = val / 100.0f;
                                    MusicManager.getInstance().setVolume(volume);
                                    sendFeedback("command.gunfu-metro.music_volume_set", val);
                                    return 1;
                                } catch (Exception e) {
                                    sendError("command.gunfu-metro.music_volume_fail", e.getMessage());
                                    LOGGER.error("Command failed", e);
                                    return 0;
                                }
                            })))
                    )
                    // ---------- config ----------
                    .then(literal("config")
                            .then(literal("gui").executes(ctx -> {
                                try {
                    Minecraft.getInstance().execute(() -> {
                        try {
                            Screen parent = Minecraft.getInstance().screen;
                            Screen configScreen = GunfuMetroConfigScreen.create(parent);
                            if (configScreen != null) {
                                Minecraft.getInstance().setScreen(configScreen);
                                reopenScreen(configScreen);
                                sendFeedback("command.gunfu-metro.config_gui_opened");
                            } else {
                                sendError("command.gunfu-metro.config_gui_null");
                            }
                        } catch (Exception e) {
                            sendError("command.gunfu-metro.config_gui_fail", e.getMessage());
                            LOGGER.error("Failed to open config screen", e);
                        }
                    });
                                    return 1;
                                } catch (Exception e) {
                                    sendError("command.gunfu-metro.command_exception", e.getMessage());
                                    LOGGER.error("Command failed", e);
                                    return 0;
                                }
                            }))
                            .then(literal("reload").executes(ctx -> {
                                try {
                                    ModClientConfig cfg = ModClientConfig.INSTANCE.instance();
                                    if (cfg.reloadFromDisk()) {
                                        MusicManager.getInstance().setVolume(cfg.musicVolume);
                                        sendFeedback("command.gunfu-metro.config_reload_done");
                                    } else {
                                        sendFeedback("command.gunfu-metro.config_reload_missing");
                                    }
                                    return 1;
                                } catch (Exception e) {
                                    sendError("command.gunfu-metro.config_reload_fail", e.getMessage());
                                    LOGGER.error("Command failed", e);
                                    return 0;
                                }
                            }))
                            .then(literal("reset").executes(ctx -> {
                                try {
                                    ModClientConfig cfg = ModClientConfig.INSTANCE.instance();
                                    cfg.resetToDefaults();
                                    MusicManager.getInstance().setVolume(cfg.musicVolume);
                                    sendFeedback("command.gunfu-metro.config_reset_done");
                                    return 1;
                                } catch (Exception e) {
                                    sendError("command.gunfu-metro.config_reset_fail", e.getMessage());
                                    LOGGER.error("Command failed", e);
                                    return 0;
                                }
                            }))
                    )
                    // ---------- map ----------
                    .then(literal("map")
                            .then(literal("regenerate").executes(ctx -> {
                                try {
                                    MetroMapTexture.regenerate();
                                    sendFeedback("command.gunfu-metro.map_regenerate_done");
                                    return 1;
                                } catch (Exception e) {
                                    sendError("command.gunfu-metro.map_regenerate_fail", e.getMessage());
                                    LOGGER.error("Command failed", e);
                                    return 0;
                                }
                            }))
                            .then(literal("reload").executes(ctx -> {
                                try {
                                    MetroData.reload();
                                    MetroMapTexture.regenerate();
                                    sendFeedback("command.gunfu-metro.map_reload_done");
                                    return 1;
                                } catch (Exception e) {
                                    sendError("command.gunfu-metro.map_reload_fail", e.getMessage());
                                    LOGGER.error("Command failed", e);
                                    return 0;
                                }
                            }))
                            .then(literal("gui").executes(ctx -> {
                                try {
                                    Minecraft.getInstance().execute(() -> {
                                        try {
                                            MetroMapScreen mapScreen = new MetroMapScreen();
                                            Minecraft.getInstance().setScreen(mapScreen);
                                            reopenScreen(mapScreen);
                                            sendFeedback("command.gunfu-metro.map_gui_opened");
                                        } catch (Exception e) {
                                            sendError("command.gunfu-metro.map_gui_fail", e.getMessage());
                                            LOGGER.error("Failed to open map screen", e);
                                        }
                                    });
                                    return 1;
                                } catch (Exception e) {
                                    sendError("command.gunfu-metro.command_exception", e.getMessage());
                                    LOGGER.error("Command failed", e);
                                    return 0;
                                }
                            }))
                    )
            );
        });
    }

    private static void sendFeedback(String key, Object... args) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("[Gunfu Metro] ")
                            .append(Component.translatable(key, args)),
                    false);
        }
    }

    private static void sendError(String key, Object... args) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("[Gunfu Metro] ")
                            .append(Component.translatable(key, args).withStyle(ChatFormatting.RED)),
                    false);
        }
    }

    /**
     * 部分情况下（从聊天栏执行客户端命令时）界面会被命令调度器再次关闭，
     * 这里在延迟后检查，若界面已被关闭则重新打开，保证命令能稳定打开界面。
     */
    private static void reopenScreen(Screen target) {
        new Thread(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
            }
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().screen != target) {
                    Minecraft.getInstance().setScreen(target);
                }
            });
        }, "Gunfu-Reopen-Screen").start();
    }
}