package com.anatawa12.mcWrapper.internal;

import com.anatawa12.mcWrapper.v1.WNBTCompound;
import net.minecraft.entity.Entity;

public interface WEntityImpl {
    WNBTCompound getWNBTCompoundByEntity(Entity entity);
}
