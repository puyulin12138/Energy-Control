package com.zuxelus.energycontrol.tileentities;

import com.zuxelus.energycontrol.EnergyControl;
import com.zuxelus.energycontrol.api.CardState;
import com.zuxelus.energycontrol.blocks.RangeTrigger;
import com.zuxelus.energycontrol.containers.ISlotItemFilter;
import com.zuxelus.energycontrol.items.ItemUpgrade;
import com.zuxelus.energycontrol.items.cards.ItemCardMain;
import com.zuxelus.energycontrol.items.cards.ItemCardReader;
import com.zuxelus.energycontrol.items.cards.ItemCardType;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class TileEntityRangeTrigger extends TileEntityInventory implements ITickable, ISlotItemFilter, ITilePacketHandler {

	public static final int SLOT_CARD = 0;
	public static final int SLOT_UPGRADE = 1;
	private static final int LOCATION_RANGE = 8;

	private static final int STATE_UNKNOWN = 0;
	private static final int STATE_PASSIVE = 1;
	private static final int STATE_ACTIVE = 2;

	protected int updateTicker;
	protected int tickRate;
	protected boolean init;

	private int prevStatus;
	private int status;
	private boolean poweredBlock;

	private boolean prevInvertRedstone;
	private boolean invertRedstone;

	private double prevLevelStart;
	public double levelStart;

	private double prevLevelEnd;
	public double levelEnd;
	
	public TileEntityRangeTrigger() {
		super("tile.range_trigger.name");
		init = false;
		tickRate = EnergyControl.config.rangeTriggerRefreshPeriod;
		updateTicker = tickRate;
		status = prevStatus = 0;
		prevInvertRedstone = invertRedstone = false;
		levelStart = 10000000;
		levelEnd = 9000000;
	}

	public boolean getInvertRedstone() {
		return invertRedstone;
	}

	public void setInvertRedstone(boolean value) {
		invertRedstone = value;
		if (!world.isRemote && prevInvertRedstone != invertRedstone)
			notifyBlockUpdate();
		prevInvertRedstone = invertRedstone;
	}

	public void setStatus(int value) {
		status = value;
		if (!world.isRemote && prevStatus != status) {
			IBlockState iblockstate = world.getBlockState(pos);
			Block block = iblockstate.getBlock();
			if (block instanceof RangeTrigger) {
				IBlockState newState = block.getDefaultState()
						.withProperty(((RangeTrigger) block).FACING, iblockstate.getValue(((RangeTrigger) block).FACING))
						.withProperty(((RangeTrigger) block).STATE, RangeTrigger.EnumState.getState(status));
				world.setBlockState(pos, newState, 3);
			}
			notifyBlockUpdate();
		}
		prevStatus = status;
	}

	public void setLevelStart(double start) {
		levelStart = start;
		if (prevLevelStart != levelStart)
			notifyBlockUpdate();
		prevLevelStart = levelStart;
	}

	public void setLevelEnd(double end) {
		levelEnd = end;
		if (prevLevelEnd != levelEnd)
			notifyBlockUpdate();
		prevLevelEnd = levelEnd;
	}

	public int getStatus() {
		return status;
	}

	@Override
	public void update() {
		if (!world.isRemote) {
			if (updateTicker-- > 0)
				return;
			updateTicker = tickRate;
			markDirty();
		}
	}

	@Override
	public void onServerMessageReceived(NBTTagCompound tag) {
		if (!tag.hasKey("type"))
			return;
		switch (tag.getInteger("type")) {
		case 1:
			if (tag.hasKey("value"))
				setLevelStart(tag.getDouble("value"));
			break;
		case 2:
			if (tag.hasKey("value"))
				setInvertRedstone(tag.getInteger("value") == 1);
			break;
		case 3:
			if (tag.hasKey("value"))
				setLevelEnd(tag.getDouble("value"));
			break;
		}
	}

	@Override
	public SPacketUpdateTileEntity getUpdatePacket() {
		NBTTagCompound tag = new NBTTagCompound();
		tag = writeProperties(tag);
		return new SPacketUpdateTileEntity(getPos(), 0, tag);
	}

	@Override
	public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
		readProperties(pkt.getNbtCompound());
	}
	
	@Override
	public NBTTagCompound getUpdateTag() {
		NBTTagCompound tag = super.getUpdateTag();
		tag = writeProperties(tag);
		return tag;
	}

	@Override
	protected void readProperties(NBTTagCompound tag) {
		super.readProperties(tag);
		prevInvertRedstone = invertRedstone = tag.getBoolean("invert");
		levelStart = tag.getDouble("levelStart");
		levelEnd = tag.getDouble("levelEnd");
	}

	@Override
	public void readFromNBT(NBTTagCompound tag) {
		super.readFromNBT(tag);
		readProperties(tag);
	}

	@Override
	protected NBTTagCompound writeProperties(NBTTagCompound tag) {
		tag = super.writeProperties(tag);
		tag.setBoolean("invert", getInvertRedstone());
		tag.setDouble("levelStart", levelStart);
		tag.setDouble("levelEnd", levelEnd);
		return tag;
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tag) {
		return writeProperties(super.writeToNBT(tag));
	}

	@Override
	public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newSate) {
		return oldState.getBlock() != newSate.getBlock();
	}

	@Override
	public void markDirty() {
		super.markDirty();
		if (world == null || world.isRemote)
			return;
		
		int status = STATE_UNKNOWN;
		ItemStack card = getStackInSlot(SLOT_CARD);
		if (!card.isEmpty()) {
			Item item = card.getItem();
			if (item instanceof ItemCardMain) {
				ItemCardReader reader = new ItemCardReader(card);
				CardState state = ItemCardMain.updateCardNBT(world, pos, reader, getStackInSlot(SLOT_UPGRADE));
				if (state == CardState.OK) {
					double min = Math.min(levelStart, levelEnd);
					double max = Math.max(levelStart, levelEnd);
					double cur = reader.getDouble("energy");

					if (cur > max) {
						status = STATE_ACTIVE;
					} else if (cur < min) {
						status = STATE_PASSIVE;
					} else if (status == STATE_UNKNOWN) {
						status = STATE_PASSIVE;
					} else
						status = STATE_PASSIVE;
				} else
					status = STATE_UNKNOWN;
			}
		}
		setStatus(status);
	}

	public void notifyBlockUpdate() {
		IBlockState iblockstate = world.getBlockState(pos);
		Block block = iblockstate.getBlock();
		if (!(block instanceof RangeTrigger))
			return;
		boolean newValue = status == 2 ? !invertRedstone : invertRedstone;
		if (poweredBlock != newValue) {
			((RangeTrigger) block).setPowered(status == 2 ? !invertRedstone : invertRedstone);
			world.notifyNeighborsOfStateChange(pos, block, false);
		}
		poweredBlock = newValue;
		world.notifyBlockUpdate(pos, iblockstate, iblockstate, 2);
	}

	// Inventory
	@Override
	public int getSizeInventory() {
		return 2;
	}

	@Override
	public boolean isItemValidForSlot(int index, ItemStack stack) {
		return isItemValid(index, stack);
	}

	@Override
	public boolean isItemValid(int slotIndex, ItemStack itemstack) { // ISlotItemFilter
		switch (slotIndex) {
		case SLOT_CARD:
			return itemstack.getItem() instanceof ItemCardMain && (itemstack.getItemDamage() == ItemCardType.CARD_ENERGY
					|| itemstack.getItemDamage() == ItemCardType.CARD_ENERGY_ARRAY);
		default:
			return itemstack.getItem() instanceof ItemUpgrade && itemstack.getItemDamage() == ItemUpgrade.DAMAGE_RANGE;
		}
	}
}
