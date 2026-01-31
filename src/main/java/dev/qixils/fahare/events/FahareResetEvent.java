package dev.qixils.fahare.events;

import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class FahareResetEvent extends Event {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    
    private final World fakeOverworld;

    public FahareResetEvent(World fakeOverworld) {
        this.fakeOverworld = fakeOverworld;
    }

    public World getWorld() {
        return this.fakeOverworld;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }
}