package me.desht.landslide.commands;

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

import me.desht.dhutils.DHValidate;
import me.desht.dhutils.MiscUtil;
import me.desht.dhutils.commands.AbstractCommand;
import me.desht.landslide.SlideOTron;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class WandCommand extends AbstractCommand {

    public WandCommand() {
        super("landslide wand", 0, 1);
        setPermissionNode("landslide.commands.wand");
        setUsage("/<command> wand [<player-name>]");
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean execute(Plugin plugin, CommandSender sender, String[] args) {
        Player player;
        if (args.length > 0) {
            String playerName = args[0];
            player = Bukkit.getPlayer(playerName);
            DHValidate.notNull(player, "Player " + playerName + " is not online.");
        } else {
            notFromConsole(sender);
            player = (Player) sender;
        }

        player.getInventory().addItem(SlideOTron.makeWand());
        player.updateInventory();

        if (!sender.getName().equals(player.getName())) {
            MiscUtil.statusMessage(sender, "Gave a Slide-O-Tron\u2122 to " + player.getName());
            String from = sender instanceof ConsoleCommandSender ? "the Powers-That-Be" : sender.getName();
            MiscUtil.alertMessage(player, "You received a Slide-O-Tron\u2122 from " + from + ".  Use with care.");
        } else {
            MiscUtil.statusMessage(sender, "Gave yourself a Slide-O-Tron\u2122");
        }
        return true;
    }

}
