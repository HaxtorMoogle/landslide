package me.desht.landslide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import me.desht.dhutils.DHUtilsException;
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
	private static final BlockFace[] faceChecks = { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST };
	private static final Vector dropDown = new Vector(0.0, 0.0, 0.0);

	private final Random random;
	private final Set<String> affectedWorlds;
	private final Map<Integer,Integer> slideChances;

	private List<Slide>[] slides;
	private List<Drop>[] drops;
	private int pointer;
	private int totalSlidesScheduled;
	private boolean dropItems;
	private int maxSlidesPerTick;
	private int maxSlidesTotal;
	private int cliffStability;

	@SuppressWarnings("unchecked")
	public SlideManager() {
		slides = new ArrayList[RING_BUFFER_SIZE];
		for (int i = 0; i < RING_BUFFER_SIZE; i++) {
			slides[i] = new ArrayList<SlideManager.Slide>();
		}
		drops  = new ArrayList[RING_BUFFER_SIZE];
		for (int i = 0; i < RING_BUFFER_SIZE; i++) {
			drops[i] = new ArrayList<SlideManager.Drop>();
		}
		random = new Random();
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
		for (Slide slide : new ArrayList<Slide>(slides[pointer])) {
			initiateSlide(slide);
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

		int delay = immediate ? 0 : random.nextInt(MAX_SLIDE_DELAY);

		int idx = (pointer + delay) % RING_BUFFER_SIZE;
		int n = 0;
		while (slides[idx].size() >= getMaxSlidesPerTick()) {
			idx = (idx + 1) % RING_BUFFER_SIZE;
			if (n++ >= RING_BUFFER_SIZE) {
				return false;
			}
		}
		slides[idx].add(new Slide(block.getLocation(), direction, mat.getId(), data, immediate));
		totalSlidesScheduled++;

		LogUtils.fine("schedule slide: " + block.getLocation() + " -> " + direction + ", " + mat + "/" + data);
		return true;
	}

	public boolean scheduleBlockSlide(Block block, BlockFace direction) {
		return scheduleBlockSlide(block, direction, block.getType(), block.getData(), false);
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
		for (BlockFace face : faceChecks) {
			if (!below.getRelative(face).getType().isSolid() && !block.getRelative(face).getType().isSolid() && !above.getRelative(face).getType().isSolid()) {
				possibles.add(face);
			}
		}
		switch (possibles.size()) {
		case 0: return null;
		case 1: return possibles.get(0);
		default: return possibles.get(random.nextInt(possibles.size()));
		}
	}

	public boolean isSlidy(Material mat) {
		Integer chance = slideChances.get(mat.getId());
		if (chance == null) {
			chance = 0;
		}
		return random.nextInt(100) < chance;
	}

	public void setCliffStability(int stability) {
		this.cliffStability = stability;
	}

	private FallingBlock initiateSlide(Slide slide) {
		Block b = slide.loc.getBlock();
		if (wouldSlide(b) == null || ((b.getTypeId() != slide.blockType || b.getData() != slide.data) && !slide.immediate)) {
			// sanity check; ensure the block can still slide now
			return null;
		}

		Block above = b.getRelative(BlockFace.UP);

		FallingBlock fb;
		if (above.getType().isSolid()) {
			if (random.nextInt(100) < cliffStability) {
				return null;
			}
			b.setType(Material.AIR);
			// start with the block out of its hole - can't slide it sideways with a block above
			Block toSide = slide.loc.getBlock().getRelative(slide.direction);
			fb = slide.loc.getWorld().spawnFallingBlock(toSide.getLocation(), slide.blockType, slide.data);
			float force = random.nextFloat() / 2.0f;
			fb.setVelocity(new Vector(slide.direction.getModX() * force, -0.01, slide.direction.getModZ() * force));
		} else {
			b.setType(Material.AIR);
			fb = slide.loc.getWorld().spawnFallingBlock(slide.loc.add(0.0, 0.15, 0.0), slide.blockType, slide.data);
			double x = slide.direction.getModX() / 4.6;
			double z = slide.direction.getModZ() / 4.6;
			fb.setVelocity(new Vector(x, slide.direction == BlockFace.DOWN ? 0.0 : 0.15, z));
		}
		if (fb.getVelocity().getY() > 0.0) {
			scheduleDrop(fb);
		}
		fb.setDropItem(getDropItems());
		return fb;
	}

	private void initiateDrop(Drop drop) {
		Location loc = drop.fb.getLocation();
		// align the block neatly on a 0.5 boundary so it will drop cleanly onto the block below,
		// minimising the chance of it breaking and dropping an item
		loc.setX(Math.round(loc.getX() * 2.0) / 2.0);
		loc.setZ(Math.round(loc.getZ() * 2.0) / 2.0);
		drop.fb.teleport(loc);
		drop.fb.setVelocity(dropDown);
	}

	private void scheduleDrop(FallingBlock fb) {
		int idx = (pointer + DROP_DELAY) % RING_BUFFER_SIZE;
		drops[idx].add(new Drop(fb));
	}

	private class Slide {
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
	}

	private class Drop {
		private final FallingBlock fb;
		private Drop (FallingBlock fb) {
			this.fb = fb;
		}
	}
}
