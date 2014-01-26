package pgDev.bukkit.DisguiseCraft.listeners.protocol;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;

import net.minecraft.server.v1_7_R1.EnumEntityUseAction;

import org.bukkit.entity.Player;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketAdapter.AdapterParameteters;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.FieldAccessException;

import pgDev.bukkit.DisguiseCraft.*;
import pgDev.bukkit.DisguiseCraft.listeners.PlayerInvalidInteractEvent;

public class PLPacketListener {
	final DisguiseCraft plugin;
	ProtocolManager pM = DisguiseCraft.protocolManager;
	
	public ConcurrentLinkedQueue<String> recentlyDisguised;
	
	public PLPacketListener(final DisguiseCraft plugin) {
		this.plugin = plugin;
	}
	
	public void setupAttackListener() {
		AdapterParameteters ap = PacketAdapter.params();
		ap.plugin(plugin);
		ap.clientSide();
		ap.types(PacketType.Play.Client.USE_ENTITY);
		
		pM.addPacketListener(new PacketAdapter(ap) {
			    @Override
			    public void onPacketReceiving(PacketEvent event) {
			    	Player player = event.getPlayer();
			        if (event.getPacketType() == PacketType.Play.Client.USE_ENTITY) {
			            try {
			            	PacketContainer packet = event.getPacket();
			                int target = packet.getSpecificModifier(int.class).read(0);
			                String action = packet.getSpecificModifier(EnumEntityUseAction.class).read(0).name();
			                
			                if (packet.getEntityModifier(player.getWorld()).read(0) == null) {
			                	PlayerInvalidInteractEvent newEvent = new PlayerInvalidInteractEvent(player, target, action);
			                    plugin.getServer().getPluginManager().callEvent(newEvent);
			                }
			            } catch (FieldAccessException e) {
			                DisguiseCraft.log.log(Level.SEVERE, "Couldn't access a field in a UseEntity packet!", e);
			            }
			        }
			    }
		});
	}
	
	public void setupTabListListener() {
		// Make database
		recentlyDisguised = new ConcurrentLinkedQueue<String>();
		
		// Set up listener
		AdapterParameteters ap = PacketAdapter.params();
		ap.plugin(plugin);
		ap.serverSide();
		ap.types(PacketType.Play.Server.PLAYER_INFO);
		
		pM.addPacketListener(new PacketAdapter(ap) {
			    @Override
			    public void onPacketSending(PacketEvent event) {
			        if (event.getPacketType() == PacketType.Play.Server.PLAYER_INFO) {
			        	try {
				        	if (recentlyDisguised.remove(event.getPacket().getStrings().read(0))) {
				        		event.setCancelled(true);
				        	}
			        	} catch (FieldAccessException e) {
			                DisguiseCraft.log.log(Level.SEVERE, "Couldn't access a field in a PlayerInfo packet!", e);
			            }
			        }
			    }
		});
	}
}
