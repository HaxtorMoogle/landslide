package me.desht.landslide;

/*
This file is part of Landslide

Landslide is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Landslide is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Landslide.  If not, see <http://www.gnu.org/licenses/>.
 */

import me.desht.dhutils.LogUtils;
import me.desht.dhutils.MiscUtil;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Note that all listener methods run with EventPriority.HIGHEST, giving other plugins a chance
 * to cancel the various events first.
 */
public class EventListener implements Listener {

	private LandslidePlugin plugin;

	public EventListener(LandslidePlugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockPlace(BlockPlaceEvent event) {
		if (!plugin.getPerWorldConfig().isEnabled(event.getBlock().getWorld())) {
			return;
		}
		checkForSlide(event.getBlock());
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockLanded(EntityChangeBlockEvent event) {
		if (!(event.getEntity() instanceof FallingBlock)) {
			return;
		}
		if (!plugin.getPerWorldConfig().isEnabled(event.getBlock().getWorld())) {
			return;
		}

		Block block = event.getBlock();
		FallingBlock fb = (FallingBlock) event.getEntity();

		if (block.getType() == Material.AIR && block.getRelative(BlockFace.DOWN).getType() == Material.SNOW) {
			// trying to land on a thick snow layer - doesn't work well, so just drop an item
			event.setCancelled(true);
			if (plugin.getPerWorldConfig().getDropItems(block.getWorld())) {
				block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(fb.getMaterial(), 1, fb.getBlockData()));
			}
			return;
		}
		if (block.getType() == Material.SNOW && (fb.getMaterial() == Material.SNOW || fb.getMaterial() == Material.SNOW_BLOCK)) {
			handleSnowAccumulation(block, fb);
		}

		LogUtils.fine("falling block landed! " + fb.getMaterial() + " -> " + block);
		if (checkForSlide(block, event.getTo(), event.getData(), true, plugin.getPerWorldConfig().getFallingBlocksBounce(fb.getWorld()))) {
			// the block continues to slide - don't waste time forming a true block
			// (checkForSlide() will create a new FallingBlock entity)
			event.setCancelled(true);
		} else {
			// the block has landed
			if (plugin.getRandom().nextInt(100) < plugin.getPerWorldConfig().getExplodeEffectChance(fb.getWorld())) {
				if (fb.getMaterial().isSolid()) {
					fb.getWorld().createExplosion(fb.getLocation(), 0.0f);
				} else if (fb.getMaterial() == Material.SNOW) {
					fb.getWorld().playSound(fb.getLocation(), Sound.STEP_SNOW, 1.0f, 0.5f);
				}
			}
		}

		// See if the block we landed on can be dislodged; but only "heavy" (aka solid) falling blocks will dislodge blocks they land on
		if (fb.getMaterial().isSolid()) {
			checkForSlide(block.getRelative(BlockFace.DOWN));
		}

		// anything living standing in the way?
		int dmg = plugin.getPerWorldConfig().getFallingBlockDamage(fb.getWorld());
		if (dmg > 0) {
			Location loc = block.getLocation();
			for (Entity e : loc.getChunk().getEntities()) {
				if (e instanceof LivingEntity) {
					e.getLocation(loc);
					if (loc.getBlockX() == block.getX() && loc.getBlockY() == block.getY() && loc.getBlockZ() == block.getZ()) {
						((LivingEntity) e).damage(dmg);
					}
				}
			}
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onSnowItemSpawn(ItemSpawnEvent event) {
		Item item = event.getEntity();
		if (item.getItemStack().getType() == Material.SNOW || item.getItemStack().getType() == Material.SNOW_BLOCK) {
			short thickness = item.getItemStack().getType() == Material.SNOW ? item.getItemStack().getDurability() : 7;
			Block b = item.getLocation().getBlock();
			if (b.getType() == Material.AIR) {
				// item could spawn in the block above a thick (thickness >= 6) snow layer
				b = b.getRelative(BlockFace.DOWN);
			}
			if (b.getType() == Material.SNOW) {
				byte newThickness = (byte) (b.getData() + thickness + 1);
				if (newThickness > 7) {
					b.setTypeIdAndData(Material.SNOW_BLOCK.getId(), (byte) 0, true);
					b.getRelative(BlockFace.UP).setTypeIdAndData(Material.SNOW.getId(), (byte)(newThickness - 8), true);
				} else if (newThickness == 7) {
					b.setTypeIdAndData(Material.SNOW_BLOCK.getId(), (byte) 0, true);
				} else {
					b.setData(newThickness);
				}
			}
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockPhysics(BlockPhysicsEvent event) {
		if (!plugin.getPerWorldConfig().isEnabled(event.getBlock().getWorld())) {
			return;
		}
		checkForSlide(event.getBlock());
		checkForSlide(event.getBlock().getRelative(BlockFace.UP));
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onEntityExplode(EntityExplodeEvent event) {
		if (!explosionEventApplies(event.getEntity())) {
			return;
		}

		// we don't drop items; instead any affected blocks have a chance to become a (high-speed) falling block
		event.setCancelled(true);

		Location centre = event.getLocation();

		double distMax0 = 0.0;
		for (Block b : event.blockList()) {
			distMax0 = Math.max(distMax0, b.getLocation().distanceSquared(centre));
		}
		if (distMax0 == 0.0) {
			return;
		}

		// work out a good direction to bias the block flinging - try to send them towards open air
		Block centreBlock = centre.getBlock();
		if (!centreBlock.getType().isSolid()) {
			centreBlock = findAdjacentSolid(centreBlock);
			centre = centreBlock.getLocation();
		}
		Vector dirModifier = new Vector(0.0, 0.0, 0.0);
		int n = 0;
		for (BlockFace face : LandslidePlugin.allFaces) {
			Block b1 = centreBlock.getRelative(face);
			if (!b1.getType().isSolid()) {
				dirModifier.add(new Vector(face.getModX() * (plugin.getRandom().nextFloat() * 1.0 + 1.0),
				                           face.getModY() * (plugin.getRandom().nextFloat() * 1.0 + 1.0),
				                           face.getModZ() * (plugin.getRandom().nextFloat() * 1.0 + 1.0)));
				n++;
			}
		}
		if (n > 1) {
			dirModifier = dirModifier.multiply(1.0 / n);
		}

		final double distMax = Math.sqrt(distMax0);
		final double forceMult = plugin.getConfig().getDouble("explosions.force_mult", 1.0);
		final int yieldChance = plugin.getConfig().getInt("explosions.yield_chance", 50);

		LogUtils.fine("explosion: cause = " + event.getEntity() + ", " + event.blockList().size() + " blocks affected, radius = " + distMax);
		for (Block b : event.blockList()) {
			if (plugin.getRandom().nextInt(100) > yieldChance) {
				b.setType(Material.AIR);
				continue;
			}
			double xOff = b.getX() - centre.getBlockX();
			double yOff = b.getY() - centre.getBlockY();
			double zOff = b.getZ() - centre.getBlockZ();
			double dist = Math.sqrt(xOff * xOff + yOff * yOff + zOff * zOff);
			double power = Math.abs((double)distMax - (double)dist) / 3.0;
			Vector vec = new Vector(xOff, yOff, zOff).normalize().multiply(forceMult * power);
			plugin.getSlideManager().scheduleBlockFling(b, vec.clone(), dirModifier.clone());
		}
	}

	@EventHandler
	public void onInteract(PlayerInteractEvent event) {
		Player player = event.getPlayer();
		SlideOTron wand = SlideOTron.getWand(player);
		if (wand == null) {
			return;
		}
		event.setCancelled(true);
		if (!player.hasPermission("landslide.use.wand")) {
			player.getWorld().createExplosion(player.getEyeLocation().add(player.getLocation().getDirection().normalize()), 0.0f);
			Vector vel = player.getLocation().getDirection().normalize().multiply(-5.0);
			vel.setY(1.2);
			player.setVelocity(vel);
			player.damage(2);
			MiscUtil.errorMessage(player, "The Slide-O-Tron\u2122 explodes in your face!");
			player.setItemInHand(null);
			return;
		}
		int quantity = player.getItemInHand().getAmount();
		switch (event.getAction()) {
		case LEFT_CLICK_AIR: case LEFT_CLICK_BLOCK:
			wand.activate(plugin, player);
			break;
		case RIGHT_CLICK_AIR: case RIGHT_CLICK_BLOCK:
			wand.setMode(wand.getMode().nextMode());
			player.setItemInHand(wand.toItemStack(quantity));
			break;
		default:
			break;
		}
	}

	@EventHandler
	public void mouseWheel(PlayerItemHeldEvent event) {
		Player player = event.getPlayer();
		if (!player.isSneaking()) {
			return;
		}
		SlideOTron wand = SlideOTron.getWand(player);
		if (wand == null) {
			return;
		}
		event.setCancelled(true);
		int delta = event.getNewSlot() - event.getPreviousSlot();
		if (delta == 0) {
			return;
		} else if (delta >= 6) {
			delta -= 9;
		} else if (delta <= -6) {
			delta += 9;
		}
		wand.setPower(wand.getPower() - delta);
		player.setItemInHand(wand.toItemStack(player.getItemInHand().getAmount()));
	}

	/**
	 * Handle the case where a snow layer or snow block falling block lands (and sucessfully forms a
	 * new block) on an existing snow layer.
	 *
	 * @param block the snow layer block being landed on
	 * @param fb the falling block, either snow layer or snow block
	 */
	private void handleSnowAccumulation(final Block block, FallingBlock fb) {
		byte fbThickness = fb.getMaterial() == Material.SNOW ? fb.getBlockData() : 7;
		final byte newThickness = (byte)(block.getData() + fbThickness + 1);
		if (newThickness > 7) {
			Bukkit.getScheduler().runTask(plugin, new Runnable() {
				@Override
				public void run() {
					block.setTypeIdAndData(Material.SNOW_BLOCK.getId(), (byte) 0, true);
					block.getRelative(BlockFace.UP).setTypeIdAndData(Material.SNOW.getId(), (byte)(newThickness - 8), true);
				}
			});
		} else {
			Bukkit.getScheduler().runTask(plugin, new Runnable() {
				@Override
				public void run() {
					if (newThickness == 7) {
						block.setTypeIdAndData(Material.SNOW_BLOCK.getId(), (byte) 0, true);
					} else {
						block.setData(newThickness, true);
					}
				}
			});
		}
	}

	private Block findAdjacentSolid(Block b) {
		for (BlockFace face : LandslidePlugin.allFaces) {
			Block b1 = b.getRelative(face);
			if (b1.getType().isSolid()) {
				return b1;
			}
		}
		return b;
	}

	private boolean explosionEventApplies(Entity e) {
		if (e instanceof Creeper && plugin.getConfig().getBoolean("fancy_explosions.creeper")) {
			return true;
		} else if (e instanceof TNTPrimed && plugin.getConfig().getBoolean("fancy_explosions.tnt")) {
			return true;
		} else if (e instanceof EnderDragon && plugin.getConfig().getBoolean("fancy_explosions.enderdragon")) {
			return true;
		} else if (plugin.getConfig().getBoolean("fancy_explosions.other")) {
			return true;
		}
		return false;
	}

	private boolean checkForSlide(Block block, Material mat, byte data, boolean immediate, boolean always) {
		int chance = always ? 100 : plugin.getPerWorldConfig().getSlideChance(block.getWorld(), mat);
		boolean orphan = plugin.isOrphan(block);
		if (chance > 0 && orphan && plugin.getConfig().getBoolean("drop_slidy_floaters")) {
			return plugin.getSlideManager().scheduleBlockSlide(block, BlockFace.DOWN, mat, data, true);
		} else if (orphan && plugin.getConfig().getBoolean("drop_nonslidy_floaters")) {
			return plugin.getSlideManager().scheduleBlockSlide(block, BlockFace.DOWN, mat, data, true);
		} else if (chance > plugin.getRandom().nextInt(100)) {
			if (block.getType() == Material.SNOW) {
				// special case; snow can slide off in layers, and the minimum thickness is configurable
				if (block.getData() < plugin.getPerWorldConfig().getSnowSlideThickness(block.getWorld()) - 1) {
					return false;
				}
			}
			BlockFace face = plugin.getSlideManager().wouldSlide(block);
			if (face != null) {
				return plugin.getSlideManager().scheduleBlockSlide(block, face, mat, data, immediate);
			}
		}
		return false;
	}

	private boolean checkForSlide(Block block) {
		return checkForSlide(block, block.getType(), block.getData(), false, false);
	}

}
