package com.nullbyte.quarrycraft;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;


public class Main extends JavaPlugin implements Listener {
	public JavaPlugin plugin;
	public ArrayList<Quarry> quarries;

	@Override
	public void onEnable() {
		Bukkit.getServer().getPluginManager().registerEvents(this, this);
		this.plugin = this;
		
		String fileSeparator = System.getProperty("file.separator");
		File pluginDir = new File("plugins" + fileSeparator + "QuarryCraft");
		pluginDir.mkdir();
		quarries = new ArrayList<Quarry>();
		loadQuarries();
		new QuarryCleaner().runTaskTimer(plugin, 10, 10);
	}
	
	@EventHandler
	public void onPlayerLeftClick(PlayerInteractEvent e) {
		if(e.getAction().equals(Action.LEFT_CLICK_BLOCK) || e.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
			if(!canInteract(e.getClickedBlock().getLocation(), e.getPlayer())) {
				e.getPlayer().sendMessage(ChatColor.DARK_RED + "You do not have permission to interact here. This is not your quarry!");
				e.setCancelled(true);
				return;
			}
			if(e.getAction().equals(Action.LEFT_CLICK_BLOCK) && !canBreak(e.getClickedBlock().getLocation(), e.getPlayer())) {
				e.getPlayer().sendMessage(ChatColor.DARK_RED + "Sorry, this block may not be broken!");
				e.setCancelled(true);
				return;
			}
		}
		
		if(e.getAction().equals(Action.LEFT_CLICK_BLOCK)) {
			if(!e.getPlayer().isSneaking()) return;
			Block clicked = e.getClickedBlock();
			if(clicked.getType().equals(Material.CHEST)) {
				Chest centreChest = (Chest) clicked.getState();
				if(isQuarryLayout(centreChest)) {
					if(addQuarry(centreChest, e.getPlayer().getName())) {
						e.getPlayer().sendMessage(ChatColor.GREEN+ "You have created a new quarry.");
						e.setCancelled(true);
					}
					else {
						e.getPlayer().sendMessage(getQuarry(centreChest).toggleEndermining());
						e.setCancelled(true);
					}
					saveQuarries();
				}
			}
			if(clicked.getType().equals(Material.DIAMOND_BLOCK)) {
				for(Quarry q : quarries) {
					if(q.isIn3x3(clicked)) {
						q.resetMiningCursor();
						e.getPlayer().sendMessage("Mining cursor reset to y=" + ChatColor.DARK_GREEN + q.nextY);
						e.setCancelled(true);
						return;
					}
				}
			}
		}
		if(e.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
			if(!e.getPlayer().isSneaking()) return;
			Block clicked = e.getClickedBlock();
			if(clicked.getType().equals(Material.CHEST)) {
				Chest centreChest = (Chest) clicked.getState();
				if(isQuarryLayout(centreChest)) {
					Quarry q = getQuarry(centreChest);
					if(q != null && e.getPlayer().getItemInHand().getType().equals(Material.AIR)){
						q.sendProgress();
						e.setCancelled(true);
					}
				}
			}
		}
	}
	
	public boolean isQuarryLayout(Chest centreChest) {
		Location centreLoc = centreChest.getLocation();
		int cx = centreLoc.getBlockX();
		int cy = centreLoc.getBlockY();
		int cz = centreLoc.getBlockZ();
		World w = centreLoc.getWorld();
		
		if(!w.getBlockAt(cx-1, cy, cz).getType().equals(Material.DIAMOND_BLOCK)) return false;
		if(!w.getBlockAt(cx+1, cy, cz).getType().equals(Material.DIAMOND_BLOCK)) return false;
		if(!w.getBlockAt(cx, cy, cz-1).getType().equals(Material.DIAMOND_BLOCK)) return false;
		if(!w.getBlockAt(cx, cy, cz+1).getType().equals(Material.DIAMOND_BLOCK)) return false;
		
		if(!w.getBlockAt(cx-1, cy, cz-1).getType().equals(Material.REDSTONE_BLOCK)) return false;
		if(!w.getBlockAt(cx-1, cy, cz+1).getType().equals(Material.REDSTONE_BLOCK)) return false;
		if(!w.getBlockAt(cx+1, cy, cz-1).getType().equals(Material.REDSTONE_BLOCK)) return false;
		if(!w.getBlockAt(cx+1, cy, cz+1).getType().equals(Material.REDSTONE_BLOCK)) return false;
		
		return true;
	}
	
	public Quarry getQuarry(Chest centreChest) {
		for(Quarry quarry : quarries) {
			if(quarry.isSameCentreChest(centreChest)) return quarry;
		}
		return null;
	}
	
	public void removeQuarry(Quarry q) {
		for(int i=0; i<quarries.size(); i++) {
			if(q.getLocation().equals(quarries.get(i).getLocation()) ) {
				quarries.get(i).cancel();
				quarries.remove(i);
				saveQuarries();
				return;
			}
		}
	}
	
	public void saveQuarries() {
		String fileSeparator = System.getProperty("file.separator");
		String path = "plugins" + fileSeparator + "QuarryCraft" + fileSeparator + "quarries.txt";
		File quarryFile = new File(path);
		quarryFile.delete();
		String fileString = "";
		for(Quarry q : quarries) {
			Location quarryLoc = q.getLocation();
			int minX = q.minX;
			int minZ = q.minZ;
			int maxX = q.maxX;
			int maxZ = q.maxZ;
			fileString += quarryLoc.getWorld().getName() + ";" + quarryLoc.getBlockX() + ";" + quarryLoc.getBlockY() + ";" + quarryLoc.getBlockZ() + ";" + minX + ";" + minZ + ";" + maxX + ";" + maxZ + ";" + q.classicMode + ";" + q.owner+"\n";		
		}
		
		try {
			FileOutputStream fos = new FileOutputStream(path);
			fos.write(fileString.getBytes());
			fos.flush();
			fos.close();
		} catch (IOException e) {
			//e.printStackTrace();
		}
	}
	
	public void loadQuarries() {
		String fileSeparator = System.getProperty("file.separator");
		String path = "plugins" + fileSeparator + "QuarryCraft" + fileSeparator + "quarries.txt";
		try {
			BufferedReader inFile = new BufferedReader(new FileReader(path));
			String currentCoords;
			String[] locString;
			int minX, minZ, maxX, maxZ;
			boolean classicMode;
			int x,y,z;
			Location currentLocation;
			String ownerName;
			do {
				currentCoords = inFile.readLine();
				if(currentCoords == null) break;
				locString = currentCoords.split(";");
				x = Integer.parseInt(locString[1]);
				y = Integer.parseInt(locString[2]);
				z = Integer.parseInt(locString[3]);
				minX = Integer.parseInt(locString[4]);
				minZ = Integer.parseInt(locString[5]);
				maxX = Integer.parseInt(locString[6]);
				maxZ = Integer.parseInt(locString[7]);
				ownerName = locString[9].trim();
				classicMode = locString[8].trim().contentEquals("true");
				Location quarryLoc = new Location(Bukkit.getWorld(locString[0]), x, y, z);
				addQuarry(quarryLoc, minX, maxX, minZ, maxZ, classicMode,ownerName);
					
			} while(currentCoords != null);
			
			saveQuarries();
		} catch (IOException e) {
			//e.printStackTrace();
		}
	}
	
	public boolean canInteract(Location l, Player p) {
		for(Quarry q : quarries)
			if(!q.canInteractAt(l, p)) return false;
		return true;
	}
	
	public boolean canBreak(Location l, Player p) {
		for(Quarry q : quarries)
			if(!q.canBreak(l, p)) return false;
		return true;
	}
	
	public boolean addQuarry(Chest centreChest, String name) {
		if(getQuarry(centreChest) == null) {
			Quarry quarry = new Quarry(centreChest, name);
			quarries.add(quarry);
			quarry.runTaskTimer(plugin, 0, 0);
			return true;
		}
		return false;
	}
	
	public boolean addQuarry(Location centreChestLocation, int minX, int maxX, int minZ, int maxZ, boolean mode, String name) {
		if(centreChestLocation.getWorld().getBlockAt(centreChestLocation).getType().equals(Material.CHEST)) {
			Quarry quarry = new Quarry((Chest)centreChestLocation.getWorld().getBlockAt(centreChestLocation).getState(), minX, maxX, minZ, maxZ, mode, name);
			quarries.add(quarry);
			quarry.runTaskTimer(plugin, 0, 0);
			return true;
		}
		return false;
	}
	
	class QuarryCleaner extends BukkitRunnable {
		@Override
		public void run() {
			for(Quarry q : quarries)
				if(q.markedForDeletion || !isQuarryLayout(q.centreChest)) {
					q.clearPlatform();
					q.tellOwner(ChatColor.DARK_RED + "Quarry at " + q.centreChestLocation.toVector().toString() + " destroyed");
					removeQuarry(q);
					return;
				}
		}
	}
}
