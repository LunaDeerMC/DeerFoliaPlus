package cn.lunadeer.mc.deerfoliaplus.configurations;

import cn.lunadeer.mc.deerfolia.utils.configuration.Comments;
import cn.lunadeer.mc.deerfolia.utils.configuration.ConfigurationPart;

public class CustomRecipeConfiguration extends ConfigurationPart {

    @Comments("Enable custom recipe system (default: false)")
    public boolean enabled = false;
}
