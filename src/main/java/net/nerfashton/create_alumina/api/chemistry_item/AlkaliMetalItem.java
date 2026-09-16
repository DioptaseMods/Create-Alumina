package net.nerfashton.create_alumina.api.chemistry_item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.WeakHashMap;

public class AlkaliMetalItem extends Item {
    private final WeakHashMap<ItemEntity, Integer> waterTicks = new WeakHashMap<>();

    public AlkaliMetalItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        tooltipComponents.add(Component.literal("Highly reactive alkali metal.")
                .withStyle(ChatFormatting.GOLD));
        tooltipComponents.add(Component.literal("Explodes vigorously on contact with water!")
                .withStyle(ChatFormatting.RED));
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity itemEntity) {
        Level level = itemEntity.level();

        if(itemEntity.isInWater()) {
            if (level.isClientSide()) {
                double offsetX = (Math.random() - 0.5) * 0.5;
                double offsetY = Math.random() * 0.5;
                double offsetZ = (Math.random() - 0.5) * 0.5;

                for (int i = 0; i < 3; i++) {
                    level.addParticle(ParticleTypes.BUBBLE,
                            itemEntity.getX() + offsetX,
                            itemEntity.getY() + offsetY,
                            itemEntity.getZ() + offsetZ,
                            0.0D, 0.1D, 0.0D);
                }

            } else {
                int ticks = waterTicks.getOrDefault(itemEntity, 0) + 1;
                waterTicks.put(itemEntity, ticks);
                if  (ticks == 1) {
                    double speed = 0.5;
                    double moveX = (Math.random() - 0.5) * speed;
                    double moveZ = (Math.random() - 0.5) * speed;

                    itemEntity.setDeltaMovement(moveX, 0, moveZ);
                    itemEntity.hasImpulse = true;
                }

                float explosionSize = (float)(0.1 * itemEntity.getItem().getCount());
                if (ticks >= 20) {
                    itemEntity.discard();
                    level.explode(null, itemEntity.getX(), itemEntity.getY(), itemEntity.getZ(),
                            explosionSize, Level.ExplosionInteraction.NONE);
                }
            }
        }
        return false;
    }
}
