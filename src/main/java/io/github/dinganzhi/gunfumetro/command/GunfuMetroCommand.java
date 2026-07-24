package io.github.dinganzhi.gunfumetro.command;

import io.github.dinganzhi.gunfumetro.MetroData;
import io.github.dinganzhi.gunfumetro.MetroMapTexture;
import io.github.dinganzhi.gunfumetro.config.ModClientConfig;
import io.github.dinganzhi.gunfumetro.music.MusicManager;
import io.github.dinganzhi.gunfumetro.screen.MetroMapScreen;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.*;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class GunfuMetroCommand {
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(literal("gunfumetro")
                    // ---------- hud ----------
                    .then(literal("hud")
                            .then(literal("on").executes(ctx -> {
                                try {
                                    ModClientConfig.INSTANCE.instance().showHud = true;
                                    ModClientConfig.INSTANCE.save();
                                    sendFeedback("HUD 已开启");
                                    return 1;
                                } catch (Exception e) {
                                    sendError("HUD 开启失败: " + e.getMessage());
                                    e.printStackTrace();
                                    return 0;
                                }
                            }))
                            .then(literal("off").executes(ctx -> {
                                try {
                                    ModClientConfig.INSTANCE.instance().showHud = false;
                                    ModClientConfig.INSTANCE.save();
                                    sendFeedback("HUD 已关闭");
                                    return 1;
                                } catch (Exception e) {
                                    sendError("HUD 关闭失败: " + e.getMessage());
                                    e.printStackTrace();
                                    return 0;
                                }
                            }))
                            .then(literal("reload").executes(ctx -> {
                                try {
                                    MetroData.loadAll();
                                    sendFeedback("线路数据已重新加载");
                                    return 1;
                                } catch (Exception e) {
                                    sendError("重新加载数据失败: " + e.getMessage());
                                    e.printStackTrace();
                                    return 0;
                                }
                            }))
                    )
                    // ---------- music ----------
                    .then(literal("music")
                            .then(literal("play").executes(ctx -> {
                                try {
                                    MusicManager.getInstance().play();
                                    sendFeedback("开始播放");
                                    return 1;
                                } catch (Exception e) {
                                    sendError("播放失败: " + e.getMessage());
                                    e.printStackTrace();
                                    return 0;
                                }
                            }))
                            .then(literal("pause").executes(ctx -> {
                                try {
                                    MusicManager.getInstance().pause();
                                    sendFeedback("已暂停");
                                    return 1;
                                } catch (Exception e) {
                                    sendError("暂停失败: " + e.getMessage());
                                    e.printStackTrace();
                                    return 0;
                                }
                            }))
                            .then(literal("next").executes(ctx -> {
                                try {
                                    MusicManager.getInstance().playNext();
                                    sendFeedback("下一首");
                                    return 1;
                                } catch (Exception e) {
                                    sendError("下一首失败: " + e.getMessage());
                                    e.printStackTrace();
                                    return 0;
                                }
                            }))
                            .then(literal("previous").executes(ctx -> {
                                try {
                                    MusicManager.getInstance().playPrevious();
                                    sendFeedback("上一首");
                                    return 1;
                                } catch (Exception e) {
                                    sendError("上一首失败: " + e.getMessage());
                                    e.printStackTrace();
                                    return 0;
                                }
                            }))
                            .then(literal("forward").then(argument("seconds", IntegerArgumentType.integer(1)).executes(ctx -> {
                                try {
                                    int sec = IntegerArgumentType.getInteger(ctx, "seconds");
                                    MusicManager.getInstance().seekForward(sec);
                                    sendFeedback("快进 " + sec + " 秒");
                                    return 1;
                                } catch (Exception e) {
                                    sendError("快进失败: " + e.getMessage());
                                    e.printStackTrace();
                                    return 0;
                                }
                            })))
                            .then(literal("backward").then(argument("seconds", IntegerArgumentType.integer(1)).executes(ctx -> {
                                try {
                                    int sec = IntegerArgumentType.getInteger(ctx, "seconds");
                                    MusicManager.getInstance().seekBackward(sec);
                                    sendFeedback("后退 " + sec + " 秒");
                                    return 1;
                                } catch (Exception e) {
                                    sendError("后退失败: " + e.getMessage());
                                    e.printStackTrace();
                                    return 0;
                                }
                            })))
                            .then(literal("goto").then(argument("id", StringArgumentType.word()).executes(ctx -> {
                                try {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    boolean found = MusicManager.getInstance().gotoTrack(id);
                                    if (found) {
                                        sendFeedback("跳转到歌曲 " + id);
                                    } else {
                                        sendError("ID未找到: " + id);
                                    }
                                    return 1;
                                } catch (Exception e) {
                                    sendError("跳转失败: " + e.getMessage());
                                    e.printStackTrace();
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
                                    sendFeedback("播放模式已切换为: " + mode.name());
                                    return 1;
                                } catch (IllegalArgumentException e) {
                                    sendError("无效的模式，可用: single_loop, list_loop, shuffle, order");
                                    return 0;
                                } catch (Exception e) {
                                    sendError("切换模式失败: " + e.getMessage());
                                    e.printStackTrace();
                                    return 0;
                                }
                            })))
                            .then(literal("hud")
                                    .then(literal("on").executes(ctx -> {
                                        try {
                                            ModClientConfig.INSTANCE.instance().showMusicHud = true;
                                            ModClientConfig.INSTANCE.save();
                                            sendFeedback("音乐 HUD 已开启");
                                            return 1;
                                        } catch (Exception e) {
                                            sendError("开启失败: " + e.getMessage());
                                            e.printStackTrace();
                                            return 0;
                                        }
                                    }))
                                    .then(literal("off").executes(ctx -> {
                                        try {
                                            ModClientConfig.INSTANCE.instance().showMusicHud = false;
                                            ModClientConfig.INSTANCE.save();
                                            sendFeedback("音乐 HUD 已关闭");
                                            return 1;
                                        } catch (Exception e) {
                                            sendError("关闭失败: " + e.getMessage());
                                            e.printStackTrace();
                                            return 0;
                                        }
                                    }))
                            )
                            .then(literal("reload").executes(ctx -> {
                                try {
                                    MusicManager.getInstance().loadPlaylist();
                                    sendFeedback("歌单已重新加载");
                                    return 1;
                                } catch (Exception e) {
                                    sendError("歌单加载失败: " + e.getMessage());
                                    e.printStackTrace();
                                    return 0;
                                }
                            }))
                            // 音量控制
                            .then(literal("volume").then(argument("value", IntegerArgumentType.integer(0, 100)).executes(ctx -> {
                                try {
                                    int val = IntegerArgumentType.getInteger(ctx, "value");
                                    float volume = val / 100.0f;
                                    MusicManager.getInstance().setVolume(volume);
                                    sendFeedback("音量已设置为 " + val + "%");
                                    return 1;
                                } catch (Exception e) {
                                    sendError("设置音量失败: " + e.getMessage());
                                    e.printStackTrace();
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
                                            System.out.println("[GunfuMetro] Config gui: parent screen = " + parent);
                                            Screen configScreen = createConfigScreen(parent);
                                            if (configScreen != null) {
                                                Minecraft.getInstance().setScreen(configScreen);
                                                System.out.println("[GunfuMetro] Config screen set: " + configScreen);
                                                // 延迟检查并重设
                                                new Thread(() -> {
                                                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                                                    if (Minecraft.getInstance().screen != configScreen) {
                                                        Minecraft.getInstance().execute(() -> {
                                                            Minecraft.getInstance().setScreen(configScreen);
                                                            System.out.println("[GunfuMetro] Config screen re-set");
                                                        });
                                                    }
                                                }).start();
                                                sendFeedback("配置界面已打开");
                                            } else {
                                                sendError("配置屏幕生成失败（返回 null）");
                                            }
                                        } catch (Exception e) {
                                            sendError("打开配置界面异常: " + e.getMessage());
                                            e.printStackTrace();
                                        }
                                    });
                                    return 1;
                                } catch (Exception e) {
                                    sendError("命令执行异常: " + e.getMessage());
                                    e.printStackTrace();
                                    return 0;
                                }
                            }))
                            .then(literal("reload").executes(ctx -> {
                                try {
                                    ModClientConfig.INSTANCE.load();
                                    // 应用音量
                                    MusicManager.getInstance().setVolume(ModClientConfig.INSTANCE.instance().musicVolume);
                                    sendFeedback("配置已重新加载。请重新打开配置界面查看变化。");
                                    return 1;
                                } catch (Exception e) {
                                    sendError("重新加载配置失败: " + e.getMessage());
                                    e.printStackTrace();
                                    return 0;
                                }
                            }))
                            .then(literal("reset").executes(ctx -> {
                                try {
                                    ModClientConfig cfg = ModClientConfig.INSTANCE.instance();
                                    cfg.showHud = true;
                                    cfg.showTitleBar = true;
                                    cfg.uiScale = 1.0f;
                                    cfg.musicSourceType = ModClientConfig.MusicSourceType.LOCAL;
                                    cfg.musicLocalPath = "gunfu-metro/musics.xml";
                                    cfg.playMode = ModClientConfig.PlayMode.ORDER;
                                    cfg.showMusicHud = true;
                                    cfg.musicVolume = 0.5f;
                                    ModClientConfig.INSTANCE.save();
                                    // 应用音量
                                    MusicManager.getInstance().setVolume(cfg.musicVolume);
                                    sendFeedback("配置已重置为默认值。请重新打开配置界面查看变化。");
                                    return 1;
                                } catch (Exception e) {
                                    sendError("重置配置失败: " + e.getMessage());
                                    e.printStackTrace();
                                    return 0;
                                }
                            }))
                    )
                    // ---------- map ----------
                    .then(literal("map")
                            .then(literal("regenerate").executes(ctx -> {
                                try {
                                    MetroMapTexture.regenerate();
                                    sendFeedback("地图纹理已重新生成");
                                    return 1;
                                } catch (Exception e) {
                                    sendError("重新生成纹理失败: " + e.getMessage());
                                    e.printStackTrace();
                                    return 0;
                                }
                            }))
                            .then(literal("reload").executes(ctx -> {
                                try {
                                    MetroData.loadAll();
                                    MetroMapTexture.regenerate();
                                    sendFeedback("地图数据及纹理已重新加载");
                                    return 1;
                                } catch (Exception e) {
                                    sendError("重新加载失败: " + e.getMessage());
                                    e.printStackTrace();
                                    return 0;
                                }
                            }))
                            .then(literal("gui").executes(ctx -> {
                                try {
                                    Minecraft.getInstance().execute(() -> {
                                        try {
                                            MetroMapScreen mapScreen = new MetroMapScreen();
                                            Minecraft.getInstance().setScreen(mapScreen);
                                            System.out.println("[GunfuMetro] Map screen set: " + mapScreen);
                                            new Thread(() -> {
                                                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                                                if (Minecraft.getInstance().screen != mapScreen) {
                                                    Minecraft.getInstance().execute(() -> {
                                                        Minecraft.getInstance().setScreen(mapScreen);
                                                        System.out.println("[GunfuMetro] Map screen re-set");
                                                    });
                                                }
                                            }).start();
                                            sendFeedback("地图界面已打开");
                                        } catch (Exception e) {
                                            sendError("打开地图界面异常: " + e.getMessage());
                                            e.printStackTrace();
                                        }
                                    });
                                    return 1;
                                } catch (Exception e) {
                                    sendError("命令执行异常: " + e.getMessage());
                                    e.printStackTrace();
                                    return 0;
                                }
                            }))
                    )
            );
        });
    }

    private static void sendFeedback(String msg) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal("[Gunfu Metro] " + msg), false);
        }
    }

    private static void sendError(String msg) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal("[Gunfu Metro] §c" + msg), false);
        }
    }

    public static Screen createConfigScreen(Screen parent) {
        try {
            return YetAnotherConfigLib.create(ModClientConfig.INSTANCE, (defaults, config, builder) -> builder
                    .title(Component.translatable("config.gunfu-metro.title"))
                    .category(ConfigCategory.createBuilder()
                            .name(Component.translatable("config.gunfu-metro.category.general"))
                            .option(Option.<Boolean>createBuilder()
                                    .name(Component.translatable("config.gunfu-metro.option.showHud"))
                                    .description(OptionDescription.of(Component.translatable("config.gunfu-metro.option.showHud.desc")))
                                    .binding(defaults.showHud, () -> config.showHud, val -> config.showHud = val)
                                    .controller(opt -> BooleanControllerBuilder.create(opt).coloured(true))
                                    .build())
                            .option(Option.<Boolean>createBuilder()
                                    .name(Component.translatable("config.gunfu-metro.option.showTitleBar"))
                                    .description(OptionDescription.of(Component.translatable("config.gunfu-metro.option.showTitleBar.desc")))
                                    .binding(defaults.showTitleBar, () -> config.showTitleBar, val -> config.showTitleBar = val)
                                    .controller(opt -> BooleanControllerBuilder.create(opt).coloured(true))
                                    .build())
                            .option(Option.<Boolean>createBuilder()
                                    .name(Component.translatable("config.gunfu-metro.option.showMusicHud"))
                                    .description(OptionDescription.of(Component.translatable("config.gunfu-metro.option.showMusicHud.desc")))
                                    .binding(defaults.showMusicHud, () -> config.showMusicHud, val -> config.showMusicHud = val)
                                    .controller(opt -> BooleanControllerBuilder.create(opt).coloured(true))
                                    .build())
                            .option(Option.<Float>createBuilder()
                                    .name(Component.translatable("config.gunfu-metro.option.uiScale"))
                                    .description(OptionDescription.of(Component.translatable("config.gunfu-metro.option.uiScale.desc")))
                                    .binding(defaults.uiScale, () -> config.uiScale, val -> config.uiScale = val)
                                    .controller(opt -> FloatSliderControllerBuilder.create(opt).range(0.5f, 2.0f).step(0.1f)
                                            .valueFormatter(val -> Component.literal(String.format("%.1f", val))))
                                    .build())
                            .build())
                    .category(ConfigCategory.createBuilder()
                            .name(Component.translatable("config.gunfu-metro.category.music"))
                            .option(Option.<ModClientConfig.MusicSourceType>createBuilder()
                                    .name(Component.translatable("config.gunfu-metro.option.musicSourceType"))
                                    .description(OptionDescription.of(Component.translatable("config.gunfu-metro.option.musicSourceType.desc")))
                                    .binding(defaults.musicSourceType, () -> config.musicSourceType, val -> config.musicSourceType = val)
                                    .controller(opt -> EnumControllerBuilder.create(opt)
                                            .enumClass(ModClientConfig.MusicSourceType.class)
                                            .valueFormatter(type -> Component.literal(type.name())))
                                    .build())
                            .option(Option.<String>createBuilder()
                                    .name(Component.translatable("config.gunfu-metro.option.musicRemoteUrl"))
                                    .description(OptionDescription.of(Component.translatable("config.gunfu-metro.option.musicRemoteUrl.desc")))
                                    .binding(defaults.musicRemoteUrl, () -> config.musicRemoteUrl, val -> config.musicRemoteUrl = val)
                                    .controller(StringControllerBuilder::create)
                                    .build())
                            .option(Option.<String>createBuilder()
                                    .name(Component.translatable("config.gunfu-metro.option.musicLocalPath"))
                                    .description(OptionDescription.of(Component.translatable("config.gunfu-metro.option.musicLocalPath.desc")))
                                    .binding(defaults.musicLocalPath, () -> config.musicLocalPath, val -> config.musicLocalPath = val)
                                    .controller(StringControllerBuilder::create)
                                    .build())
                            .option(Option.<ModClientConfig.PlayMode>createBuilder()
                                    .name(Component.translatable("config.gunfu-metro.option.playMode"))
                                    .description(OptionDescription.of(Component.translatable("config.gunfu-metro.option.playMode.desc")))
                                    .binding(defaults.playMode, () -> config.playMode, val -> config.playMode = val)
                                    .controller(opt -> EnumControllerBuilder.create(opt)
                                            .enumClass(ModClientConfig.PlayMode.class)
                                            .valueFormatter(mode -> switch (mode) {
                                                case SINGLE_LOOP -> Component.literal("单曲循环");
                                                case LIST_LOOP -> Component.literal("列表循环");
                                                case SHUFFLE -> Component.literal("随机播放");
                                                case ORDER -> Component.literal("顺序播放");
                                            }))
                                    .build())
                            // 音量滑块
                            .option(Option.<Float>createBuilder()
                                    .name(Component.translatable("config.gunfu-metro.option.musicVolume"))
                                    .description(OptionDescription.of(Component.translatable("config.gunfu-metro.option.musicVolume.desc")))
                                    .binding(defaults.musicVolume, () -> config.musicVolume, val -> config.musicVolume = val)
                                    .controller(opt -> FloatSliderControllerBuilder.create(opt)
                                            .range(0.0f, 1.0f).step(0.01f)
                                            .valueFormatter(val -> Component.literal((int)(val * 100) + "%")))
                                    .build())
                            .build())
                    .save(() -> ModClientConfig.INSTANCE.save())
            ).generateScreen(parent);
        } catch (Exception e) {
            e.printStackTrace();
            return new Screen(Component.literal("配置屏幕生成失败")) {
                @Override
                public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
                    renderBackground(graphics, mouseX, mouseY, delta);
                    graphics.drawCenteredString(font, Component.literal("配置屏幕生成失败，请查看日志"), width/2, height/2, 0xFF0000);
                }
            };
        }
    }
}