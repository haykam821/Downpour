package io.github.haykam821.downpour.game.phase;

import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.haykam821.downpour.game.DownpourConfig;
import io.github.haykam821.downpour.game.DownpourTimerBar;
import io.github.haykam821.downpour.game.Shelter;
import io.github.haykam821.downpour.game.map.DownpourMap;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import xyz.nucleoid.plasmid.game.GameLogic;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.event.GameCloseListener;
import xyz.nucleoid.plasmid.game.event.GameOpenListener;
import xyz.nucleoid.plasmid.game.event.GameTickListener;
import xyz.nucleoid.plasmid.game.event.PlayerAddListener;
import xyz.nucleoid.plasmid.game.event.PlayerDamageListener;
import xyz.nucleoid.plasmid.game.event.PlayerDeathListener;
import xyz.nucleoid.plasmid.game.rule.GameRule;
import xyz.nucleoid.plasmid.game.rule.RuleResult;
import xyz.nucleoid.plasmid.util.PlayerRef;
import xyz.nucleoid.plasmid.widget.GlobalWidgets;

public class DownpourActivePhase {
	private final ServerWorld world;
	private final GameSpace gameSpace;
	private final DownpourMap map;
	private final DownpourConfig config;
	private final Set<PlayerRef> players;
	private final DownpourTimerBar timerBar;
	private boolean singleplayer;
	private boolean opened;
	private int rounds = 0;
	private int ticksUntilSwitch;
	private Shelter shelter;

	public DownpourActivePhase(GameSpace gameSpace, DownpourMap map, DownpourConfig config, Set<PlayerRef> players, GlobalWidgets widgets) {
		this.world = gameSpace.getWorld();
		this.gameSpace = gameSpace;
		this.map = map;
		this.config = config;
		this.players = players;
		this.timerBar = new DownpourTimerBar(widgets);
		this.ticksUntilSwitch = this.config.getLockTime();
		this.createShelter();
	}

	public static void setRules(GameLogic game) {
		game.setRule(GameRule.BLOCK_DROPS, RuleResult.DENY);
		game.setRule(GameRule.CRAFTING, RuleResult.DENY);
		game.setRule(GameRule.FALL_DAMAGE, RuleResult.DENY);
		game.setRule(GameRule.HUNGER, RuleResult.DENY);
		game.setRule(GameRule.INTERACTION, RuleResult.DENY);
		game.setRule(GameRule.PORTALS, RuleResult.DENY);
		game.setRule(GameRule.PVP, RuleResult.ALLOW);
	}

	public static void open(GameSpace gameSpace, DownpourMap map, DownpourConfig config) {
		gameSpace.openGame(game -> {
			GlobalWidgets widgets = new GlobalWidgets(game);
			Set<PlayerRef> players = gameSpace.getPlayers().stream().map(PlayerRef::of).collect(Collectors.toSet());
			DownpourActivePhase phase = new DownpourActivePhase(gameSpace, map, config, players, widgets);

			DownpourActivePhase.setRules(game);

			// Listeners
			game.on(GameCloseListener.EVENT, phase::close);
			game.on(GameOpenListener.EVENT, phase::open);
			game.on(GameTickListener.EVENT, phase::tick);
			game.on(PlayerAddListener.EVENT, phase::addPlayer);
			game.on(PlayerDamageListener.EVENT, phase::onPlayerDamage);
			game.on(PlayerDeathListener.EVENT, phase::onPlayerDeath);
		});
	}

	private void close() {
		this.timerBar.remove();
	}

	private void open() {
		this.opened = true;
		this.singleplayer = this.players.size() == 1;

 		for (PlayerRef playerRef : this.players) {
			playerRef.ifOnline(this.world, player -> {
				player.setGameMode(GameMode.ADVENTURE);
				DownpourActivePhase.spawn(this.world, this.map, player);
			});
		}
	}

	private void createShelter() {
		BlockPos minPos = this.map.getShelterBounds().getMin();
		BlockPos maxPos = this.map.getShelterBounds().getMax();

		int x = this.world.getRandom().nextInt(maxPos.getX() + 1 - minPos.getX()) + minPos.getX();
		int z = this.world.getRandom().nextInt(maxPos.getZ() + 1 - minPos.getZ()) + minPos.getZ();
		int size = Math.max(0, Math.min(4, 4 - this.rounds / 2));

		this.shelter = new Shelter(new BlockPos(x, this.map.getShelterBounds().getMin().getY(), z), size, false);
		this.shelter.build(this.world);
	}

	private Text getKnockbackEnabledText() {
		return new TranslatableText("text.downpour.knockback_enabled").formatted(Formatting.RED);
	}

	private void tick() {
		this.ticksUntilSwitch -= 1;
		this.timerBar.tick(this);
		if (this.ticksUntilSwitch < 0) {
			if (this.shelter.isLocked()) {
				// Unlock
				this.shelter.clear(this.world);
				this.createShelter();

				this.rounds += 1;
				if (this.rounds == this.config.getNoKnockbackRounds()) {
					this.gameSpace.getPlayers().sendMessage(this.getKnockbackEnabledText());
				}
				
				this.gameSpace.getPlayers().sendSound(this.config.getUnlockSound());
				this.ticksUntilSwitch = this.config.getLockTime();
			} else {
				// Lock
				this.shelter.setLocked(true);
				this.shelter.build(this.world);

				this.gameSpace.getPlayers().sendSound(this.config.getLockSound());
				this.ticksUntilSwitch = this.config.getUnlockTime();
			}
		}

		// Eliminate players that are out of bounds
		Iterator<PlayerRef> playerIterator = this.players.iterator();
		while (playerIterator.hasNext()) {
			PlayerRef playerRef = playerIterator.next();
			playerRef.ifOnline(this.world, player -> {
				if (!this.map.getBounds().contains(player.getBlockPos())) {
					this.eliminate(player, ".out_of_bounds", false);
					playerIterator.remove();
				} else if (this.shelter != null && this.shelter.isLocked() && !this.shelter.getBox().contains(player.getBlockPos())) {
					this.eliminate(player, ".out_of_shelter", false);
					playerIterator.remove();
				}
			});
		}

		// Determine a winner
		if (this.players.size() < 2) {
			if (this.players.size() == 1 && this.singleplayer) return;
			
			Text endingMessage = this.getEndingMessage();
			for (ServerPlayerEntity player : this.gameSpace.getPlayers()) {
				player.sendMessage(endingMessage, false);
			}

			this.gameSpace.close();
		}
	}

	private Text getEndingMessage() {
		if (this.players.size() == 1) {
			PlayerRef winnerRef = this.players.iterator().next();
			if (winnerRef.isOnline(this.world)) {
				PlayerEntity winner = winnerRef.getEntity(this.world);
				return new TranslatableText("text.downpour.win", winner.getDisplayName(), this.rounds).formatted(Formatting.GOLD);
			}
		}
		return new TranslatableText("text.downpour.no_winners", this.rounds).formatted(Formatting.GOLD);
	}

	private void setSpectator(PlayerEntity player) {
		player.setGameMode(GameMode.SPECTATOR);
	}

	private void addPlayer(PlayerEntity player) {
		if (!this.players.contains(PlayerRef.of(player))) {
			this.setSpectator(player);
		} else if (this.opened) {
			this.eliminate(player, true);
		}
	}

	private void eliminate(PlayerEntity eliminatedPlayer, String suffix, boolean remove) {
		Text message = new TranslatableText("text.downpour.eliminated" + suffix, eliminatedPlayer.getDisplayName()).formatted(Formatting.RED);
		for (ServerPlayerEntity player : this.gameSpace.getPlayers()) {
			player.sendMessage(message, false);
		}

		if (remove) {
			this.players.remove(PlayerRef.of(eliminatedPlayer));
		}
		this.setSpectator(eliminatedPlayer);
	}

	private void eliminate(PlayerEntity eliminatedPlayer, boolean remove) {
		this.eliminate(eliminatedPlayer, "", remove);
	}

	private ActionResult onPlayerDamage(ServerPlayerEntity player, DamageSource source, float amount) {
		return this.rounds >= this.config.getNoKnockbackRounds() ? ActionResult.SUCCESS : ActionResult.FAIL;
	}

	private ActionResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
		if (this.players.contains(PlayerRef.of(player))) {
			this.eliminate(player, true);
		} else {
			DownpourActivePhase.spawn(this.world, this.map, player);
		}
		return ActionResult.FAIL;
	}

	public static void spawn(ServerWorld world, DownpourMap map, ServerPlayerEntity player) {
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, Integer.MAX_VALUE, 127, true, false));

		Vec3d center = map.getBounds().getCenter();
		player.teleport(world, center.getX(), map.getShelterBounds().getMin().getY(), center.getZ(), 0, 0);
	}

	public float getTimerBarPercent() {
		if (this.shelter == null) return 0;
		return this.ticksUntilSwitch / (float) (this.shelter.isLocked() ? this.config.getUnlockTime() : this.config.getLockTime());
	}
}