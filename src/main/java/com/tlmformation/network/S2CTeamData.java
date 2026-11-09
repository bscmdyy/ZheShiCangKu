package com.tlmformation.network;

import com.tlmformation.client.TeamClientData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public record S2CTeamData(int activeTeam, List<Member> allMaids, List<TeamView> teams) {
    public record Member(UUID uuid, int entityId, String name, boolean hasDeath,
                               boolean waitingRevive, boolean downed, int downedSeconds,
                               double deathX, double deathY, double deathZ,
                               String deathDimension,
                               @Nullable CompoundTag backup) {
    }

    public record Rally(double x, double y, double z, String dimension) {
    }

    public record TeamView(List<UUID> members, @Nullable Rally rally) {
    }

    public static void encode(S2CTeamData message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.activeTeam);
        buffer.writeVarInt(message.allMaids.size());
        for (Member member : message.allMaids) {
            buffer.writeUUID(member.uuid);
            buffer.writeVarInt(member.entityId);
            buffer.writeUtf(member.name, 128);
            buffer.writeBoolean(member.hasDeath);
            buffer.writeBoolean(member.waitingRevive);
            buffer.writeBoolean(member.downed);
            buffer.writeVarInt(member.downedSeconds);
            buffer.writeBoolean(member.backup != null);
            if (member.backup != null) {
                buffer.writeNbt(member.backup);
            }
            if (member.hasDeath) {
                buffer.writeDouble(member.deathX);
                buffer.writeDouble(member.deathY);
                buffer.writeDouble(member.deathZ);
                buffer.writeUtf(member.deathDimension, 128);
            }
        }
        buffer.writeVarInt(message.teams.size());
        for (TeamView team : message.teams) {
            buffer.writeVarInt(team.members.size());
            for (UUID member : team.members) {
                buffer.writeUUID(member);
            }
            buffer.writeBoolean(team.rally != null);
            if (team.rally != null) {
                buffer.writeDouble(team.rally.x);
                buffer.writeDouble(team.rally.y);
                buffer.writeDouble(team.rally.z);
                buffer.writeUtf(team.rally.dimension, 128);
            }
        }
    }

    public static S2CTeamData decode(FriendlyByteBuf buffer) {
        int active = buffer.readVarInt();
        int allSize = buffer.readVarInt();
        List<Member> all = new ArrayList<>();
        for (int i = 0; i < allSize; i++) {
            UUID uuid = buffer.readUUID();
            int entityId = buffer.readVarInt();
            String name = buffer.readUtf(128);
            boolean hasDeath = buffer.readBoolean();
            boolean waitingRevive = buffer.readBoolean();
            boolean downed = buffer.readBoolean();
            int downedSeconds = buffer.readVarInt();
            CompoundTag backup = buffer.readBoolean() ? buffer.readNbt() : null;
            double dx = 0, dy = 0, dz = 0;
            String dim = "";
            if (hasDeath) {
                dx = buffer.readDouble();
                dy = buffer.readDouble();
                dz = buffer.readDouble();
                dim = buffer.readUtf(128);
            }
            all.add(new Member(uuid, entityId, name, hasDeath, waitingRevive,
                    downed, downedSeconds, dx, dy, dz, dim, backup));
        }
        int teamSize = buffer.readVarInt();
        List<TeamView> teams = new ArrayList<>();
        for (int i = 0; i < teamSize; i++) {
            int members = buffer.readVarInt();
            List<UUID> ids = new ArrayList<>();
            for (int j = 0; j < members; j++) {
                ids.add(buffer.readUUID());
            }
            Rally rally = null;
            if (buffer.readBoolean()) {
                rally = new Rally(buffer.readDouble(), buffer.readDouble(),
                        buffer.readDouble(), buffer.readUtf(128));
            }
            teams.add(new TeamView(ids, rally));
        }
        return new S2CTeamData(active, all, teams);
    }

    public static void handle(S2CTeamData message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        if (context.getDirection().getReceptionSide().isClient()) {
            context.enqueueWork(() -> TeamClientData.update(message));
        }
        context.setPacketHandled(true);
    }
}
