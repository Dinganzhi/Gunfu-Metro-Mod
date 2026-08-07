package io.github.dinganzhi.gunfumetro.config;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 配置界面构建入口（基于 YACL，是模组的硬依赖）。
 *
 * <p>单独放在一个不依赖 ModMenu 的类里：命令与地图界面的「配置」按钮都直接调用它，
 * 这样即使没有安装 ModMenu（非必须前置）也能正常打开配置界面。
 * ModMenu 的入口类 {@link io.github.dinganzhi.gunfumetro.compat.ModMenuCompat}
 * 只做一层委托，避免因 ModMenu 缺失而在加载配置类时抛 NoClassDefFoundError。</p>
 */
public final class GunfuMetroConfigScreen {
    private GunfuMetroConfigScreen() {
    }

    /** 唯一的配置界面构建入口，命令与 ModMenu 共用，避免两份代码漂移 */
    public static Screen create(Screen parent) {
        return YetAnotherConfigLib.create(ModClientConfig.INSTANCE, (defaults, config, builder) -> builder
                .title(Component.translatable("config.gunfu-metro.title"))
                .category(ConfigCategory.createBuilder()
                        .name(Component.translatable("config.gunfu-metro.category.general"))
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("config.gunfu-metro.option.showHud"))
                                .description(OptionDescription
                                        .of(Component.translatable("config.gunfu-metro.option.showHud.desc")))
                                .binding(defaults.showHud, () -> config.showHud, val -> config.showHud = val)
                                .controller(opt -> BooleanControllerBuilder.create(opt).coloured(true))
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("config.gunfu-metro.option.showTitleBar"))
                                .description(OptionDescription
                                        .of(Component.translatable("config.gunfu-metro.option.showTitleBar.desc")))
                                .binding(defaults.showTitleBar, () -> config.showTitleBar,
                                        val -> config.showTitleBar = val)
                                .controller(opt -> BooleanControllerBuilder.create(opt).coloured(true))
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("config.gunfu-metro.option.showMusicHud"))
                                .description(OptionDescription
                                        .of(Component.translatable("config.gunfu-metro.option.showMusicHud.desc")))
                                .binding(defaults.showMusicHud, () -> config.showMusicHud,
                                        val -> config.showMusicHud = val)
                                .controller(opt -> BooleanControllerBuilder.create(opt).coloured(true))
                                .build())
                        .option(Option.<Float>createBuilder()
                                .name(Component.translatable("config.gunfu-metro.option.uiScale"))
                                .description(OptionDescription
                                        .of(Component.translatable("config.gunfu-metro.option.uiScale.desc")))
                                .binding(defaults.uiScale, () -> config.uiScale, val -> config.uiScale = val)
                                .controller(opt -> FloatSliderControllerBuilder.create(opt).range(0.5f, 2.0f).step(0.1f)
                                        .valueFormatter(val -> Component.literal(String.format("%.1f", val))))
                                .build())
                        .build())
                .category(ConfigCategory.createBuilder()
                        .name(Component.translatable("config.gunfu-metro.category.music"))
                        .option(Option.<ModClientConfig.MusicSourceType>createBuilder()
                                .name(Component.translatable("config.gunfu-metro.option.musicSourceType"))
                                .description(OptionDescription
                                        .of(Component.translatable("config.gunfu-metro.option.musicSourceType.desc")))
                                .binding(defaults.musicSourceType, () -> config.musicSourceType,
                                        val -> config.musicSourceType = val)
                                .controller(opt -> EnumControllerBuilder.create(opt)
                                        .enumClass(ModClientConfig.MusicSourceType.class)
                                        .valueFormatter(type -> Component.translatable(
                                                "config.gunfu-metro.option.musicSourceType." + type.name().toLowerCase())))
                                .build())
                        .option(Option.<String>createBuilder()
                                .name(Component.translatable("config.gunfu-metro.option.musicRemoteUrl"))
                                .description(OptionDescription
                                        .of(Component.translatable("config.gunfu-metro.option.musicRemoteUrl.desc")))
                                .binding(defaults.musicRemoteUrl, () -> config.musicRemoteUrl,
                                        val -> config.musicRemoteUrl = val)
                                .controller(StringControllerBuilder::create)
                                .build())
                        .option(Option.<String>createBuilder()
                                .name(Component.translatable("config.gunfu-metro.option.musicLocalPath"))
                                .description(OptionDescription
                                        .of(Component.translatable("config.gunfu-metro.option.musicLocalPath.desc")))
                                .binding(defaults.musicLocalPath, () -> config.musicLocalPath,
                                        val -> config.musicLocalPath = val)
                                .controller(StringControllerBuilder::create)
                                .build())
                        .option(Option.<ModClientConfig.PlayMode>createBuilder()
                                .name(Component.translatable("config.gunfu-metro.option.playMode"))
                                .description(OptionDescription
                                        .of(Component.translatable("config.gunfu-metro.option.playMode.desc")))
                                .binding(defaults.playMode, () -> config.playMode, val -> config.playMode = val)
                                .controller(opt -> EnumControllerBuilder.create(opt)
                                        .enumClass(ModClientConfig.PlayMode.class)
                                        .valueFormatter(mode -> Component.translatable(
                                                "config.gunfu-metro.option.playMode." + mode.name().toLowerCase())))
                                .build())
                        .option(Option.<Float>createBuilder()
                                .name(Component.translatable("config.gunfu-metro.option.musicVolume"))
                                .description(OptionDescription
                                        .of(Component.translatable("config.gunfu-metro.option.musicVolume.desc")))
                                .binding(defaults.musicVolume, () -> config.musicVolume, val -> config.musicVolume = val)
                                .controller(opt -> FloatSliderControllerBuilder.create(opt)
                                        .range(0.0f, 1.0f).step(0.01f)
                                        .valueFormatter(val -> Component.literal((int) (val * 100) + "%")))
                                .build())
                        .build())
                .save(() -> ModClientConfig.INSTANCE.save())).generateScreen(parent);
    }
}