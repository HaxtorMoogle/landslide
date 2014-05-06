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

import me.desht.dhutils.MessagePager;
import me.desht.dhutils.MiscUtil;
import me.desht.dhutils.commands.AbstractCommand;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

public class PageCommand extends AbstractCommand {

    public PageCommand() {
        super("landslide page", 0, 1);
        setUsage("/<command> page [n|p|#]");
    }

    @Override
    public boolean execute(Plugin plugin, CommandSender sender, String[] args) {
        MessagePager pager = MessagePager.getPager(sender);
        if (args.length < 1) {
            // default is to advance one page and display
            pager.nextPage();
            pager.showPage();
        } else if (args[0].startsWith("n")) {
            pager.nextPage();
            pager.showPage();
        } else if (args[0].startsWith("p")) {
            pager.prevPage();
            pager.showPage();
        } else {
            try {
                int pageNum = Integer.parseInt(args[0]);
                pager.showPage(pageNum);
            } catch (NumberFormatException e) {
                MiscUtil.errorMessage(sender, "Invalid numeric quantity: " + args[0]);
            }
        }
        return true;
    }

}
