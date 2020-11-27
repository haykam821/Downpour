package io.github.haykam821.downpour.game.phase;

import io.github.haykam821.downpour.game.DownpourConfig;
import io.github.haykam821.downpour.game.map.DownpourMap;
import io.github.haykam821.downpour.game.map.DownpourMapBuilder;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.world.GameMode;
import xyz.nucleoid.fantasy.BubbleWorldConfig;
import xyz.nucleoid.plasmid.game.GameOpenContext;
import xyz.nucleoid.plasmid.game.GameOpenProcedure;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.GameWaitingLobby;
import xyz.nucleoid.plasmid.game.StartResult;
import xyz.nucleoid.plasmid.game.config.PlayerConfig;
import xyz.nucleoid.plasmid.game.event.OfferPlayerListener;
import xyz.nucleoid.plasmid.game.event.PlayerAddListener;
import xyz.nucleoid.plasmid.game.event.PlayerDeathListener;
import xyz.nucleoid.plasmid.game.event.RequestStartListener;
import xyz.nucleoid.plasmid.game.player.JoinResult;
import xyz.nucleoid.plasmid.game.rule.GameRule;
import xyz.nucleoid.plasmid.game.rule.RuleResult;

public class DownpourWaitingPhase {
	private final GameSpace gameSpace;
	private final DownpourMap map;
	private final DownpourConfig config;

	public DownpourWaitingPhase(GameSpace gameSpace, DownpourMap map, DownpourConfig config) {
		this.gameSpace = gameSpace;
		this.map = map;
		this.config = config;
	}

	public static GameOpenProcedure open(GameOpenContext<DownpourConfig> context) {
		DownpourMapBuilder mapBuilder = new DownpourMapBuilder(context.getConfig());
		DownpourMap map = mapBuilder.create();

		BubbleWorldConfig worldConfig = new BubbleWorldConfig()
			.setGenerator(map.createGenerator(context.getServer()))
			.setRaining(true)
			.setDefaultGameMode(GameMode.ADVENTURE);

		return context.createOpenProcedure(worldConfig, game -> {
			DownpourWaitingPhase phase = new DownpourWaitingPhase(game.getSpace(), map, context.getConfig());
			GameWaitingLobby.applyTo(game, context.getConfig().getPlayerConfig());

			DownpourActivePhase.setRules(game);
			game.setRule(GameRule.PVP, RuleResult.DENY);

			// Listeners
			game.on(PlayerAddListener.EVENT, phase::addPlayer);
			game.on(PlayerDeathListener.EVENT, phase::onPlayerDeath);
			game.on(OfferPlayerListener.EVENT, phase::offerPlayer);
			game.on(RequestStartListener.EVENT, phase::requestStart);
		});
	}

	private boolean isFull() {
		return this.gameSpace.getPlayerCount() >= this.config.getPlayerConfig().getMaxPlayers();
	}

	private JoinResult offerPlayer(ServerPlayerEntity player) {
		return this.isFull() ? JoinResult.gameFull() : JoinResult.ok();
	}

	private StartResult requestStart() {
		PlayerConfig playerConfig = this.config.getPlayerConfig();
		if (this.gameSpace.getPlayerCount() < playerConfig.getMinPlayers()) {
			return StartResult.NOT_ENOUGH_PLAYERS;
		}

		DownpourActivePhase.open(this.gameSpace, this.map, this.config);
		return StartResult.OK;
	}

	private void addPlayer(ServerPlayerEntity player) {
		DownpourActivePhase.spawn(this.gameSpace.getWorld(), this.map, player);
	}

	private ActionResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
		DownpourActivePhase.spawn(this.gameSpace.getWorld(), this.map, player);
		return ActionResult.FAIL;
	}
}