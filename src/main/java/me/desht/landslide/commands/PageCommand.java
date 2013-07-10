package me.desht.landslide.commands;

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
