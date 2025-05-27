package cn.lunadeer.mc.deerfoliaplus.configurations;

import cn.lunadeer.mc.deerfolia.utils.configuration.Comments;
import cn.lunadeer.mc.deerfolia.utils.configuration.ConfigurationPart;

public class FakePlayerConfiguration extends ConfigurationPart {

    @Comments({
            "Enable fake player feature",
    })
    public boolean enable = false;

    @Comments({
            "Weather to always send data to the client",
            "If true, the bot will always send data to the client, even if the player is not online.",
    })
    public boolean alwaysSendData = true;

    @Comments({
            "Weather to spawn phantom for the fake player",
            "If true, phantom will be spawned for the fake player like a real player.",
    })
    public boolean spawnPhantom = true;

    @Comments({
            "Weather to skip sleep check for the fake player",
            "If true, server skips the sleep check for the fake player,",
    })
    public boolean skipSleepCheck = true;

    @Comments({
            "Weather to make the fake player invulnerable",
            "If true, the fake player will not take any damage (and won't be hostaged by mobs).",
    })
    public boolean invulnerable = true;

    @Comments({
            "The prefix of the fake player name, default is 'BOT_'",
            "You can change it to any string you want, but make sure it doesn't conflict with other players' names.",
            "Prefix is counted in the name length, so it should be less than 16 characters in total.",
    })
    public String prefix = "BOT_";

    public String suffix = "";

    @Comments({
            "Whether the fake player is auto saved before the server stops",
            "and loaded when the server starts.",
    })
    public boolean residentBot = true;

    public boolean cacheSkin = true;

    @Comments({
            "The amount of health the fake player will regenerate per second.",
            "Default is 0.0, which means no regeneration.",
    })
    public double regenAmount = 0.0;

    @Comments({
            "The amount of fake players to spawn per real player.",
    })
    public int amountPerPlayer = 2;

}
