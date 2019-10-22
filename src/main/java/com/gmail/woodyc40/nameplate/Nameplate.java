package com.gmail.woodyc40.nameplate;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class Nameplate extends JavaPlugin implements Listener {
    @Override
    public void onEnable() {
        registerNameplateInterceptor(this);

        this.getCommand("nameplate").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("You are not a player!");
            return true;
        }
        if (!sender.hasPermission("nameplate.nameplate")) {
            sender.sendMessage("No permission!");
            return true;
        }

        Player player = (Player) sender;
        if (args.length < 1) {
            removeNameplate(this, player);
            sender.sendMessage("You have reset your nameplate");
            return true;
        }

        String newName = StringUtils.join(args, ' ');

        setNameplate(this, player, newName);
        sender.sendMessage("You have set your new nameplate to: " + newName);

        return true;
    }

    private static final Map<UUID, NameplateData> nameMap = new HashMap<>();

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        nameMap.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        for (NameplateData nd : nameMap.values()) {
            setScoreboardAppendages(event.getPlayer(), nd);
        }
    }

    private static void registerNameplateInterceptor(Plugin plugin) {
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();
        pm.addPacketListener(new PacketAdapter(plugin, PacketType.Play.Server.PLAYER_INFO) {
            @Override
            public void onPacketSending(PacketEvent event) {
                onPlayerInfoSend(plugin, event);
            }
        });
    }

    private static void onPlayerInfoSend(Plugin plugin, PacketEvent event) {
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();
        PacketContainer pc = event.getPacket();
        EnumWrappers.PlayerInfoAction action = pc.getPlayerInfoAction().read(0);
        if (action == EnumWrappers.PlayerInfoAction.ADD_PLAYER) {
            List<PlayerInfoData> infoData = pc.getPlayerInfoDataLists().read(0);
            List<PlayerInfoData> newInfoData = new ArrayList<>(infoData.size());

            for (PlayerInfoData pid : infoData) {
                WrappedGameProfile profile = pid.getProfile();
                UUID uuid = profile.getUUID();
                NameplateData nd = nameMap.get(uuid);
                if (nd == null) {
                    newInfoData.add(pid);
                    continue;
                }

                WrappedGameProfile newProfile = profile.withName(nd.getNewName());
                PlayerInfoData newPid = new PlayerInfoData(newProfile,
                        pid.getLatency(),
                        pid.getGameMode(),
                        pid.getDisplayName());
                newInfoData.add(newPid);
            }

            PacketContainer newPacket = pm.createPacket(event.getPacketType());
            newPacket.getPlayerInfoAction().write(0, action);
            newPacket.getPlayerInfoDataLists().write(0, newInfoData);
            try {
                pm.sendServerPacket(event.getPlayer(), newPacket, false);
            } catch (InvocationTargetException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to intercept for name change", e);
                return;
            }

            event.setCancelled(true);
        }
    }

    private static void setNameplate(Plugin plugin, Player player, String newName) {
        int nameLen = newName.length();
        String prefix = null;
        String suffix = null;
        if (nameLen > 144) {
            throw new IllegalArgumentException("Cannot have a name longer than 144 chars");
        } else if (nameLen > 80) {
            prefix = newName.substring(0, 64);
            suffix = newName.substring(80);
            newName = newName.substring(64, 80);
        } else if (nameLen > 64) {
            prefix = newName.substring(0, 64);
            newName = newName.substring(64);
        } else if (nameLen > 16) {
            prefix = newName;
            newName = null;
        }

        NameplateData nd = new NameplateData(prefix, newName, suffix);
        nameMap.put(player.getUniqueId(), nd);

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) {
                continue;
            }

            if (newName != null) {
                other.hidePlayer(plugin, player);
                other.showPlayer(plugin, player);
            }

            setScoreboardAppendages(other, nd);
        }
    }

    private static void removeNameplate(Plugin plugin, Player player) {
        NameplateData nd = nameMap.remove(player.getUniqueId());
        if (nd != null) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.equals(player)) {
                    continue;
                }

                if (nd.getNewName() != null) {
                    other.hidePlayer(plugin, player);
                    other.showPlayer(plugin, player);
                }

                if (nd.getPrefix() != null) {
                    Scoreboard curScoreboard = other.getScoreboard();

                    String origName = player.getName();
                    Team team = curScoreboard.getTeam(origName);
                    if (team != null) {
                        team.removeEntry(origName);
                    }
                }
            }
        }
    }

    private static void setScoreboardAppendages(Player player, NameplateData nd) {
        String prefix = nd.getPrefix();
        String suffix = nd.getSuffix();

        if (prefix != null) {
            ScoreboardManager sm = Bukkit.getScoreboardManager();
            Scoreboard curScoreboard = player.getScoreboard();
            if (curScoreboard.equals(sm.getMainScoreboard())) {
                curScoreboard = sm.getNewScoreboard();
            }

            String origName = player.getName();
            Team team = curScoreboard.getTeam(origName);
            if (team == null) {
                team = curScoreboard.registerNewTeam(origName);
            }

            team.setPrefix(prefix);
            team.addEntry(nd.getNewName());

            if (suffix != null) {
                team.setSuffix(suffix);
            }

            player.setScoreboard(curScoreboard);
        }
    }

    private static class NameplateData {
        private final String prefix;
        private final String newName;
        private final String suffix;

        private NameplateData(String prefix, String newName, String suffix) {
            this.prefix = prefix;
            this.newName = newName;
            this.suffix = suffix;
        }

        public String getPrefix() {
            return this.prefix;
        }

        public String getNewName() {
            return this.newName;
        }

        public String getSuffix() {
            return this.suffix;
        }
    }
}
