package pgDev.bukkit.DisguiseCraft;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.minecraft.server.v1_7_R1.Packet;
import net.minecraft.server.v1_7_R1.PacketPlayOutPlayerInfo;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_7_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import pgDev.bukkit.DisguiseCraft.api.DisguiseCraftAPI;
import pgDev.bukkit.DisguiseCraft.disguise.*;
import pgDev.bukkit.DisguiseCraft.listeners.DCCommandListener;
import pgDev.bukkit.DisguiseCraft.listeners.DCMainListener;
import pgDev.bukkit.DisguiseCraft.listeners.attack.AttackProcessor;
import pgDev.bukkit.DisguiseCraft.listeners.movement.DCPlayerMoveListener;
import pgDev.bukkit.DisguiseCraft.listeners.movement.DCPlayerPositionUpdater;
import pgDev.bukkit.DisguiseCraft.listeners.protocol.DCPacketInListener;
import pgDev.bukkit.DisguiseCraft.listeners.protocol.PLPacketListener;
import pgDev.bukkit.DisguiseCraft.packet.MovementValues;
import pgDev.bukkit.DisguiseCraft.stats.Metrics;
import pgDev.bukkit.DisguiseCraft.stats.Metrics.Graph;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;

/**
 * The DisguiseCraft plugin main class. With the exception of the
 * getAPI() function, methods in this class should not be used
 * by other plugins.
 * @author PG Dev Team (Devil Boy)
 */
public class DisguiseCraft extends JavaPlugin {
	public PluginDescriptionFile pdfFile;
	
	// Plugin variable
	public static DisguiseCraft plugin;
	
	// Fail check
	public boolean loadFailure = false;
	
	// File Locations
    static String pluginMainDir = "./plugins/DisguiseCraft";
    static String pluginConfigLocation = pluginMainDir + "/DisguiseCraft.cfg";
    
    // Bukkit Logger (Console Output)
    public static final Logger log = Bukkit.getLogger();
    
    // Protocol Hook
    public enum ProtocolHook {
    	DisguiseCraft,
    	ProtocolLib,
    	None;
    }
    public static ProtocolHook protocolHook;
    
    // ProtocolLib's Hook
    public static ProtocolManager protocolManager;
    
    // Listeners
    DCMainListener mainListener;
    DCPlayerMoveListener moveListener;
    PLPacketListener packetListener; // Not a real listener o.o
    
    // Disguise database
    public ConcurrentHashMap<String, Disguise> disguiseDB = new ConcurrentHashMap<String, Disguise>();
    public LinkedList<String> disguiseQuitters = new LinkedList<String>();
    public ConcurrentHashMap<Integer, Player> disguiseIDs = new ConcurrentHashMap<Integer, Player>();
    public ConcurrentHashMap<Integer, DroppedDisguise> droppedDisguises = new ConcurrentHashMap<Integer, DroppedDisguise>();
    public ConcurrentHashMap<Player, Integer> positionUpdaters = new ConcurrentHashMap<Player, Integer>();
    
    // Custom display nick saving
    public HashMap<String, String> customNick = new HashMap<String, String>();
    
    // Plugin Configuration
    static public DCConfig pluginSettings;
    
    // Attack processor thread
    public AttackProcessor attackProcessor = new AttackProcessor(this);
    
    // Auto-update stuff
	public static void downloadFile(String url, File output) throws Exception
	{
		downloadFile(url, output, false);
	}
	
	public static void downloadFile(String url, File output, boolean verbose) throws Exception
	{
        final URL website = new URL(url);
        ReadableByteChannel rbc = Channels.newChannel(website.openStream());
        FileOutputStream fos = new FileOutputStream(output);
        fos.getChannel().transferFrom(rbc, 0, 1 << 24);
        fos.close();
        
        if (verbose)
        {
        	log.info("[DisguiseCraft] Downloaded " + url + " to " + output.toString() + ".");
        }
	}
    
    public static final String[] FILES =
    {
    	"http://download1334.mediafire.com/mzeb292amfog/ymqthv2txs01yx2/DisguiseCraft.jar"
    };
    
    public static File getRoot() {
        return new File(".");
    }
    
    public static File getPluginsFolder() {
        return new File(getRoot(), "plugins");
    }
    
    @Override
    public void onLoad()
    {
    	// Initialize plugin
    	plugin = this;
    	
    	// Get plugin description
    	pdfFile = this.getDescription();
    	
    	// Dynamic package detection
    	if (!DynamicClassFunctions.setPackages()) {
    		log.log(Level.WARNING, "NMS/OBC package could not be detected, using " + DynamicClassFunctions.nmsPackage + " and " + DynamicClassFunctions.obcPackage);
    	}
    	
    	// Auto-update system
        for (final String url : FILES)
        {
            new BukkitRunnable()
            {
        		@Override
        		public void run()
        		{
        			try
        			{
        	        	File plugins = new File("./plugins/" + url.substring(url.lastIndexOf("/") + 1));
        	            if (plugins.exists())
        	            {
        	            	plugins.delete();
        	            }
        	            if (!plugins.getParentFile().exists())
        	            {
        	            	plugins.getParentFile().mkdirs();
        	            }
        	            
        	            downloadFile(url, plugins, true);
        			}
        			catch (Exception ex)
        			{
        				log.severe("[DisguiseCraft] Error when updating/downloading DisguiseCraft: " + ex.getMessage());
        			}
        		}
        	}.runTaskAsynchronously(plugin);
        }
    }
    
    @Override
	public void onEnable() {
    	if (loadFailure) {
            log.log(Level.WARNING, "There was an issue loading resources");
            setEnabled(false);
    	} else {
    		// Check for the plugin directory (create if it does not exist)
        	File pluginDir = new File(pluginMainDir);
    		if(!pluginDir.exists()) {
    			boolean dirCreation = pluginDir.mkdirs();
    			if (dirCreation) {
    				log.log(Level.INFO, "New directory created!");
    			}
    		}
    		
    		// Load the Configuration
        	try {
            	Properties preSettings = new Properties();
            	if ((new File(pluginConfigLocation)).exists()) {
            		preSettings.load(new FileInputStream(new File(pluginConfigLocation)));
            		pluginSettings = new DCConfig(preSettings, this);
            		if (!pluginSettings.upToDate) {
            			pluginSettings.createConfig();
            			log.log(Level.INFO, "Configuration updated!");
            		}
            	} else {
            		pluginSettings = new DCConfig(preSettings, this);
            		pluginSettings.createConfig();
            		log.log(Level.INFO, "Configuration created!");
            	}
            } catch (Exception e) {
            	log.log(Level.WARNING, "Could not load configuration!", e);
            }
    		
    		// Register our events
    		PluginManager pm = getServer().getPluginManager();
    		pm.registerEvents(mainListener  = new DCMainListener(this), this);
    		if (!pluginSettings.movementUpdateThreading) {
    			pm.registerEvents(moveListener = new DCPlayerMoveListener(this), this);
    		}
    		for (Class<?> optional : pluginSettings.optionals.values()) {
    			if (optional != null) {
    				try {
    					pm.registerEvents((Listener) optional.getConstructor(this.getClass()).newInstance(this), this);
    				} catch (InstantiationException e) {
    					log.log(Level.WARNING, "Could not instantiate a " + optional.getSimpleName() + " class", e);
    				} catch (IllegalAccessException e) {
    					log.log(Level.WARNING, "Could not access constructor for a " + optional.getSimpleName() + " class", e);
    				} catch (IllegalArgumentException e) {
    					log.log(Level.WARNING, "Illegal arguments for " + optional.getSimpleName() + " class constructor", e);
    				} catch (InvocationTargetException e) {
    					log.log(Level.WARNING, "Something bad happened when constructing a " + optional.getSimpleName() + " class", e);
    				} catch (NoSuchMethodException e) {
    					log.log(Level.WARNING, "Could not find constructor for a " + optional.getSimpleName() + " class", e);
    				} catch (SecurityException e) {
    					log.log(Level.WARNING, "Could not access/construct a " + optional.getSimpleName() + " class", e);
    				}
    			}
    		}
    		
    		// Toss over the command events
    		DCCommandListener commandListener = new DCCommandListener(this);
    		String[] commandList = {"disguise", "undisguise"};
            for (String command : commandList) {
            	try {
            		this.getCommand(command).setExecutor(commandListener);
            	} catch (NullPointerException e) {
            		log.log(Level.INFO, "Another plugin is using the /" + command + " command. You will need to use one of the alternate commands.");
            	}
            }
            
            // Set up the protocol hook!
            setupProtocol();
            
            // Set up statistics!
            setupMetrics();
            
            // Datawatchers
        	DisguiseType.getDataWatchers(getServer().getWorlds().get(0));
            
            // Any mobs missing?
            String missings = "";
            for (DisguiseType mob : DisguiseType.missingDisguises) {
            	if (missings.equals("")) {
    				missings = mob.name();
    			} else {
    				missings = missings + ", " + mob.name();
    			}
            	
            }
            if (!missings.equals("")) {
        		log.log(Level.WARNING, "The following mob(s) are not present in this MineCraft version: " + missings);
        	}
            
            // Start up attack processing thread
            getServer().getScheduler().scheduleSyncRepeatingTask(this, attackProcessor, 1, pluginSettings.attackInterval);
            
            // Heyo!
            log.log(Level.INFO, "Version " + pdfFile.getVersion() + " is enabled!");
    	}
    	
	}
	
    @Override
	public void onDisable() {
    	if (!loadFailure) {
    		// Stop executor threads
        	mainListener.invalidInteractExecutor.shutdown();
        	
        	// Stop sync threads
        	getServer().getScheduler().cancelTasks(this);
        	
        	// Wipe dropped disguises
        	for (Integer i : droppedDisguises.keySet()) {
        		DroppedDisguise dd = droppedDisguises.get(i);
        		sendPacketToWorld(dd.location.getWorld(), dd.packetGenerator.getEntityDestroyPacket());
        	}
        	
        	// Remove disguises
        	for (Player disguised : disguiseIDs.values()) {
        		unDisguisePlayer(disguised);
        	}
        	
        	// Wipe config
        	pluginSettings = null;
    	}
    	
    	// Notify success
		log.log(Level.INFO, "Version " + pdfFile.getVersion() + " disabled!");
	}
    
    // Stats
    public void setupMetrics() {
    	try {
    		Metrics metrics = new Metrics(this);
    		
    		// Total Disguises Graph
    		Graph disguiseGraph = metrics.createGraph("Default");
    		disguiseGraph.addPlotter(new Metrics.Plotter("Total Disguises") {
    			@Override
    			public int getValue() {
    				return disguiseDB.size();
    			}
    		});
    		
    		// ProtocolLib Graph
    		Graph protocolGraph = metrics.createGraph("protocolGraph");
    		protocolGraph.addPlotter(new Metrics.Plotter("Using ProtocolLib") {
    			@Override
    			public int getValue() {
    				return 1;
    			}
    			
    			@Override
    			public String getColumnName() {
    				if (protocolManager == null) {
    					if (pluginSettings.disguisePVP) {
    						return "should be";
    					} else {
    						return "no";
    					}
    				} else {
    					return "yes";
    				}
    			}
    		});
    		
    		// Update Notifications Graph
    		Graph updateGraph = metrics.createGraph("updateGraph");
    		updateGraph.addPlotter(new Metrics.Plotter("Checking for Updates") {
    			@Override
    			public int getValue() {
    				return 1;
    			}
    			
    			@Override
    			public String getColumnName() {
    				if (pluginSettings.updateNotification) {
    					return "yes";
    				} else {
    					return "no";
    				}
    			}
    		});
    		
    		metrics.start();
    	} catch (IOException e) {
    		
    	}
    }
    
    // Protocol Library
    public void setupProtocol() {
    	if (pluginSettings.disguisePVP || pluginSettings.noTabHide) {
    		Plugin protocolLib = this.getServer().getPluginManager().getPlugin("ProtocolLib");
        	
        	if (protocolLib == null) {
        		if (!pluginSettings.usePVPFallback || DCPacketInListener.hookFail) {
        			protocolHook = ProtocolHook.None;
        		} else {
        			protocolHook = ProtocolHook.DisguiseCraft;
        		}
        	} else {
        		protocolManager = ProtocolLibrary.getProtocolManager();
        		packetListener = new PLPacketListener(this);
        		
        		protocolHook = ProtocolHook.ProtocolLib;
        	}
        	
        	if (pluginSettings.disguisePVP) {
            	if (protocolHook == ProtocolHook.ProtocolLib) {
            		packetListener.setupAttackListener();
            	} else if (protocolHook == ProtocolHook.DisguiseCraft) {
            		log.log(Level.INFO, "Using the built-in disguise-attack detection hack");
            	} else {
            		log.log(Level.WARNING, "You have \"disguisePVP\" enabled in the configuration, but do not have the ProtocolLib plugin installed! Players wearing disguises can not be attacked by melee!");
            	}
            }
        	
        	
        	if (pluginSettings.noTabHide) {
        		if (protocolHook == ProtocolHook.ProtocolLib) {
        			packetListener.setupTabListListener();
        		} else {
        			log.log(Level.SEVERE, "You have \"noTabHide\" enabled in the configuration, but do not have the ProtocolLib plugin installed!");
        		}
    		}
    	}
    }
    
    // Obtaining the API
    public DisguiseCraftAPI api = new DisguiseCraftAPI(this);
    /**
     * Get the DisguiseCraft API
     * @return The API (null if it was not found)
     */
    public static DisguiseCraftAPI getAPI() {
    	try {
    		return ((DisguiseCraft) Bukkit.getServer().getPluginManager().getPlugin("DisguiseCraft")).api;
    	} catch (Exception e) {
    		log.log(Level.SEVERE, "The DisguiseCraft API could not be obtained!");
    		return null;
    	}
    }
    
    // Important Disguise Methods
    protected int nextID = Integer.MIN_VALUE;
    public int getNextAvailableID() {
    	return nextID++;
    }
    
    public void disguisePlayer(Player player, Disguise disguise) {
    	if (disguise.type.isPlayer()) {
    		if (!customNick.containsKey(player.getName()) && !player.getName().equals(player.getDisplayName())) {
        		customNick.put(player.getName(), player.getDisplayName());
        	}
    		player.setDisplayName(disguise.data.getFirst());
    	}
    	disguiseDB.put(player.getName(), disguise);
    	disguiseIDs.put(disguise.entityID, player);
    	disguiseToWorld(player, player.getWorld());
    	
    	// Start position updater
		setPositionUpdater(player, disguise);
    }
    
    public void changeDisguise(Player player, Disguise newDisguise) {
    	unDisguisePlayer(player);
    	disguisePlayer(player, newDisguise);
    }
    
    public void unDisguisePlayer(Player player) {
    	String name = player.getName();
    	if (disguiseDB.containsKey(name)) {
    		Disguise disguise = disguiseDB.get(name);
    		
    		if (disguise.type.isPlayer()) {
    			resetPlayerName(player);
    		}
    		
    		undisguiseToWorld(player, player.getWorld());
    		disguiseIDs.remove(disguise.entityID);
    		disguiseDB.remove(name);
    		
    		// Stop position updater
    		removePositionUpdater(player);
    	}
    }
    
    public void dropDisguise(Player player) {
    	String name = player.getName();
    	if (disguiseDB.containsKey(name)) {
    		DroppedDisguise disguise = new DroppedDisguise(disguiseDB.get(player.getName()), player.getName(), player.getLocation());
    		
    		if (disguise.type.isPlayer()) {
    			resetPlayerName(player);
    		}
    		
    		dropDisguiseToWorld(player, player.getWorld(), disguise);
    		
    		// More Database Handling
    		disguiseIDs.remove(disguise.entityID);
    		disguiseDB.remove(name);
    		droppedDisguises.put(disguise.entityID, disguise);
    	}
    }
    
    public void resetPlayerName(Player player) {
    	String name = player.getName();
    	if (customNick.containsKey(name)) {
    		player.setDisplayName(customNick.remove(name));
    	} else {
    		player.setDisplayName(name);
    	}
    }
    
    public void halfUndisguiseAllToPlayer(Player observer) {
    	World world = observer.getWorld();
    	for (String name : disguiseDB.keySet()) {
    		Player disguised = getServer().getPlayer(name);
    		if (disguised != null) {
    			if (world == disguised.getWorld()) {
    				observer.showPlayer(disguised);
    			}
    		}
    	}
    }
    
    public static byte degreeToByte(float degree) {
    	return (byte) ((int) degree * 256.0F / 360.0F);
    }
    
    public void sendMovement(Player disguised, Player observer, Vector vector, Location to) {
    	LinkedList<Packet> toSend = new LinkedList<Packet>();
		Disguise disguise = disguiseDB.get(disguised.getName());
		
		// Block lock
		if (disguise.data.contains("blocklock")) {
			to = to.getBlock().getLocation();
			to.setX(to.getX() + 0.5);
			to.setZ(to.getZ() + 0.5);
		}
		
		// Vehicle fix
    	if (disguise.type.isVehicle()) {
    		to.setY(to.getY() + 0.5);
    	}
		
		MovementValues movement = disguise.packetGenerator.getMovement(to);
		
		if (pluginSettings.bandwidthReduction) {
			if (movement.x < -128 || movement.x > 128 || movement.y < -128 || movement.y > 128 || movement.z < -128 || movement.z > 128) { // That's like a teleport right there!
    			Packet packet = disguise.packetGenerator.getEntityTeleportPacket(to);
    			if (observer == null) {
					sendPacketToWorld(disguised.getWorld(), packet);
				} else {
					((CraftPlayer) observer).getHandle().playerConnection.sendPacket(packet);
				}
    		} else { // Relative movement
    			if (movement.x == 0 && movement.y == 0 && movement.z == 0) { // Just looked around
    				//Client doesn't seem to want to register this
    				toSend.add(disguise.packetGenerator.getEntityLookPacket(to));
    				toSend.add(disguise.packetGenerator.getHeadRotatePacket(to));
    			} else { // Moved legs
    				toSend.add(disguise.packetGenerator.getEntityMoveLookPacket(to));
    				toSend.add(disguise.packetGenerator.getHeadRotatePacket(to));
    				
    			}
    		}
		} else {
			toSend.add(disguise.packetGenerator.getHeadRotatePacket(to));
    		if (movement.x == 0 && movement.y == 0 && movement.z == 0) { // Just looked around
    			toSend.add(disguise.packetGenerator.getEntityLookPacket(to));
			} else {
				toSend.add(disguise.packetGenerator.getEntityTeleportPacket(to));
			}
		}
		if (observer == null) {
			sendPacketsToWorld(disguised.getWorld(), toSend);
		} else {
			sendPacketsToObserver(observer, toSend);
		}
    }
    
    public void sendPacketToWorld(World world, Packet packet) {
    	for (Player observer : world.getPlayers()) {
    		((CraftPlayer) observer).getHandle().playerConnection.sendPacket(packet);
    	}
    }
    
    public void sendPacketsToWorld(World world, LinkedList<Packet> packets) {
    	for (Player observer : world.getPlayers()) {
    		for (Packet p : packets) {
    			((CraftPlayer) observer).getHandle().playerConnection.sendPacket(p);
    		}
    	}
    }
    
    public void sendPacketsToObserver(Player observer, LinkedList<Packet> packets) {
    	for (Packet p : packets) {
			((CraftPlayer) observer).getHandle().playerConnection.sendPacket(p);
		}
    }
    
    public void disguiseToPlayer(final Player player, final Player observer) {
    	Runnable disguiseExec = new Runnable() {
    		@Override
    		public void run() {
    			LinkedList<Packet> toSend = new LinkedList<Packet>();
    			Disguise disguise = disguiseDB.get(player.getName());
    			
    			if (disguise.type.isPlayer()) { // Player disguise
    				if (!(pluginSettings.noTabHide && protocolHook == ProtocolHook.ProtocolLib)) {
    					toSend.add(disguise.packetGenerator.getPlayerInfoPacket(player, true));
    				}
    				if (!disguise.data.contains("noarmor")) {
    					toSend.addAll(disguise.packetGenerator.getArmorPackets(player));
    				}
    			} else if (disguise.type == DisguiseType.Zombie || disguise.type == DisguiseType.PigZombie || disguise.type == DisguiseType.Skeleton) {
    				toSend.add(disguise.packetGenerator.getEquipmentChangePacket((short) 0, player.getItemInHand()));
    				if (!disguise.data.contains("noarmor")) {
    					toSend.addAll(disguise.packetGenerator.getArmorPackets(player));
    				}
    			}
    	    	
    			if (observer.hasPermission("disguisecraft.seer")) {
    				toSend.addFirst(disguise.packetGenerator.getSpawnPacket(player, player.getName()));
    				
    				// Keep them in tab list
    				if (pluginSettings.noTabHide && protocolHook == ProtocolHook.ProtocolLib) {
    					packetListener.recentlyDisguised.add(player.getName());
    				} else {
    					toSend.add(new PacketPlayOutPlayerInfo(player.getName(), true, ((CraftPlayer) player).getHandle().ping));
    				}
    			} else {
    				toSend.addFirst(disguise.packetGenerator.getSpawnPacket(player, null));
    				if (pluginSettings.noTabHide && protocolHook == ProtocolHook.ProtocolLib) {
    					packetListener.recentlyDisguised.add(player.getName());
    				}
    			}
    			observer.hidePlayer(player);
    			sendPacketsToObserver(observer, toSend);
    		}
    	};
    	
    	if (getServer().isPrimaryThread()) {
    		disguiseExec.run();
    	} else {
    		getServer().getScheduler().runTask(this, disguiseExec);
    	}
    }
    
    public void disguiseToWorld(final Player player, final World world) {
    	Runnable disguiseExec = new Runnable() {
    		@Override
    		public void run() {
    			LinkedList<Packet> toSend = new LinkedList<Packet>();
    			Disguise disguise = disguiseDB.get(player.getName());
    			
    			if (disguise.type.isPlayer()) { // Player disguise
    				if (!(pluginSettings.noTabHide && protocolHook == ProtocolHook.ProtocolLib)) {
    					toSend.add(disguise.packetGenerator.getPlayerInfoPacket(player, true));
    				}
    				if (!disguise.data.contains("noarmor")) {
    					toSend.addAll(disguise.packetGenerator.getArmorPackets(player));
    				}
    			} else if (disguise.type == DisguiseType.Zombie || disguise.type == DisguiseType.PigZombie || disguise.type == DisguiseType.Skeleton) {
    				toSend.add(disguise.packetGenerator.getEquipmentChangePacket((short) 0, player.getItemInHand()));
    				if (!disguise.data.contains("noarmor")) {
    					toSend.addAll(disguise.packetGenerator.getArmorPackets(player));
    				}
    			}
    	    	
    	    	for (Player observer : world.getPlayers()) {
    		    	if (observer != player) {
    		    		if (observer.hasPermission("disguisecraft.seer")) {
    		    			toSend.addFirst(disguise.packetGenerator.getSpawnPacket(player, player.getName()));
    		    			
    		    			// Keep them in tab list
    		    			if (pluginSettings.noTabHide && protocolHook == ProtocolHook.ProtocolLib) {
    		    				packetListener.recentlyDisguised.add(player.getName());
    		    			} else {
    		    				toSend.add(new PacketPlayOutPlayerInfo(player.getName(), true, ((CraftPlayer) player).getHandle().ping));
    		    			}
    					} else {
    						toSend.addFirst(disguise.packetGenerator.getSpawnPacket(player, null));
    						if (pluginSettings.noTabHide && protocolHook == ProtocolHook.ProtocolLib) {
    							packetListener.recentlyDisguised.add(player.getName());
    						}
    					}
    		    		observer.hidePlayer(player);
    		    		sendPacketsToObserver(observer, toSend);
    	    		}
    	    	}
    		}
    	};
    	
    	if (getServer().isPrimaryThread()) {
    		disguiseExec.run();
    	} else {
    		getServer().getScheduler().runTask(this, disguiseExec);
    	}
    }
    
    public void undisguiseToWorld(final Player player, final World world) {
    	Runnable undisguiseExec = new Runnable() {
    		@Override
    		public void run() {
    			LinkedList<Packet> toSend = new LinkedList<Packet>();
    			Disguise disguise = disguiseDB.get(player.getName());
    			toSend.add(disguise.packetGenerator.getEntityDestroyPacket());
    			if (disguise.type.isPlayer() && !(pluginSettings.noTabHide && protocolHook == ProtocolHook.ProtocolLib)) {
    				toSend.add(disguise.packetGenerator.getPlayerInfoPacket(player, false));
    			}
    	    	
    	    	for (Player observer : world.getPlayers()) {
    	    		if (observer != player) {
    	    			sendPacketsToObserver(observer, toSend);
    					observer.showPlayer(player);
    	    		}
    	    	}
    		}
    	};
    	
    	if (getServer().isPrimaryThread()) {
    		undisguiseExec.run();
    	} else {
    		getServer().getScheduler().runTask(this, undisguiseExec);
    	}
    }
    
    public void undisguiseToPlayer(final Player player, final Player observer) {
    	Runnable undisguiseExec = new Runnable() {
    		@Override
    		public void run() {
    			LinkedList<Packet> toSend = new LinkedList<Packet>();
    			Disguise disguise = disguiseDB.get(player.getName());
    			toSend.add(disguise.packetGenerator.getEntityDestroyPacket());
    			if (disguise.type.isPlayer() && !(pluginSettings.noTabHide && protocolHook == ProtocolHook.ProtocolLib)) {
    				toSend.add(disguise.packetGenerator.getPlayerInfoPacket(player, false));
    			}
    	    	
    	    	
    			sendPacketsToObserver(observer, toSend);
    			observer.showPlayer(player);
    		}
    	};
    	
    	if (getServer().isPrimaryThread()) {
    		undisguiseExec.run();
    	} else {
    		getServer().getScheduler().runTask(this, undisguiseExec);
    	}
    }
    
    public void dropDisguiseToWorld(final Player player, final World world, final DroppedDisguise disguise) {
    	Runnable undisguiseExec = new Runnable() {
    		@Override
    		public void run() {
    			LinkedList<Packet> toSend = new LinkedList<Packet>();
    			if (disguise.type.isPlayer()) {
    				toSend.add(disguise.packetGenerator.getPlayerInfoPacket(player, false));
    			}
    			
    			for (Player observer : world.getPlayers()) {
    	    		if (observer != player) {
    	    			sendPacketsToObserver(observer, toSend);
    					observer.showPlayer(player);
    	    		}
    	    	}
    			
    			// See own dropped disguise
    			sendPacketsToObserver(player, disguise.getSpawnPackets(player, null));
    		}
    	};
    	
    	if (getServer().isPrimaryThread()) {
    		undisguiseExec.run();
    	} else {
    		getServer().getScheduler().runTask(this, undisguiseExec);
    	}
    }
    
    public void showWorldDisguises(Player observer) {
    	for (String disguisedName : disguiseDB.keySet()) {
			Player disguised = getServer().getPlayer(disguisedName);
			if (disguised != null && disguised != observer) {
				if (disguised.getWorld() == observer.getWorld()) {
					disguiseToPlayer(disguised, observer);

					if (pluginSettings.noTabHide && protocolHook == ProtocolHook.ProtocolLib) {
						((CraftPlayer) observer).getHandle().playerConnection.sendPacket(new PacketPlayOutPlayerInfo(disguisedName, true, ((CraftPlayer) disguised).getHandle().ping));
					}
				}
			}
		}
    }
    
    public void resetWorldDisguises(Player observer) {
    	for (String disguisedName : disguiseDB.keySet()) {
			Player disguised = getServer().getPlayer(disguisedName);
			if (disguised != null && disguised != observer) {
				if (disguised.getWorld() == observer.getWorld()) {
					undisguiseToPlayer(disguised, observer);
					disguiseToPlayer(disguised, observer);
				}
			}
		}
    }
    
    public void setPositionUpdater(Player player, Disguise disguise) {
    	if (DisguiseCraft.pluginSettings.movementUpdateThreading) {
    		positionUpdaters.put(player, getServer().getScheduler().scheduleSyncRepeatingTask(this, new DCPlayerPositionUpdater(this, player, disguise), 1, pluginSettings.movementUpdateFrequency));
    	}
    }
    
    public void removePositionUpdater(Player player) {
    	if (DisguiseCraft.pluginSettings.movementUpdateThreading) {
			if (positionUpdaters.containsKey(player)) {
				getServer().getScheduler().cancelTask(positionUpdaters.get(player));
			}
		}
    }
}
