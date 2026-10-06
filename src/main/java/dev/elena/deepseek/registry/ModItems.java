package dev.elena.deepseek.registry;

import dev.elena.deepseek.DeepSeekMod;
import dev.elena.deepseek.item.BuildingTorchItem;
import dev.elena.deepseek.item.PokeBallItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, DeepSeekMod.MODID);

    public static final RegistryObject<Item> COMPANION_SPAWN_EGG = ITEMS.register("companion_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.COMPANION, 0x9B6FD0, 0xF6E3C5,
                    new Item.Properties().tab(CreativeModeTab.TAB_MISC)));

    public static final RegistryObject<Item> BUILDING_TORCH = ITEMS.register("building_torch",
            () -> new BuildingTorchItem(new Item.Properties().stacksTo(1).tab(CreativeModeTab.TAB_MISC)));

    public static final RegistryObject<Item> POKE_BALL = ITEMS.register("poke_ball",
            () -> new PokeBallItem(new Item.Properties().stacksTo(1).tab(CreativeModeTab.TAB_MISC)));

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
