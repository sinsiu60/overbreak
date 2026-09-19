package kr.overbreak.client.mixin;

import kr.overbreak.client.anim.KnockRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements KnockRenderState {
	@Unique private float overbreak$knock;
	@Unique private float overbreak$rollAngle;
	@Unique private float overbreak$rollAxisX;
	@Unique private float overbreak$rollAxisZ;

	@Override
	public void overbreak$setRoll(float angle, float axisX, float axisZ) {
		this.overbreak$rollAngle = angle;
		this.overbreak$rollAxisX = axisX;
		this.overbreak$rollAxisZ = axisZ;
	}

	@Override
	public float overbreak$rollAngle() {
		return overbreak$rollAngle;
	}

	@Override
	public float overbreak$rollAxisX() {
		return overbreak$rollAxisX;
	}

	@Override
	public float overbreak$rollAxisZ() {
		return overbreak$rollAxisZ;
	}

	@Override
	public void overbreak$setKnock(float progress) {
		this.overbreak$knock = progress;
	}

	@Override
	public float overbreak$knock() {
		return overbreak$knock;
	}
}
