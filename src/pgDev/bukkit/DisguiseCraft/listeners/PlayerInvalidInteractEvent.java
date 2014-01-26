package pgDev.bukkit.DisguiseCraft.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

public class PlayerInvalidInteractEvent extends PlayerEvent {
    private static final HandlerList handlers = new HandlerList();
    private final int target;
    private final boolean action;

    public PlayerInvalidInteractEvent(final Player player, final int target, final String action) {
        super(player);
        this.target = target;
        
        if (action.equalsIgnoreCase("ATTACK")) {
        	this.action = true;
        } else if (action.equalsIgnoreCase("INTERACT")) {
        	this.action = false;
        } else {
        	throw new IllegalArgumentException("Unidentified action type");
        }
    }

    /**
     * Gets the entity ID that the player interacted with
     *
     * @return Entity ID of the target
     */
    public int getTarget() {
        return target;
    }

    /**
     * Gets whether the player left- or right-clicked
     *
     * @return True is left-click, False is right-click
     */
    public boolean getAction() {
        return action;
    }

	@Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}