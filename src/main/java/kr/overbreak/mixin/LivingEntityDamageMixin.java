package kr.overbreak.mixin;

import kr.overbreak.combat.DamageModifiers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 받는 피해 수정 훅 — {@link DamageModifiers}. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float overbreak$modifyDamage(float damage, ServerLevel level, DamageSource source) {
		return DamageModifiers.apply((LivingEntity) (Object) this, source, damage);
	}
}
