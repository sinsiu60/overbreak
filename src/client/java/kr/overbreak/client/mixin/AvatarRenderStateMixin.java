package kr.overbreak.client.mixin;

import kr.overbreak.client.anim.AnimRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements AnimRenderState {
	@Unique private int overbreak$anim;
	@Unique private float overbreak$time;
	@Unique private float overbreak$end;
	@Unique private float overbreak$weight;
	@Unique private float overbreak$itemScale = 1.0F;
	@Unique private float overbreak$spin;
	@Unique private final net.minecraft.client.renderer.item.ItemStackRenderState overbreak$magazine = new net.minecraft.client.renderer.item.ItemStackRenderState();

	@Override
	public void overbreak$set(int anim, float time, float end, float weight) {
		this.overbreak$anim = anim;
		this.overbreak$time = time;
		this.overbreak$end = end;
		this.overbreak$weight = weight;
	}

	@Override
	public void overbreak$setItemScale(float scale) {
		this.overbreak$itemScale = scale;
	}

	@Override
	public void overbreak$setSpin(float spin) {
		this.overbreak$spin = spin;
	}

	@Override
	public int overbreak$anim() {
		return overbreak$anim;
	}

	@Override
	public float overbreak$time() {
		return overbreak$time;
	}

	@Override
	public float overbreak$end() {
		return overbreak$end;
	}

	@Override
	public float overbreak$weight() {
		return overbreak$weight;
	}

	@Override
	public float overbreak$itemScale() {
		return overbreak$itemScale;
	}

	@Override
	public float overbreak$spin() {
		return overbreak$spin;
	}

	@Override
	public net.minecraft.client.renderer.item.ItemStackRenderState overbreak$magazine() {
		return overbreak$magazine;
	}
}
