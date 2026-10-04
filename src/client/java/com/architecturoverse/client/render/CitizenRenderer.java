package com.architecturoverse.client.render;

import com.architecturoverse.citizen.CitizenEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.npc.BabyVillagerModel;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.client.renderer.entity.layers.VillagerProfessionLayer;
import net.minecraft.client.renderer.entity.state.HoldingEntityRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.resources.Identifier;

/** Draws citizens exactly like vanilla villagers; the profession overlay shows their job. */
public class CitizenRenderer extends MobRenderer<CitizenEntity, VillagerRenderState, VillagerModel> {
	private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/villager/villager.png");

	public CitizenRenderer(EntityRendererProvider.Context context) {
		super(context, new VillagerModel(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
		this.addLayer(new VillagerProfessionLayer<>(
			this,
			context.getResourceManager(),
			"villager",
			new VillagerModel(context.bakeLayer(ModelLayers.VILLAGER_NO_HAT)),
			new BabyVillagerModel(context.bakeLayer(ModelLayers.VILLAGER_BABY_NO_HAT))
		));
		this.addLayer(new CrossedArmsItemLayer<>(this));
	}

	@Override
	public Identifier getTextureLocation(VillagerRenderState state) {
		return TEXTURE;
	}

	@Override
	public VillagerRenderState createRenderState() {
		return new VillagerRenderState();
	}

	@Override
	public void extractRenderState(CitizenEntity entity, VillagerRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		HoldingEntityRenderState.extractHoldingEntityRenderState(entity, state, this.itemModelResolver);
		state.villagerData = entity.getLook();
	}
}
