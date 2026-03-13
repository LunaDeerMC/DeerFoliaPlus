package cn.lunadeer.mc.deerfoliaplus.configurations;

import cn.lunadeer.mc.deerfolia.utils.configuration.Comments;
import cn.lunadeer.mc.deerfolia.utils.configuration.ConfigurationPart;

public class ServuxConfiguration extends ConfigurationPart {
    @Comments("Enable Servux structures protocol for structure bounding box overlay (default: false)")
    public boolean structureProtocol = false;
    @Comments("Enable Servux entity data protocol for entity/block entity NBT inspection (default: false)")
    public boolean entityProtocol = false;
}
