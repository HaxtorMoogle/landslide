package me.desht.landslide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.desht.dhutils.LogUtils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.FallingBlock;
import org.bukkit.util.Vector;

public class SlideManager {
	private static final int RING_BUFFER_SIZE = 30;
	private static final int DROP_DELAY = 6;
	private static final int MAX_SLIDE_DELAY = 20; // must be < RING_BUFFER_SIZE - DROP_DELAY

	private final Set<String> affectedWorlds;
	private final Map<Integer,Integer> slideChances;
	private final LandslidePlugin plugin;

	private List<ScheduledBlockMove>[] slides;
	private List<Drop>[] drops;
	private int pointer;
	private int totalSlidesScheduled;
	private boolean dropItems;
	private int maxSlidesPerTick;
	private int maxSlidesTotal;
	private int cliffStability;

	@SuppressWarnings("unchecked")
	public SlideManager(LandslidePlugin plugin) {
		this.plugin = plugin;
		slides = new ArrayList[RING_BUFFER_SIZE];
		for (int i = 0; i < RING_BUFFER_SIZE; i++) {
			slides[i] = new ArrayList<ScheduledBlockMove>();
		}
		drops  = new ArrayList[RING_BUFFER_SIZE];
		for (int i = 0; i < RING_BUFFER_SIZE; i++) {
			drops[i] = new ArrayList<SlideManager.Drop>();
		}
		pointer = 0;
		totalSlidesScheduled = 0;
		dropItems = false;
		affectedWorlds = new HashSet<String>();
		slideChances = new HashMap<Integer, Integer>();
		cliffStability = 100;
	}

	public void tick() {
		if (slides[pointer].size() > 0) {
			LogUtils.fine("pointer = " + pointer + " - " + slides[pointer].size() + " blocks to slide");
		}
		for (ScheduledBlockMove slide : new ArrayList<ScheduledBlockMove>(slides[pointer])) {
			slide.initiateMove();
		}
		for (Drop drop : new ArrayList<Drop>(drops[pointer])) {
			initiateDrop(drop);
		}
		totalSlidesScheduled -= slides[pointer].size();
		slides[pointer].clear();
		drops[pointer].clear();
		pointer = (pointer + 1) % RING_BUFFER_SIZE;
	}

	public boolean scheduleBlockSlide(Block block, BlockFace direction, Material mat, byte data, boolean immediate) {
		if (totalSlidesScheduled >= getMaxSlidesTotal() || getMaxSlidesPerTick() <= 0) {
			return false;
		}

		Slide slide = new Slide(block.getLocation(), direction, mat.getId(), data, immediate);
		int delay = immediate ? 0 : plugin.getRandom().nextInt(MAX_SLIDE_DELAY);

		if (scheduleOperation(slide, delay)) {
			LogUtils.fine("scheduled slide: " + block.getLocation() + " -> " + direction + ", " + mat + "/" + data);
			return true;
		} else {
			return false;
		}
	}

	public boolean scheduleBlockSlide(Block block, BlockFace direction) {
		return scheduleBlockSlide(block, direction, block.getType(), block.getData(), false);
	}

	public boolean scheduleBlockFling(Block block, int delay, Vector vec, Vector offset) {
		delay =  plugin.getRandom().nextInt(MAX_SLIDE_DELAY);
		Fling fling = new Fling(block.getLocation().add(offset), vec, block.getTypeId(), block.getData());
		if (scheduleOperation(fling, delay)) {
			LogUtils.fine("scheduled fling: " + block.getLocation() + " -> " + vec);
			return true;
		} else {
			return false;
		}
	}

	private boolean scheduleOperation(ScheduledBlockMove operation, int delay) {
		int idx = (pointer + delay) % RING_BUFFER_SIZE;
		int n = 0;
		while (slides[idx].size() >= getMaxSlidesPerTick()) {
			idx = (idx + 1) % RING_BUFFER_SIZE;
			if (n++ >= RING_BUFFER_SIZE) {
				return false;
			}
		}
		slides[idx].add(operation);
		totalSlidesScheduled++;
		return true;
	}

	public void setMaxSlidesPerTick(int max) {
		maxSlidesPerTick = max;
	}

	public int getMaxSlidesPerTick() {
		return maxSlidesPerTick;
	}

	public void setMaxSlidesTotal(int max) {
		maxSlidesTotal = max;
	}

	public int getMaxSlidesTotal() {
		return maxSlidesTotal;
	}

	public void setDropItems(boolean dropItems) {
		this.dropItems = dropItems;
	}

	public boolean getDropItems() {
		return dropItems;
	}

	public void clearAffectedWorlds() {
		affectedWorlds.clear();
	}

	public void setAffectedWorld(String worldName) {
		affectedWorlds.add(worldName);
	}

	public boolean isWorldAffected(String worldName) {
		return affectedWorlds.contains(worldName);
	}

	public void resetSlideChances() {
		slideChances.clear();
	}

	public void setSlideChance(String matName, int chance) {
		Material mat = Material.matchMaterial(matName);
		if (mat != null) {
			slideChances.put(mat.getId(), chance);
		}
	}

	public BlockFace wouldSlide(Block block) {
		Block below = block.getRelative(BlockFace.DOWN);
		if (!below.getType().isSolid()) {
			return BlockFace.DOWN;
		}
		Block above = block.getRelative(BlockFace.UP);
		List<BlockFace>	possibles = new ArrayList<BlockFace>();
		for (BlockFace face : LandslidePlugin.horizontalFaces) {
			if (!below.getRelative(face).getType().isSolid() && !block.getRelative(face).getType().isSolid() && !above.getRelative(face).getType().isSolid()) {
				possibles.add(face);
			}
		}
		switch (possibles.size()) {
		case 0: return null;
		case 1: return possibles.get(0);
		default: return possibles.get(plugin.getRandom().nextInt(possibles.size()));
		}
	}

	public boolean isSlidy(Material mat) {
		Integer chance = slideChances.get(mat.getId());
		if (chance == null) {
			chance = 0;
		}
		return plugin.getRandom().nextInt(100) < chance;
	}

	public void setCliffStability(int stability) {
		this.cliffStability = stability;
	}

	private void initiateDrop(Drop drop) {
		Location loc = drop.fb.getLocation();
		// align the block neatly on a 0.5 boundary so it will drop cleanly onto the block below,
		// minimising the chance of it breaking and dropping an item
		loc.setX(Math.round(loc.getX() * 2.0) / 2.0);
		loc.setZ(Math.round(loc.getZ() * 2.0) / 2.0);
		drop.fb.teleport(loc);
		// halt the block's lateral velocity, making it continue straight down
		Vector vec = drop.fb.getVelocity();
		vec.setX(0.0);
		vec.setZ(0.0);
		drop.fb.setVelocity(vec);
	}

	private void scheduleDrop(FallingBlock fb) {
		int idx = (pointer + DROP_DELAY) % RING_BUFFER_SIZE;
		drops[idx].add(new Drop(fb));
	}

	private class Slide implements ScheduledBlockMove {
		private final Location loc;
		private final int blockType;
		private final byte data;
		private final BlockFace direction;
		private final boolean immediate;

		private Slide(Location loc, BlockFace direction, int blockType, byte data, boolean immediate) {
			this.direction = direction;
			this.loc = loc;
			this.blockType = blockType;
			this.data = data;
			this.immediate = immediate;
		}

		@Override
		public FallingBlock initiateMove() {
			Block b = loc.getBlock();
			if (wouldSlide(b) == null || ((b.getTypeId() != blockType || b.getData() != data) && !immediate)) {
				// sanity check; ensure the block can still slide now
				return null;
			}

			Block above = b.getRelative(BlockFace.UP);

			FallingBlock fb;
			if (above.getType().isSolid()) {
				if (plugin.getRandom().nextInt(100) < cliffStability) {
					return null;
				}
				b.setType(Material.AIR);
				// start with the block out of its hole - can't slide it sideways with a block above
				Block toSide = loc.getBlock().getRelative(direction);
				fb = loc.getWorld().spawnFallingBlock(toSide.getLocation(), blockType, data);
				float force = plugin.getRandom().nextFloat() / 2.0f;
				fb.setVelocity(new Vector(direction.getModX() * force, -0.01, direction.getModZ() * force));
			} else {
				b.setType(Material.AIR);
				fb = loc.getWorld().spawnFallingBlock(loc.add(0.0, 0.15, 0.0), blockType, data);
				double x = direction.getModX() / 4.6;
				double z = direction.getModZ() / 4.6;
				fb.setVelocity(new Vector(x, direction == BlockFace.DOWN ? 0.0 : 0.15, z));
			}
			if (fb.getVelocity().getY() > 0.0) {
				scheduleDrop(fb);
			}
			fb.setDropItem(getDropItems());
			return fb;
		}
	}

	private class Fling implements ScheduledBlockMove {
		private final Location loc;
		private final Vector vec;
		private int blockType;
		private byte data;

		private Fling(Location location, Vector vec, int blockType, byte data) {
			this.loc = location;
			this.vec = vec;
			this.blockType = blockType;
			this.data = data;
		}

		@Override
		public FallingBlock initiateMove() {
			FallingBlock fb = loc.getWorld().spawnFallingBlock(loc, blockType, data);
			fb.setVelocity(vec);
			fb.setDropItem(getDropItems());
			return fb;
		}
	}

	private class Drop {
		private final FallingBlock fb;
		private Drop (FallingBlock fb) {
			this.fb = fb;
		}
	}
}
