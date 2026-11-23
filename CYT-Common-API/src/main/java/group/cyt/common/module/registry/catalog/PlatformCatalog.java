package group.cyt.common.module.registry.catalog;

/** A platform contributes its operations, credentials and legacy names as one Java module. */
public interface PlatformCatalog {
  void register(CatalogBuilder catalog);
}
