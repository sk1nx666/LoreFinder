package org.gradle.accessors.dm;

import org.gradle.api.NonNullApi;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.plugin.use.PluginDependency;
import org.gradle.api.artifacts.ExternalModuleDependencyBundle;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.provider.Provider;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.internal.catalog.AbstractExternalDependencyFactory;
import org.gradle.api.internal.catalog.DefaultVersionCatalog;
import java.util.Map;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParser;
import javax.inject.Inject;

/**
 * A catalog of dependencies accessible via the {@code libs} extension.
 */
@NonNullApi
public class LibrariesForLibs extends AbstractExternalDependencyFactory {

    private final AbstractExternalDependencyFactory owner = this;
    private final FabricLibraryAccessors laccForFabricLibraryAccessors = new FabricLibraryAccessors(owner);
    private final MeteorLibraryAccessors laccForMeteorLibraryAccessors = new MeteorLibraryAccessors(owner);
    private final VersionAccessors vaccForVersionAccessors = new VersionAccessors(providers, config);
    private final BundleAccessors baccForBundleAccessors = new BundleAccessors(objects, providers, config, attributesFactory, capabilityNotationParser);
    private final PluginAccessors paccForPluginAccessors = new PluginAccessors(providers, config);

    @Inject
    public LibrariesForLibs(DefaultVersionCatalog config, ProviderFactory providers, ObjectFactory objects, ImmutableAttributesFactory attributesFactory, CapabilityNotationParser capabilityNotationParser) {
        super(config, providers, objects, attributesFactory, capabilityNotationParser);
    }

    /**
     * Dependency provider for <b>minecraft</b> with <b>com.mojang:minecraft</b> coordinates and
     * with version reference <b>minecraft</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getMinecraft() {
        return create("minecraft");
    }

    /**
     * Dependency provider for <b>yarn</b> with <b>net.fabricmc:yarn</b> coordinates and
     * with version reference <b>yarn.mappings</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getYarn() {
        return create("yarn");
    }

    /**
     * Group of libraries at <b>fabric</b>
     */
    public FabricLibraryAccessors getFabric() {
        return laccForFabricLibraryAccessors;
    }

    /**
     * Group of libraries at <b>meteor</b>
     */
    public MeteorLibraryAccessors getMeteor() {
        return laccForMeteorLibraryAccessors;
    }

    /**
     * Group of versions at <b>versions</b>
     */
    public VersionAccessors getVersions() {
        return vaccForVersionAccessors;
    }

    /**
     * Group of bundles at <b>bundles</b>
     */
    public BundleAccessors getBundles() {
        return baccForBundleAccessors;
    }

    /**
     * Group of plugins at <b>plugins</b>
     */
    public PluginAccessors getPlugins() {
        return paccForPluginAccessors;
    }

    public static class FabricLibraryAccessors extends SubDependencyFactory {

        public FabricLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>loader</b> with <b>net.fabricmc:fabric-loader</b> coordinates and
         * with version reference <b>fabric.loader</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getLoader() {
            return create("fabric.loader");
        }

    }

    public static class MeteorLibraryAccessors extends SubDependencyFactory {

        public MeteorLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>client</b> with <b>meteordevelopment:meteor-client</b> coordinates and
         * with version reference <b>meteor</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getClient() {
            return create("meteor.client");
        }

    }

    public static class VersionAccessors extends VersionFactory  {

        private final FabricVersionAccessors vaccForFabricVersionAccessors = new FabricVersionAccessors(providers, config);
        private final ModVersionAccessors vaccForModVersionAccessors = new ModVersionAccessors(providers, config);
        private final YarnVersionAccessors vaccForYarnVersionAccessors = new YarnVersionAccessors(providers, config);
        public VersionAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

        /**
         * Version alias <b>jdk</b> with value <b>21</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getJdk() { return getVersion("jdk"); }

        /**
         * Version alias <b>loom</b> with value <b>1.8-SNAPSHOT</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getLoom() { return getVersion("loom"); }

        /**
         * Version alias <b>meteor</b> with value <b>1.21.4-SNAPSHOT</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getMeteor() { return getVersion("meteor"); }

        /**
         * Version alias <b>minecraft</b> with value <b>1.21.4</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getMinecraft() { return getVersion("minecraft"); }

        /**
         * Group of versions at <b>versions.fabric</b>
         */
        public FabricVersionAccessors getFabric() {
            return vaccForFabricVersionAccessors;
        }

        /**
         * Group of versions at <b>versions.mod</b>
         */
        public ModVersionAccessors getMod() {
            return vaccForModVersionAccessors;
        }

        /**
         * Group of versions at <b>versions.yarn</b>
         */
        public YarnVersionAccessors getYarn() {
            return vaccForYarnVersionAccessors;
        }

    }

    public static class FabricVersionAccessors extends VersionFactory  {

        public FabricVersionAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

        /**
         * Version alias <b>fabric.loader</b> with value <b>0.16.9</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getLoader() { return getVersion("fabric.loader"); }

    }

    public static class ModVersionAccessors extends VersionFactory  {

        public ModVersionAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

        /**
         * Version alias <b>mod.version</b> with value <b>0.1.0</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getVersion() { return getVersion("mod.version"); }

    }

    public static class YarnVersionAccessors extends VersionFactory  {

        public YarnVersionAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

        /**
         * Version alias <b>yarn.mappings</b> with value <b>1.21.4+build.7</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getMappings() { return getVersion("yarn.mappings"); }

    }

    public static class BundleAccessors extends BundleFactory {

        public BundleAccessors(ObjectFactory objects, ProviderFactory providers, DefaultVersionCatalog config, ImmutableAttributesFactory attributesFactory, CapabilityNotationParser capabilityNotationParser) { super(objects, providers, config, attributesFactory, capabilityNotationParser); }

    }

    public static class PluginAccessors extends PluginFactory {
        private final FabricPluginAccessors paccForFabricPluginAccessors = new FabricPluginAccessors(providers, config);

        public PluginAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

        /**
         * Group of plugins at <b>plugins.fabric</b>
         */
        public FabricPluginAccessors getFabric() {
            return paccForFabricPluginAccessors;
        }

    }

    public static class FabricPluginAccessors extends PluginFactory {

        public FabricPluginAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

        /**
         * Plugin provider for <b>fabric.loom</b> with plugin id <b>fabric-loom</b> and
         * with version reference <b>loom</b>
         * <p>
         * This plugin was declared in catalog libs.versions.toml
         */
        public Provider<PluginDependency> getLoom() { return createPlugin("fabric.loom"); }

    }

}
