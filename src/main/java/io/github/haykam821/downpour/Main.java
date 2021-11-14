package io.github.haykam821.downpour;

import io.github.haykam821.downpour.game.DownpourConfig;
import io.github.haykam821.downpour.game.phase.DownpourWaitingPhase;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import xyz.nucleoid.plasmid.game.GameType;

public class Main implements ModInitializer {
	private static final String MOD_ID = "downpour";

	private static final Identifier DOWNPOUR_ID = new Identifier(MOD_ID, "downpour");
	public static final GameType<DownpourConfig> DOWNPOUR_TYPE = GameType.register(DOWNPOUR_ID, DownpourConfig.CODEC, DownpourWaitingPhase::open);

	@Override
	public void onInitialize() {
		return;
	}
}