package io.github.dinganzhi.gunfumetro.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.dinganzhi.gunfumetro.config.GunfuMetroConfigScreen;

/**
 * ModMenu 兼容入口。仅在安装了 ModMenu 时才会被加载（走 fabric.mod.json 的 modmenu entrypoint）。
 *
 * <p>配置界面真正的构建逻辑在 {@link GunfuMetroConfigScreen}（不依赖 ModMenu），
 * 命令与地图界面的「配置」按钮都直接调用它，因此不装 ModMenu 也能正常打开配置界面。</p>
 */
public class ModMenuCompat implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> GunfuMetroConfigScreen.create(parent);
    }
}