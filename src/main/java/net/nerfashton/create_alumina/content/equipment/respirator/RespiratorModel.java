package net.nerfashton.create_alumina.content.equipment.respirator;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.nerfashton.create_alumina.CreateAlumina;

public class RespiratorModel extends BakedModelWrapper<BakedModel> {
    public RespiratorModel(BakedModel originalModel) {
        super(originalModel);
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext cameraItemDisplayContext, PoseStack mat, boolean leftHanded) {
        ResourceLocation respiratorModelLocation = ResourceLocation.fromNamespaceAndPath(CreateAlumina.MOD_ID, "block/respirator");
        if (cameraItemDisplayContext == ItemDisplayContext.HEAD)
            return getBakedModel(Minecraft.getInstance().getModelManager(), respiratorModelLocation)
                    .applyTransform(cameraItemDisplayContext, mat, leftHanded);
        return super.applyTransform(cameraItemDisplayContext, mat, leftHanded);
    }

    public BakedModel getBakedModel(ModelManager modelManager, ResourceLocation location) {
        return modelManager.getModel(ModelResourceLocation.standalone(location));
    }
}
