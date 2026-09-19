package kr.overbreak.client.mixin;

import kr.overbreak.client.anim.SkillAnims;
import kr.overbreak.client.hud.MatchTeams;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 모델 애니메이션이 대신 보여 주는 디스플레이 엔티티를 모드 클라이언트에서 숨깁니다.
 * 팀전에서는 테두리 색도 여기서 정합니다 (우리 편 파랑 · 상대 빨강 — 보는 사람마다 다름).
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void overbreak$hideSkillDisplay(Entity entity, Frustum culler, double camX, double camY, double camZ,
											CallbackInfoReturnable<Boolean> cir) {
		if (SkillAnims.hides(entity)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void overbreak$teamOutlineColor(Entity entity, EntityRenderState state, float partial, CallbackInfo ci) {
		int color = MatchTeams.outline(entity);
		if (color != 0) {
			state.outlineColor = color;
		}
	}
}
