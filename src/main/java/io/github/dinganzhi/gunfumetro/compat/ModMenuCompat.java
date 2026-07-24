package io.github.dinganzhi.gunfumetro.compat;

import io.github.dinganzhi.gunfumetro.config.ModClientConfig;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ModMenuCompat implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> createConfigScreen(parent);
    }

    public static Screen createConfigScreen(Screen parent) {
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
                                        .valueFormatter(type -> Component.literal(type.name())))
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
                                        .valueFormatter(mode -> Component.literal(mode.name())))
                                .build())
                        .build())
                .save(() -> ModClientConfig.INSTANCE.save())).generateScreen(parent);
    }
}