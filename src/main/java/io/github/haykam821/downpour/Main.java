package io.github.haykam821.downpour;

import io.github.haykam821.downpour.game.DownpourConfig;
import io.github.haykam821.downpour.game.phase.DownpourWaitingPhase;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import xyz.nucleoid.plasmid.game.GameType;
import xyz.nucleoid.plasmid.game.stats.StatisticKey;

public class Main implements ModInitializer {
	public static final String MOD_ID = "downpour";

	private static final Identifier DOWNPOUR_ID = new Identifier(MOD_ID, "downpour");
	public static final GameType<DownpourConfig> DOWNPOUR_TYPE = GameType.register(DOWNPOUR_ID, DownpourConfig.CODEC, DownpourWaitingPhase::open);

	private static final Identifier ROUNDS_SURVIVED_ID = new Identifier(MOD_ID, "rounds_survived");
	public static final StatisticKey<Integer> ROUNDS_SURVIVED = StatisticKey.intKey(ROUNDS_SURVIVED_ID);

	private static final Identifier PLAYERS_PUNCHED_ID = new Identifier(MOD_ID, "players_punched");
	public static final StatisticKey<Integer> PLAYERS_PUNCHED = StatisticKey.intKey(PLAYERS_PUNCHED_ID);

	@Override
	public void onInitialize() {
		return;
	}
}