package kr.overbreak.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.ult.UltGauge;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /ob class &lt;직업&gt;   직업 지급 (데이터팩 /function pvp:class/&lt;직업&gt;/give)
 * /ob clear            직업 해제
 * /ob ult              게이지 100% (시험용)
 */
public final class ObCommand {
	private ObCommand() {}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> dispatcher.register(
				Commands.literal("ob")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("class")
								.then(Commands.argument("id", StringArgumentType.word())
										.suggests((c, b) -> {
											Classes.all().forEach(pc -> b.suggest(pc.id()));
											return b.buildFuture();
										})
										.executes(c -> {
											ServerPlayer p = c.getSource().getPlayerOrException();
											String id = StringArgumentType.getString(c, "id");
											PvpClass pc = Classes.byId(id);
											if (pc == null) {
												c.getSource().sendFailure(Component.literal("없는 직업: " + id));
												return 0;
											}
											Classes.give(p, pc);
											return 1;
										})))
						.then(Commands.literal("clear").executes(c -> {
							Classes.clear(c.getSource().getPlayerOrException());
							return 1;
						}))
						.then(Commands.literal("ult").executes(c -> {
							UltGauge.fill(c.getSource().getPlayerOrException());
							return 1;
						}))));
	}
}
