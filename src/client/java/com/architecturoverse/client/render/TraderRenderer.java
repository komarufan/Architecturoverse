package com.architecturoverse.client.render;

import com.architecturoverse.trade.TraderEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.resources.Identifier;

/** The merchant looks like the vanilla wandering trader. */
public class TraderRenderer extends MobRenderer<TraderEntity, VillagerRenderState, VillagerModel> {
	private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/wandering_trader/wandering_trader.png");

	public TraderRenderer(EntityRendererProvider.Context context) {
		super(context, new VillagerModel(context.bakeLayer(ModelLayers.WANDERING_TRADER)), 0.5F);
	}

	@Override
	public Identifier getTextureLocation(VillagerRenderState state) {
		return TEXTURE;
	}

	@Override
	public VillagerRenderState createRenderState() {
		return new VillagerRenderState();
	}
}
