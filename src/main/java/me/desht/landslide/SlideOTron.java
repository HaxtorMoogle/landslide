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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.desht.dhutils.ItemGlow;
import me.desht.dhutils.MiscUtil;
import me.desht.dhutils.ParticleEffect;
import me.desht.dhutils.cuboid.Cuboid;
import me.desht.dhutils.cuboid.Cuboid.CuboidDirection;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.BlockIterator;

public class SlideOTron {
	private static final String DISPLAY_PREFIX = ChatColor.YELLOW + "Slide-O-Tron\u2122: " + ChatColor.GOLD;
	private static final String POWER_PREFIX = ChatColor.ITALIC.toString();
	private static final Material WAND_MATERIAL = Material.BLAZE_ROD;

	// for parsing data out of the item meta display name
	private static final Pattern textPat = Pattern.compile(DISPLAY_PREFIX + "(.+?)" + POWER_PREFIX + " ([0-9]+)");

	public enum Mode {
		KABOOM ("Block Exploder"),
		FORCE_SLIDE ("Slide Initiator");

		private static final Map<String, Mode> map = new HashMap<String, SlideOTron.Mode>();
		static {
			for (Mode m : values()) {
				map.put(m.getText(), m);
			}
		}
		private final String text;

		private Mode(String text) {
			this.text = text;
		}

		public String getText() {
			return text;
		}

		public Mode nextMode() {
			int idx = (ordinal() + 1) % Mode.values().length;
			return Mode.values()[idx];
		}

		public static Mode getModeForText(String text) {
			return map.get(text);
		}
	}

	private Mode mode;
	private int power;

	private SlideOTron() {
		mode = Mode.KABOOM;
		power = 2;
	}

	private SlideOTron(ItemMeta meta) {
		String s = meta.getDisplayName();
		Matcher m = textPat.matcher(s);
		if (m.find() && m.groupCount() == 2) {
			mode = Mode.getModeForText(m.group(1));
			if (mode == null) {
				mode = Mode.KABOOM;
			}
			try {
				this.power = Integer.parseInt(m.group(2));
			} catch (NumberFormatException e) {
				this.power = 2;
			}
		}
	}

	public static void setupRecipe() {
		ShapedRecipe recipe = new ShapedRecipe(new SlideOTron().toItemStack(1));
		recipe.shape("ABC");
		recipe.setIngredient('A', Material.ENDER_PEARL);
		recipe.setIngredient('B', Material.BLAZE_ROD);
		recipe.setIngredient('C', Material.TNT);
		Bukkit.addRecipe(recipe);
	}

	/**
	 * @return the mode
	 */
	public Mode getMode() {
		return mode;
	}

	/**
	 * @param mode the mode to set
	 */
	public void setMode(Mode mode) {
		this.mode = mode;
	}

	/**
	 * @return the power
	 */
	public int getPower() {
		return power;
	}

	/**
	 * @param power the power to set
	 */
	public void setPower(int power) {
		this.power = Math.max(power, 0);
	}

	/**
	 * Get a wand item for this Slide-O-Tron.
	 *
	 * @return an ItemStack with the appropriate metadata set
	 */
	public ItemStack toItemStack(int quantity) {
		ItemStack item = new ItemStack(WAND_MATERIAL, quantity);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(DISPLAY_PREFIX + mode.getText() + POWER_PREFIX + " " + power);
		List<String> lore = new ArrayList<String>();
		lore.add(ChatColor.GRAY + ChatColor.ITALIC.toString() + "Left-click: use wand");
		lore.add(ChatColor.GRAY + ChatColor.ITALIC.toString() + "Right-click: change mode");
		lore.add(ChatColor.GRAY + ChatColor.ITALIC.toString() + "Shift + Mouse-wheel: change power");
		meta.setLore(lore);
		item.setItemMeta(meta);
		if (LandslidePlugin.getInstance().isProtocolLibEnabled()) {
			ItemGlow.setGlowing(item, true);
		}
		return item;
	}

	@Override
	public String toString() {
		return "Slide-O-Tron: mode=" + mode + " power=" + power;
	}

	public static ItemStack makeWand() {
		return new SlideOTron().toItemStack(1);
	}

	/**
	 * Get the Slide-O-Tron that the player is holding, if any.
	 *
	 * @param player the player to check
	 * @return returns the Slide-O-Tron, or null if the player isn't holding one
	 */
	public static SlideOTron getWand(Player player) {
		ItemStack item = player.getItemInHand();
		if (item.getType() != WAND_MATERIAL) {
			return null;
		}
		ItemMeta meta = item.getItemMeta();
		if (meta.getDisplayName() == null || !meta.getDisplayName().startsWith(DISPLAY_PREFIX)) {
			return null;
		}
		return new SlideOTron(meta);
	}

	private static final HashSet<Byte> transparent = new HashSet<Byte>();
	static {
		transparent.add((byte)Material.STATIONARY_WATER.getId());
		transparent.add((byte)Material.AIR.getId());
	}

	public void activate(LandslidePlugin plugin, Player player) {
		Block b;
		switch (mode) {
		case KABOOM:
			b = player.getTargetBlock(transparent, 140);
			if (b != null && b.getY() != 0 && b.getType() != Material.AIR) {
				player.getWorld().createExplosion(b.getLocation(), power);
			} else {
				player.playSound(player.getLocation(), Sound.NOTE_BASS, 1.0f, 1.0f);
			}
			break;
		case FORCE_SLIDE:
			BlockIterator iter = new BlockIterator(player, 140);
			int n = 0;
			while (iter.hasNext()) {
				Block bb = iter.next();
				if (bb.getType() == Material.BEDROCK || bb.getY() < 1 || bb.getY() > bb.getWorld().getMaxHeight()) {
					player.playSound(player.getLocation(), Sound.NOTE_BASS, 1.0f, 1.0f);
					break;
				}
				if (bb.getType() == Material.AIR || bb.isLiquid()) {
					if (n > 3 && n % 2 == 0) {
						if (plugin.isProtocolLibEnabled()) {
							ParticleEffect.WITCH_MAGIC.play(bb.getLocation(), 0.2f, 0.2f, 0.2f, 1.0f, 3);
						} else {
							bb.getWorld().playEffect(bb.getLocation(), Effect.SMOKE, 0);
						}
					}
				} else {
					forceSlide(plugin, player, bb);
					break;
				}
				n++;
			}
			break;
		default:
			break;
		}
	}

	private void forceSlide(LandslidePlugin plugin, Player player, Block b) {
		int size = Math.min(power, 20);
		int n = 0;

		Cuboid c = new Cuboid(b.getLocation());
		for (int s = 0; s <= size; s++) {
			if (s == 0) {
				// degenerate case
				BlockFace face = plugin.getSlideManager().wouldSlide(b);
				if (face != null) {
					plugin.getSlideManager().scheduleBlockSlide(b, face);
					n++;
				}
			} else {
				n += forceSlide(plugin, b.getType(), c.getFace(CuboidDirection.Down));
				n += forceSlide(plugin, b.getType(), c.getFace(CuboidDirection.North).inset(CuboidDirection.Vertical, 1));
				n += forceSlide(plugin, b.getType(), c.getFace(CuboidDirection.South).inset(CuboidDirection.Vertical, 1));
				n += forceSlide(plugin, b.getType(), c.getFace(CuboidDirection.East).inset(CuboidDirection.Vertical, 1));
				n += forceSlide(plugin, b.getType(), c.getFace(CuboidDirection.West).inset(CuboidDirection.Vertical, 1));
				n += forceSlide(plugin, b.getType(), c.getFace(CuboidDirection.Up));
			}
			c = c.outset(CuboidDirection.Both, 1);
		}
		String s = n == 1 ? "" : "s";
		MiscUtil.statusMessage(player, "Scheduled " + n + " block" + s + " to slide");
	}

	private int forceSlide(LandslidePlugin plugin, Material mat, Cuboid c) {
		int n = 0;
		for (Block b : c) {
			if (b.getType() == mat) {
				BlockFace face = plugin.getSlideManager().wouldSlide(b);
				if (face != null) {
					plugin.getSlideManager().scheduleBlockSlide(b, face);
					n++;
				}
			}
		}
		return n;
	}
}
