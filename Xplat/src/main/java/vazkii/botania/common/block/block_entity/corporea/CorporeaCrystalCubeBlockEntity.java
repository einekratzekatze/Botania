/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.common.block.block_entity.corporea;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import vazkii.botania.api.block.Wandable;
import vazkii.botania.api.corporea.CorporeaHelper;
import vazkii.botania.api.corporea.CorporeaRequestMatcher;
import vazkii.botania.api.corporea.CorporeaRequestor;
import vazkii.botania.api.corporea.CorporeaSpark;
import vazkii.botania.api.internal.VanillaPacketDispatcher;
import vazkii.botania.common.block.block_entity.BotaniaBlockEntities;

import java.util.List;

public class CorporeaCrystalCubeBlockEntity extends BaseCorporeaBlockEntity implements CorporeaRequestor, Wandable {
	private static final String TAG_REQUEST_TARGET = "requestTarget";
	private static final String TAG_ITEM_COUNT = "itemCount";
	private static final String TAG_LOCK = "lock";

	private ItemStack requestTarget = ItemStack.EMPTY;
	private int itemCount = 0;
	private int ticks = 0;
	private int compValue = 0;
	public boolean locked = false;

	public CorporeaCrystalCubeBlockEntity(BlockPos pos, BlockState state) {
		super(BotaniaBlockEntities.CORPOREA_CRYSTAL_CUBE, pos, state);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, CorporeaCrystalCubeBlockEntity self) {
		++self.ticks;
		if (self.ticks % 20 == 0) {
			self.updateCount();
		}
	}

	public void setRequestTarget(ItemStack stack) {
		if (!stack.isEmpty() && !locked) {
			ItemStack copy = stack.copy();
			copy.setCount(1);
			requestTarget = copy;
			updateCount();
			if (!level.isClientSide) {
				VanillaPacketDispatcher.dispatchTEToNearbyPlayers(this);
			}
		}

	}

	public ItemStack getRequestTarget() {
		return requestTarget;
	}

	public int getItemCount() {
		return itemCount;
	}

	public void doRequest(Player player) {
		if (level.isClientSide) {
			return;
		}

		CorporeaSpark spark = getSpark();
		if (spark != null && spark.getMaster() != null && !requestTarget.isEmpty()) {
			int count = player.isShiftKeyDown() ? requestTarget.getMaxStackSize() : 1;
			var matcher = CorporeaHelper.instance().createMatcher(requestTarget, true);
			doCorporeaRequest(matcher, count, spark, player);
		}
	}

	private void updateCount() {
		if (level.isClientSide) {
			return;
		}

		int sum = 0;
		CorporeaSpark spark = getSpark();
		if (spark != null && spark.getMaster() != null && !requestTarget.isEmpty()) {
			var matcher = CorporeaHelper.instance().createMatcher(requestTarget, true);
			List<ItemStack> stacks = CorporeaHelper.instance().requestItem(matcher, -1, spark, null, false).stacks();
			for (ItemStack stack : stacks) {
				sum += stack.getCount();
			}
		}

		setCount(sum);
	}

	private void setCount(int count) {
		int oldCount = this.itemCount;
		this.itemCount = count;
		if (this.itemCount != oldCount) {
			int oldCompValue = this.compValue;
			this.compValue = CorporeaHelper.instance().signalStrengthForRequestSize(itemCount);
			if (this.compValue != oldCompValue && this.level != null) {
				this.level.updateNeighbourForOutputSignal(this.worldPosition, getBlockState().getBlock());
			}
			VanillaPacketDispatcher.dispatchTEToNearbyPlayers(this);
		}
	}

	@Override
	public void writePacketNBT(CompoundTag tag) {
		super.writePacketNBT(tag);
		CompoundTag cmp = new CompoundTag();
		if (!requestTarget.isEmpty()) {
			cmp = requestTarget.save(cmp);
		}
		tag.put(TAG_REQUEST_TARGET, cmp);
		tag.putInt(TAG_ITEM_COUNT, itemCount);
		tag.putBoolean(TAG_LOCK, locked);
	}

	@Override
	public void readPacketNBT(CompoundTag tag) {
		super.readPacketNBT(tag);
		CompoundTag cmp = tag.getCompound(TAG_REQUEST_TARGET);
		requestTarget = ItemStack.of(cmp);
		setCount(tag.getInt(TAG_ITEM_COUNT));
		locked = tag.getBoolean(TAG_LOCK);
	}

	public int getComparatorValue() {
		return compValue;
	}

	@Override
	public void doCorporeaRequest(CorporeaRequestMatcher request, int count, CorporeaSpark spark, @Nullable LivingEntity entity) {
		if (!requestTarget.isEmpty()) {
			List<ItemStack> stacks = CorporeaHelper.instance().requestItem(request, count, spark, entity, true).stacks();
			spark.onItemsRequested(stacks);
			boolean did = false;
			int sum = 0;
			for (ItemStack reqStack : stacks) {
				ItemEntity item = new ItemEntity(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.5, worldPosition.getZ() + 0.5, reqStack);
				level.addFreshEntity(item);
				if (requestTarget.sameItem(reqStack)) {
					sum += reqStack.getCount();
					did = true;
				}
			}

			if (did) {
				setCount(getItemCount() - sum);
			}
		}
	}

	@Override
	public boolean onUsedByWand(@Nullable Player player, ItemStack stack, Direction side) {
		if (player == null || player.isShiftKeyDown()) {
			this.locked = !this.locked;
			if (!level.isClientSide) {
				VanillaPacketDispatcher.dispatchTEToNearbyPlayers(this);
			}
			return true;
		}
		return false;
	}

	public static class Hud {
		public static void render(PoseStack ps, CorporeaCrystalCubeBlockEntity cube) {
			Minecraft mc = Minecraft.getInstance();
			ProfilerFiller profiler = mc.getProfiler();

			profiler.push("crystalCube");
			ItemStack target = cube.getRequestTarget();
			if (!target.isEmpty()) {
				String nameStr = target.getHoverName().getString();
				String countStr = cube.getItemCount() + "x";
				String lockedStr = I18n.get("botaniamisc.locked");

				int strlen = Math.max(mc.font.width(nameStr), mc.font.width(countStr));
				if (cube.locked) {
					strlen = Math.max(strlen, mc.font.width(lockedStr));
				}

				int w = mc.getWindow().getGuiScaledWidth();
				int h = mc.getWindow().getGuiScaledHeight();
				int boxH = h / 2 + (cube.locked ? 20 : 10);
				GuiComponent.fill(ps, w / 2 + 8, h / 2 - 12, w / 2 + strlen + 32, boxH, 0x44000000);
				GuiComponent.fill(ps, w / 2 + 6, h / 2 - 14, w / 2 + strlen + 34, boxH + 2, 0x44000000);

				mc.font.drawShadow(ps, nameStr, w / 2.0F + 30, h / 2.0F - 10, 0x6666FF);
				mc.font.drawShadow(ps, countStr, w / 2.0F + 30, h / 2.0F, 0xFFFFFF);
				if (cube.locked) {
					mc.font.drawShadow(ps, lockedStr, w / 2.0F + 30, h / 2.0F + 10, 0xFFAA00);
				}
				mc.getItemRenderer().renderAndDecorateItem(target, w / 2 + 10, h / 2 - 10);
			}

			profiler.pop();
		}
	}
}
