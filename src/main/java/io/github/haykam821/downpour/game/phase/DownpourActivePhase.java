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
import xyz.nucleoid.plasmid.game.GameActivity;
import xyz.nucleoid.plasmid.game.GameCloseReason;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.common.GlobalWidgets;
import xyz.nucleoid.plasmid.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.game.player.PlayerOffer;
import xyz.nucleoid.plasmid.game.player.PlayerOfferResult;
import xyz.nucleoid.plasmid.game.rule.GameRuleType;
import xyz.nucleoid.plasmid.util.PlayerRef;
import xyz.nucleoid.stimuli.event.player.PlayerDamageEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

public class DownpourActivePhase {
	private final ServerWorld world;
	private final GameSpace gameSpace;
	private final DownpourMap map;
	private final DownpourConfig config;
	private final Set<PlayerRef> players;
	private final DownpourTimerBar timerBar;
	private boolean singleplayer;
	private int rounds = 0;
	private int ticksUntilSwitch;
	private Shelter shelter;

	public DownpourActivePhase(GameSpace gameSpace, ServerWorld world, DownpourMap map, DownpourConfig config, Set<PlayerRef> players, GlobalWidgets widgets) {
		this.world = world;
		this.gameSpace = gameSpace;
		this.map = map;
		this.config = config;
		this.players = players;
		this.timerBar = new DownpourTimerBar(widgets);
		this.ticksUntilSwitch = this.config.getLockTime();
		this.createShelter();
	}

	public static void setRules(GameActivity activity) {
		activity.deny(GameRuleType.BLOCK_DROPS);
		activity.deny(GameRuleType.CRAFTING);
		activity.deny(GameRuleType.FALL_DAMAGE);
		activity.deny(GameRuleType.HUNGER);
		activity.deny(GameRuleType.INTERACTION);
		activity.deny(GameRuleType.PORTALS);
		activity.allow(GameRuleType.PVP);
	}

	public static void open(GameSpace gameSpace, ServerWorld world, DownpourMap map, DownpourConfig config) {
		gameSpace.setActivity(activity -> {
			GlobalWidgets widgets = GlobalWidgets.addTo(activity);
			Set<PlayerRef> players = gameSpace.getPlayers().stream().map(PlayerRef::of).collect(Collectors.toSet());
			DownpourActivePhase phase = new DownpourActivePhase(gameSpace, world, map, config, players, widgets);

			DownpourActivePhase.setRules(activity);

			// Listeners
			activity.listen(GameActivityEvents.ENABLE, phase::enable);
			activity.listen(GameActivityEvents.TICK, phase::tick);
			activity.listen(GamePlayerEvents.OFFER, phase::offerPlayer);
			activity.listen(GamePlayerEvents.REMOVE, phase::removePlayer);
			activity.listen(PlayerDamageEvent.EVENT, phase::onPlayerDamage);
			activity.listen(PlayerDeathEvent.EVENT, phase::onPlayerDeath);
		});
	}

	private void enable() {
		this.singleplayer = this.players.size() == 1;

 		for (PlayerRef playerRef : this.players) {
			playerRef.ifOnline(this.world, player -> {
				this.updateRoundsExperienceLevel(player);
				player.changeGameMode(GameMode.ADVENTURE);
				DownpourActivePhase.spawn(this.world, this.map, player);
			});
		}
	}

	private void createShelter() {
		BlockPos minPos = this.map.getShelterBounds().min();
		BlockPos maxPos = this.map.getShelterBounds().max();

		int x = this.world.getRandom().nextInt(maxPos.getX() + 1 - minPos.getX()) + minPos.getX();
		int z = this.world.getRandom().nextInt(maxPos.getZ() + 1 - minPos.getZ()) + minPos.getZ();
		int size = Math.max(0, Math.min(4, 4 - this.rounds / 2));

		this.shelter = new Shelter(new BlockPos(x, this.map.getShelterBounds().min().getY(), z), size, false);
		this.shelter.build(this.world);
	}

	private Text getKnockbackEnabledText() {
		return new TranslatableText("text.downpour.knockback_enabled").formatted(Formatting.RED);
	}

	private void updateRoundsExperienceLevel(ServerPlayerEntity player) {
		player.setExperienceLevel(this.rounds + 1);
	}

	private void setRounds(int rounds) {
		this.rounds = rounds;
		for (ServerPlayerEntity player : this.gameSpace.getPlayers()) {
			this.updateRoundsExperienceLevel(player);
		}
	}

	private void tick() {
		this.ticksUntilSwitch -= 1;
		this.timerBar.tick(this);
		if (this.ticksUntilSwitch < 0) {
			if (this.shelter.isLocked()) {
				// Unlock
				this.shelter.clear(this.world);
				this.createShelter();

				this.setRounds(this.rounds + 1);
				if (this.rounds == this.config.getNoKnockbackRounds()) {
					this.gameSpace.getPlayers().sendMessage(this.getKnockbackEnabledText());
				}
				
				this.gameSpace.getPlayers().playSound(this.config.getUnlockSound());
				this.ticksUntilSwitch = this.config.getLockTime();
			} else {
				// Lock
				this.shelter.setLocked(true);
				this.shelter.build(this.world);

				this.gameSpace.getPlayers().playSound(this.config.getLockSound());
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

			this.gameSpace.close(GameCloseReason.FINISHED);
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

	private void setSpectator(ServerPlayerEntity player) {
		player.changeGameMode(GameMode.SPECTATOR);
	}

	private PlayerOfferResult offerPlayer(PlayerOffer offer) {
		return offer.accept(this.world, this.map.getBounds().center()).and(() -> {
			this.updateRoundsExperienceLevel(offer.player());
			this.setSpectator(offer.player());
		});
	}

	private void removePlayer(ServerPlayerEntity player) {
		this.eliminate(player, true);
	}

	private boolean eliminate(ServerPlayerEntity eliminatedPlayer, String suffix, boolean remove) {
		PlayerRef eliminatedRef = PlayerRef.of(eliminatedPlayer);
		if (!this.players.contains(eliminatedRef)) {
			return false;
		}

		Text message = new TranslatableText("text.downpour.eliminated" + suffix, eliminatedPlayer.getDisplayName()).formatted(Formatting.RED);
		for (ServerPlayerEntity player : this.gameSpace.getPlayers()) {
			player.sendMessage(message, false);
		}

		if (remove) {
			this.players.remove(eliminatedRef);
		}
		this.setSpectator(eliminatedPlayer);

		return true;
	}

	private boolean eliminate(ServerPlayerEntity eliminatedPlayer, boolean remove) {
		return this.eliminate(eliminatedPlayer, "", remove);
	}

	private ActionResult onPlayerDamage(ServerPlayerEntity player, DamageSource source, float amount) {
		return this.rounds >= this.config.getNoKnockbackRounds() ? ActionResult.SUCCESS : ActionResult.FAIL;
	}

	private ActionResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
		if (!this.eliminate(player, true)) {
			DownpourActivePhase.spawn(this.world, this.map, player);
		}
		return ActionResult.FAIL;
	}

	public static void spawn(ServerWorld world, DownpourMap map, ServerPlayerEntity player) {
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, Integer.MAX_VALUE, 127, true, false));

		Vec3d pos = DownpourActivePhase.getSpawnPos(map);
		player.teleport(world, pos.getX(), pos.getY(), pos.getZ(), 0, 0);
	}

	public static Vec3d getSpawnPos(DownpourMap map) {
		Vec3d center = map.getBounds().center();
		return new Vec3d(center.getX(), map.getShelterBounds().min().getY(), center.getZ());
	}

	public float getTimerBarPercent() {
		if (this.shelter == null) return 0;
		return this.ticksUntilSwitch / (float) (this.shelter.isLocked() ? this.config.getUnlockTime() : this.config.getLockTime());
	}
}