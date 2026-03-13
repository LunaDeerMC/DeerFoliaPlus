package cn.lunadeer.mc.deerfoliaplus.configurations;

import cn.lunadeer.mc.deerfolia.utils.configuration.Comments;
import cn.lunadeer.mc.deerfolia.utils.configuration.ConfigurationPart;

public class SyncmaticaConfiguration extends ConfigurationPart {
    @Comments("Enable Syncmatica protocol support (default: false)")
    public boolean enable = false;
    @Comments("Enable file upload quota limit (default: false)")
    public boolean useQuota = false;
    @Comments("Maximum upload size in bytes when quota is enabled (default: 40000000)")
    public int quotaLimit = 40000000;
}
