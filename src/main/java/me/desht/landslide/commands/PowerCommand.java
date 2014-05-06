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

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import me.desht.dhutils.DHUtilsException;
import me.desht.dhutils.DHValidate;
import me.desht.dhutils.commands.AbstractCommand;
import me.desht.landslide.SlideOTron;

public class PowerCommand extends AbstractCommand {

    public PowerCommand() {
        super("landslide power", 1, 1);
        setPermissionNode("landslide.commands.power");
        setUsage("/<command> power <power-level>");
    }

    @Override
    public boolean execute(Plugin plugin, CommandSender sender, String[] args) {
        notFromConsole(sender);

        Player player = (Player) sender;
        SlideOTron wand = SlideOTron.getWand(player);
        DHValidate.notNull(wand, "You are not holding a Slide-O-Tron wand");

        try {
            int power = Integer.parseInt(args[0]);
            wand.setPower(power);
            player.setItemInHand(wand.toItemStack(player.getItemInHand().getAmount()));
        } catch (NumberFormatException e) {
            throw new DHUtilsException("Invalid numeric quantity: " + args[0]);
        }

        return true;
    }

}
