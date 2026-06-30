package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;

public class DynamicPortraitsModPlugin extends BaseModPlugin {
    @Override
    public void onGameLoad(boolean newGame) {
        DynamicPortraitsManager.install();
    }

    @Override
    public void onNewGameAfterEconomyLoad() {
        DynamicPortraitsManager.install();
    }
}
