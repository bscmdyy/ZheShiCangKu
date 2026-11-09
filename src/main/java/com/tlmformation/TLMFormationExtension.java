package com.tlmformation;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.api.entity.ai.IExtraMaidBrain;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ExtraMaidBrainManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import com.tlmformation.formation.FormationFollowTask;
import com.tlmformation.tactical.TacticalLogisticsTask;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;

import java.util.List;

@LittleMaidExtension
public final class TLMFormationExtension implements ILittleMaid {
    @Override
    public void addMaidTask(TaskManager manager) {
        manager.add(new TacticalLogisticsTask());
    }

    @Override
    public void addExtraMaidBrain(ExtraMaidBrainManager manager) {
        TLMFormationMod.LOGGER.info("Registering formation follow brain");
        manager.addExtraMaidBrain(new FormationBrain());
    }

    private static final class FormationBrain implements IExtraMaidBrain {
        @Override
        public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> getCoreBehaviors() {
            return ImmutableList.of(Pair.of(4, new FormationFollowTask()));
        }
    }
}
