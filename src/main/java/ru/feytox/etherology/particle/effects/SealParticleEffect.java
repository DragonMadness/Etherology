package ru.feytox.etherology.particle.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.particle.ParticleType;
import net.minecraft.util.math.Vec3d;
import ru.feytox.etherology.magic.seal.SealType;
import ru.feytox.etherology.particle.effects.misc.FeyParticleEffect;
import ru.feytox.etherology.util.misc.CodecUtil;

@Getter
public class SealParticleEffect extends FeyParticleEffect<SealParticleEffect> {

    private final SealType zoneType;
    private final Vec3d endPos;

    public SealParticleEffect(ParticleType<SealParticleEffect> type, SealType zoneType, Vec3d endPos) {
        super(type);
        this.zoneType = zoneType;
        this.endPos = endPos;
    }

    public SealParticleEffect(ParticleType<SealParticleEffect> type) {
        this(type, null, null);
    }

    @Override
    public MapCodec<SealParticleEffect> createCodec() {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                SealType.CODEC.fieldOf("zoneType").forGetter(SealParticleEffect::getZoneType),
                Vec3d.CODEC.fieldOf("endPos").forGetter(SealParticleEffect::getEndPos)
        ).apply(instance, biFactory(SealParticleEffect::new)));
    }

    @Override
    public PacketCodec<RegistryByteBuf, SealParticleEffect> createPacketCodec() {
        return PacketCodec.tuple(SealType.PACKET_CODEC, SealParticleEffect::getZoneType,
                CodecUtil.VEC3D_PACKET, SealParticleEffect::getEndPos, biFactory(SealParticleEffect::new));
    }
}
