package org.leavesmc.leaves.protocol.syncmatica;

import java.util.Collection;
import java.util.EnumSet;

public class FeatureSet {

    private final EnumSet<Feature> features;

    private FeatureSet(final EnumSet<Feature> features) {
        this.features = features;
    }

    public static FeatureSet empty() {
        return new FeatureSet(EnumSet.noneOf(Feature.class));
    }

    public static FeatureSet fromString(final String featureString) {
        final EnumSet<Feature> featureSet = EnumSet.noneOf(Feature.class);
        if (featureString != null && !featureString.isEmpty()) {
            final String[] split = featureString.split("\n");
            for (final String s : split) {
                final Feature feature = Feature.fromString(s);
                if (feature != null) {
                    featureSet.add(feature);
                }
            }
        }
        return new FeatureSet(featureSet);
    }

    public static FeatureSet fromCollection(final Collection<Feature> featureCollection) {
        final EnumSet<Feature> featureSet = EnumSet.noneOf(Feature.class);
        featureSet.addAll(featureCollection);
        return new FeatureSet(featureSet);
    }

    public boolean hasFeature(final Feature feature) {
        return features.contains(feature);
    }

    public FeatureSet intersect(final FeatureSet other) {
        final EnumSet<Feature> intersection = EnumSet.copyOf(features);
        intersection.retainAll(other.features);
        return new FeatureSet(intersection);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (final Feature f : features) {
            if (!first) {
                sb.append("\n");
            }
            sb.append(f.toString());
            first = false;
        }
        return sb.toString();
    }
}
