<script setup lang="ts">
defineProps<{ label: string }>();
</script>

<template>
  <div class="list-filter-bar">
    <div class="list-filter-row" role="group" :aria-label="label">
      <slot />
      <div v-if="$slots.actions" class="list-filter-actions">
        <slot name="actions" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.list-filter-bar {
  container: list-filter / inline-size;
  padding: var(--list-filter-padding, 12px var(--app-panel-padding));
  border-bottom: var(--list-filter-border, 1px solid var(--app-divider));
}
.list-filter-row,
.list-filter-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.list-filter-actions {
  max-width: 100%;
  margin-inline-start: auto;
  gap: 8px 10px;
  font-size: 13px;
}
:slotted(.list-filter-search) {
  flex: 1 0 min(var(--list-filter-search-width, 200px), 100%);
  min-width: 0;
  max-width: var(--app-filter-search-max-width);
}
:slotted(.el-select),
:slotted(.list-filter-field) {
  flex: 1 0 min(var(--list-filter-field-width, 118px), 100%);
  min-width: 0;
  max-width: var(--list-filter-field-limit, 144px);
}
.list-filter-row :deep(.el-select__wrapper) {
  padding-inline: 10px;
}
:slotted(.list-filter-count) {
  color: var(--app-text-secondary);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.list-filter-actions :deep(.list-filter-count strong) {
  color: var(--app-text);
}
.list-filter-actions :deep(.el-button + .el-button) {
  margin-inline-start: 0;
}

@container list-filter (width < 520px) {
  :slotted(.list-filter-search) {
    flex-basis: 100%;
    max-width: 100%;
  }
}
@media (max-width: 760px) {
  .list-filter-bar {
    padding-inline: var(--list-filter-mobile-padding, 14px);
  }
}
</style>
