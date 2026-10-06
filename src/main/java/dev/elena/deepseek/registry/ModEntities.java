package dev.elena.deepseek.registry;

import dev.elena.deepseek.DeepSeekMod;
import dev.elena.deepseek.entity.CompanionEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, DeepSeekMod.MODID);

    public static final RegistryObject<EntityType<CompanionEntity>> COMPANION =
            ENTITIES.register("companion", () -> EntityType.Builder
                    .of(CompanionEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build("companion"));

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }
}
