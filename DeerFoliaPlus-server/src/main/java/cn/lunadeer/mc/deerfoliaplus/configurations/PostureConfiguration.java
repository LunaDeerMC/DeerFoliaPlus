package cn.lunadeer.mc.deerfoliaplus.configurations;

import cn.lunadeer.mc.deerfolia.utils.configuration.Comments;
import cn.lunadeer.mc.deerfolia.utils.configuration.ConfigurationPart;

public class PostureConfiguration extends ConfigurationPart {

    @Comments("Enable posture commands and chair interaction")
    public boolean enabled = false;

    @Comments("Allow empty-hand right click on stairs and slabs to sit")
    public boolean chairInteraction = true;

    @Comments("Require signs on both sides of a stair or slab before it counts as a chair")
    public boolean requireSideSigns = false;

    @Comments("Maximum number of contiguous stairs or slabs that can be recognized as one chair row")
    public int maxChairChainLength = 8;
}
