package io.github.haykam821.downpour.game;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;

public class Shelter {
	private static final BlockState AIR = Blocks.AIR.getDefaultState();
	private static final BlockState FLOOR = Blocks.RED_TERRACOTTA.getDefaultState();
	private static final BlockState ROOF = Blocks.SPRUCE_SLAB.getDefaultState();
	private static final BlockState GLASS = Blocks.RED_STAINED_GLASS.getDefaultState();

	private final BlockBox box;
	private final BlockBox outerBox;
	private boolean locked;

	public Shelter(BlockPos pos, int size, boolean locked) {
		int upperRadius = (int) Math.ceil(size / (float) 2);
		int lowerRadius = (int) Math.floor(size / (float) 2);
		this.box = new BlockBox(pos.getX() - lowerRadius, pos.getY(), pos.getZ() - lowerRadius, pos.getX() + upperRadius, pos.getY() + 6, pos.getZ() + upperRadius);
		this.outerBox = new BlockBox(this.box.getMinX() - 1, this.box.getMinY(), this.box.getMinZ() - 1, this.box.getMaxX() + 1, this.box.getMaxY(), this.box.getMaxZ() + 1);

		this.locked = locked;
	}

	public BlockBox getBox() {
		return this.box;
	}

	public boolean isLocked() {
		return this.locked;
	}

	public void setLocked(boolean locked) {
		this.locked = locked;
	}

	private Iterable<BlockPos> iterateOuter() {
		return BlockPos.iterate(this.outerBox.getMinX(), this.outerBox.getMinY(), this.outerBox.getMinZ(), this.outerBox.getMaxX(), this.outerBox.getMaxY(), this.outerBox.getMaxZ());
	}

	public void build(ServerWorld world) {
		for (BlockPos pos : this.iterateOuter()) {
			if (!this.box.contains(pos)) {
				if (this.locked && pos.getY() != this.box.getMaxY()) {
					world.setBlockState(pos, GLASS);
				}
			} else if (pos.getY() == this.box.getMinY()) {
				world.setBlockState(pos, FLOOR);
			} else if (pos.getY() == this.box.getMaxY()) {
				world.setBlockState(pos, ROOF);
			}
		}
	}

	public void clear(ServerWorld world) {
		for (BlockPos pos : this.iterateOuter()) {
			world.setBlockState(pos, AIR);
		}
	}
}
