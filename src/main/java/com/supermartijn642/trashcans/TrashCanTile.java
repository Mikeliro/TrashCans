package com.supermartijn642.trashcans;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Items;
import net.minecraft.item.ItemBucket;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;

/**
 * Created 7/10/2020 by SuperMartijn642
 */
public class TrashCanTile extends TileEntity implements ITickable {

    public static final int DEFAULT_ENERGY_LIMIT = 10000, MAX_ENERGY_LIMIT = 10000000, MIN_ENERGY_LIMIT = 100;

    public final IItemHandler ITEM_HANDLER = new IItemHandlerModifiable() {
        @Override
        public void setStackInSlot(int slot, @Nonnull ItemStack stack){
        }

        @Override
        public int getSlots(){
            return 1;
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot){
            return ItemStack.EMPTY;
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate){
            for(ItemStack filter : TrashCanTile.this.itemFilter){
                if(!filter.isEmpty() && ItemStack.areItemsEqual(stack, filter))
                    return TrashCanTile.this.itemFilterWhitelist ? ItemStack.EMPTY : stack;
            }
            return TrashCanTile.this.itemFilterWhitelist ? stack : ItemStack.EMPTY;
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate){
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot){
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack){
            for(ItemStack filter : TrashCanTile.this.itemFilter){
                if(!filter.isEmpty() && ItemStack.areItemsEqual(stack, filter))
                    return TrashCanTile.this.itemFilterWhitelist;
            }
            return !TrashCanTile.this.itemFilterWhitelist;
        }
    };

    public final IFluidHandler FLUID_HANDLER = new IFluidHandler() {
        @Override
        public IFluidTankProperties[] getTankProperties(){
            return new IFluidTankProperties[]{new IFluidTankProperties() {
                @Nullable
                @Override
                public FluidStack getContents(){
                    return null;
                }

                @Override
                public int getCapacity(){
                    return Integer.MAX_VALUE;
                }

                @Override
                public boolean canFill(){
                    return true;
                }

                @Override
                public boolean canDrain(){
                    return false;
                }

                @Override
                public boolean canFillFluidType(FluidStack fluidStack){
                    if(fluidStack == null)
                        return true;
                    for(FluidStack filter : TrashCanTile.this.liquidFilter){
                        if(filter != null && filter.isFluidEqual(fluidStack) && FluidStack.areFluidStackTagsEqual(fluidStack, filter))
                            return TrashCanTile.this.liquidFilterWhitelist;
                    }
                    return !TrashCanTile.this.liquidFilterWhitelist;
                }

                @Override
                public boolean canDrainFluidType(FluidStack fluidStack){
                    return false;
                }
            }};
        }

        @Override
        public int fill(FluidStack resource, boolean doFill){
            for(FluidStack filter : TrashCanTile.this.liquidFilter){
                if(filter != null && filter.isFluidEqual(resource) && FluidStack.areFluidStackTagsEqual(resource, filter))
                    return TrashCanTile.this.liquidFilterWhitelist ? resource.amount : 0;
            }
            return TrashCanTile.this.liquidFilterWhitelist ? 0 : resource.amount;
        }

        @Nullable
        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain){
            return null;
        }

        @Nullable
        @Override
        public FluidStack drain(int maxDrain, boolean doDrain){
            return null;
        }
    };
    public final IItemHandler LIQUID_ITEM_HANDLER = new IItemHandlerModifiable() {
        @Override
        public void setStackInSlot(int slot, @Nonnull ItemStack stack){
            TrashCanTile.this.liquidItem = stack;
        }

        @Override
        public int getSlots(){
            return 1;
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot){
            return TrashCanTile.this.liquidItem;
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate){
            if(!this.isItemValid(slot, stack) || !TrashCanTile.this.liquidItem.isEmpty() || stack.isEmpty())
                return stack;
            if(!simulate){
                TrashCanTile.this.liquidItem = stack.copy();
                TrashCanTile.this.liquidItem.setCount(1);
                TrashCanTile.this.dataChanged();
            }
            ItemStack stack1 = stack.copy();
            stack1.shrink(1);
            return stack1;
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate){
            if(amount <= 0 || TrashCanTile.this.liquidItem.isEmpty())
                return ItemStack.EMPTY;
            ItemStack stack = TrashCanTile.this.liquidItem.copy();
            stack.setCount(Math.min(amount, stack.getCount()));
            if(!simulate){
                TrashCanTile.this.liquidItem.shrink(amount);
                TrashCanTile.this.dataChanged();
            }
            return stack;
        }

        @Override
        public int getSlotLimit(int slot){
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack){
            if(!stack.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null))
                return false;
            IFluidHandlerItem handler = stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
            if(handler == null)
                return false;
            IFluidTankProperties[] properties = handler.getTankProperties();
            if(properties == null)
                return false;
            for(IFluidTankProperties property : properties)
                if(property != null && property.canDrain() && property.getContents() != null && property.getContents().amount > 0)
                    return true;
            return false;
        }
    };

    public final IEnergyStorage ENERGY_STORAGE = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate){
            return TrashCanTile.this.useEnergyLimit ? Math.min(maxReceive, TrashCanTile.this.energyLimit) : maxReceive;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate){
            return 0;
        }

        @Override
        public int getEnergyStored(){
            return 0;
        }

        @Override
        public int getMaxEnergyStored(){
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean canExtract(){
            return false;
        }

        @Override
        public boolean canReceive(){
            return true;
        }
    };
    public final IItemHandler ENERGY_ITEM_HANDLER = new IItemHandlerModifiable() {
        @Override
        public void setStackInSlot(int slot, @Nonnull ItemStack stack){
            TrashCanTile.this.energyItem = stack;
        }

        @Override
        public int getSlots(){
            return 1;
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot){
            return TrashCanTile.this.energyItem;
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate){
            if(!this.isItemValid(slot, stack) || !TrashCanTile.this.energyItem.isEmpty() || stack.isEmpty())
                return stack;
            if(!simulate){
                TrashCanTile.this.energyItem = stack.copy();
                TrashCanTile.this.energyItem.setCount(1);
                TrashCanTile.this.dataChanged();
            }
            ItemStack stack1 = stack.copy();
            stack1.shrink(1);
            return stack1;
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate){
            if(amount <= 0 || TrashCanTile.this.energyItem.isEmpty())
                return ItemStack.EMPTY;
            ItemStack stack = TrashCanTile.this.energyItem.copy();
            stack.setCount(Math.min(amount, stack.getCount()));
            if(!simulate){
                TrashCanTile.this.energyItem.shrink(amount);
                TrashCanTile.this.dataChanged();
            }
            return stack;
        }

        @Override
        public int getSlotLimit(int slot){
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack){
            if(!stack.hasCapability(CapabilityEnergy.ENERGY, null))
                return false;
            IEnergyStorage storage = stack.getCapability(CapabilityEnergy.ENERGY, null);
            return storage != null && storage.canExtract() && storage.getEnergyStored() > 0;
        }
    };

    public static class ItemTrashCanTile extends TrashCanTile {
        public ItemTrashCanTile(){
            super(true, false, false);
        }
    }

    public static class LiquidTrashCanTile extends TrashCanTile {
        public LiquidTrashCanTile(){
            super(false, true, false);
        }
    }

    public static class EnergyTrashCanTile extends TrashCanTile {
        public EnergyTrashCanTile(){
            super(false, false, true);
        }
    }

    public static class UltimateTrashCanTile extends TrashCanTile {
        public UltimateTrashCanTile(){
            super(true, true, true);
        }
    }

    public final boolean items;
    public final ArrayList<ItemStack> itemFilter = new ArrayList<>();
    public boolean itemFilterWhitelist = false;
    public final boolean liquids;
    public final ArrayList<FluidStack> liquidFilter = new ArrayList<>();
    public boolean liquidFilterWhitelist = false;
    public ItemStack liquidItem = ItemStack.EMPTY;
    public final boolean energy;
    public int energyLimit = DEFAULT_ENERGY_LIMIT;
    public boolean useEnergyLimit = false;
    public ItemStack energyItem = ItemStack.EMPTY;

    private boolean dataChanged = false;

    public TrashCanTile(boolean items, boolean liquids, boolean energy){
        super();
        this.items = items;
        this.liquids = liquids;
        this.energy = energy;

        for(int i = 0; i < 9; i++){
            this.itemFilter.add(ItemStack.EMPTY);
            this.liquidFilter.add(null);
        }
    }

    @Override
    public void update(){
        if(this.liquids && !this.liquidItem.isEmpty() && this.liquidItem.getItem() != Items.BUCKET){
            if(this.liquidItem.getItem() instanceof ItemBucket)
                this.liquidItem = new ItemStack(Items.BUCKET);
            else if(this.liquidItem.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)){
                IFluidHandlerItem fluidHandler = this.liquidItem.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
                if(fluidHandler != null){
                    IFluidTankProperties[] properties = fluidHandler.getTankProperties();
                    if(properties != null){
                        boolean changed = false;
                        for(IFluidTankProperties property : properties)
                            if(property.getContents() != null && property.canDrain() && property.getContents().amount > 0){
                                fluidHandler.drain(property.getContents(), true);
                                changed = true;
                            }
                        if(changed)
                            TrashCanTile.this.dataChanged();
                    }
                }
            }
        }
        if(this.energy && !this.energyItem.isEmpty() && this.energyItem.hasCapability(CapabilityEnergy.ENERGY, null)){
            IEnergyStorage energyStorage = this.energyItem.getCapability(CapabilityEnergy.ENERGY, null);
            if(energyStorage != null && energyStorage.canExtract() && energyStorage.getEnergyStored() > 0){
                energyStorage.extractEnergy(energyStorage.getEnergyStored(), false);
                this.dataChanged();
            }
        }
    }

    @Override
    public boolean hasCapability(Capability<?> cap, @Nullable EnumFacing facing){
        return (this.items && cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) ||
            (this.liquids && cap == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) ||
            (this.energy && cap == CapabilityEnergy.ENERGY) ||
            super.hasCapability(cap, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(Capability<T> cap, @Nullable EnumFacing facing){
        if(this.items && cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(ITEM_HANDLER);
        if(this.liquids && cap == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY)
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(FLUID_HANDLER);
        if(this.energy && cap == CapabilityEnergy.ENERGY)
            return CapabilityEnergy.ENERGY.cast(ENERGY_STORAGE);
        return super.getCapability(cap, facing);
    }

    public void dataChanged(){
        if(this.world.isRemote)
            return;
        this.dataChanged = true;
        IBlockState blockState = this.world.getBlockState(this.pos);
        this.world.notifyBlockUpdate(this.pos, blockState, blockState, 2);
    }

    private NBTTagCompound getChangedData(){
        if(this.dataChanged){
            this.dataChanged = false;
            return this.getData();
        }
        return null;
    }

    private NBTTagCompound getData(){
        NBTTagCompound tag = new NBTTagCompound();
        if(this.items){
            for(int i = 0; i < this.itemFilter.size(); i++)
                tag.setTag("itemFilter" + i, this.itemFilter.get(i).writeToNBT(new NBTTagCompound()));
            tag.setBoolean("itemFilterWhitelist", this.itemFilterWhitelist);
        }
        if(this.liquids){
            for(int i = 0; i < this.liquidFilter.size(); i++)
                if(this.liquidFilter.get(i) != null)
                    tag.setTag("liquidFilter" + i, this.liquidFilter.get(i).writeToNBT(new NBTTagCompound()));
            tag.setBoolean("liquidFilterWhitelist", this.liquidFilterWhitelist);
            if(!this.liquidItem.isEmpty())
                tag.setTag("liquidItem", this.liquidItem.writeToNBT(new NBTTagCompound()));
        }
        if(this.energy){
            tag.setBoolean("useEnergyLimit", this.useEnergyLimit);
            tag.setInteger("energyLimit", this.energyLimit);
            if(!this.energyItem.isEmpty())
                tag.setTag("energyItem", this.energyItem.writeToNBT(new NBTTagCompound()));
        }
        return tag;
    }

    private void handleData(NBTTagCompound tag){
        if(this.items){
            for(int i = 0; i < this.itemFilter.size(); i++)
                this.itemFilter.set(i, tag.hasKey("itemFilter" + i) ? new ItemStack(tag.getCompoundTag("itemFilter" + i)) : ItemStack.EMPTY);
            this.itemFilterWhitelist = tag.hasKey("itemFilterWhitelist") && tag.getBoolean("itemFilterWhitelist");
        }
        if(this.liquids){
            for(int i = 0; i < this.liquidFilter.size(); i++)
                this.liquidFilter.set(i, tag.hasKey("liquidFilter" + i) ? FluidStack.loadFluidStackFromNBT(tag.getCompoundTag("liquidFilter" + i)) : null);
            this.liquidFilterWhitelist = tag.hasKey("liquidFilterWhitelist") && tag.getBoolean("liquidFilterWhitelist");
            this.liquidItem = tag.hasKey("liquidItem") ? new ItemStack(tag.getCompoundTag("liquidItem")) : ItemStack.EMPTY;
        }
        if(this.energy){
            this.useEnergyLimit = tag.hasKey("useEnergyLimit") && tag.getBoolean("useEnergyLimit");
            this.energyLimit = tag.hasKey("energyLimit") ? tag.getInteger("energyLimit") : DEFAULT_ENERGY_LIMIT;
            this.energyItem = tag.hasKey("energyItem") ? new ItemStack(tag.getCompoundTag("energyItem")) : ItemStack.EMPTY;
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound){
        super.writeToNBT(compound);
        compound.setTag("data", this.getData());
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound){
        super.readFromNBT(compound);
        this.handleData(compound.getCompoundTag("data"));
    }

    @Override
    public NBTTagCompound getUpdateTag(){
        NBTTagCompound tag = super.getUpdateTag();
        tag.setTag("data", this.getData());
        return tag;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag){
        super.handleUpdateTag(tag);
        this.handleData(tag.getCompoundTag("data"));
    }

    @Nullable
    @Override
    public SPacketUpdateTileEntity getUpdatePacket(){
        NBTTagCompound tag = this.getChangedData();
        return tag == null || tag.hasNoTags() ? null : new SPacketUpdateTileEntity(this.pos, 0, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt){
        this.handleData(pkt.getNbtCompound());
    }
}