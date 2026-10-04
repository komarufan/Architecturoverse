package com.architecturoverse.client.render;

import com.architecturoverse.military.EnemySoldierEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.npc.BabyVillagerModel;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.client.renderer.entity.layers.VillagerProfessionLayer;
import net.minecraft.client.renderer.entity.state.HoldingEntityRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;

/** Enemy soldiers are taiga weaponsmiths, so they look different from the ruler's own soldiers. */
public class EnemySoldierRenderer extends MobRenderer<EnemySoldierEntity, VillagerRenderState, VillagerModel> {
	private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/villager/villager.png");

	public EnemySoldierRenderer(EntityRendererProvider.Context context) {
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
	public void extractRenderState(EnemySoldierEntity entity, VillagerRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		HoldingEntityRenderState.extractHoldingEntityRenderState(entity, state, this.itemModelResolver);
		state.villagerData = new VillagerData(
			BuiltInRegistries.VILLAGER_TYPE.getOrThrow(VillagerType.TAIGA),
			BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.WEAPONSMITH),
			5);
	}
}
