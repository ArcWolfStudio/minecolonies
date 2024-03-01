package com.minecolonies.coremod.client.gui.containers;

import com.minecolonies.coremod.colony.buildings.views.AbstractBuildingView;
import net.dries007.tfc.client.screen.PotScreen;
import net.dries007.tfc.common.container.PotContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class WindowPotCrafting extends PotScreen {

    private final AbstractBuildingView building;

    public WindowPotCrafting(PotContainer container, Inventory playerInventory, Component name, AbstractBuildingView building) {
        super(container, playerInventory, name);
        this.building = building;
    }

    public AbstractBuildingView getBuildingView()
    {
        return building;
    }
}
