package dev.elena.deepseek;

import dev.elena.deepseek.ai.Config;
import dev.elena.deepseek.network.Network;
import dev.elena.deepseek.registry.ModBlocks;
import dev.elena.deepseek.registry.ModEntities;
import dev.elena.deepseek.registry.ModItems;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(DeepSeekMod.MODID)
public class DeepSeekMod {
    public static final String MODID = "deepseek";

    public DeepSeekMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.register(bus);
        ModItems.register(bus);
        ModBlocks.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        Network.register();
    }
}
