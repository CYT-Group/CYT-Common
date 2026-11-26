<script setup lang="ts">
import { computed } from 'vue';
import type { Schema } from '~/api/types';
import { schemaRows } from '~/utils/integration-direction';
const props = withDefaults(
  defineProps<{
    schema: Schema;
    fieldLabel?: string;
    showRequired?: boolean;
    emptyText?: string;
    readable?: boolean;
  }>(),
  {
    fieldLabel: '参数',
    showRequired: true,
    emptyText: '此接口无需请求参数',
  },
);
const rows = computed(() => schemaRows(props.schema));
</script>
<template>
  <template v-if="readable">
    <dl v-if="rows.length" class="field-list" :aria-label="fieldLabel">
      <div v-for="row in rows" :key="row.name">
        <dt>
          <code>{{ row.name }}</code>
          <span class="field-type">{{ row.type }}</span>
          <span v-if="showRequired && row.required" class="field-required">必填</span>
        </dt>
        <dd>
          {{ row.description }}
          <small v-if="row.default !== '—'">
            {{ /^(可选值|示例)：/.test(row.default) ? row.default : `默认值：${row.default}` }}
          </small>
        </dd>
      </div>
    </dl>
    <p v-else class="fields-empty">{{ emptyText }}</p>
  </template>
  <el-table v-else :data="rows" :empty-text="emptyText" size="small" class="schema-fields"
    ><el-table-column :label="fieldLabel" min-width="190"
      ><template #default="{ row }"
        ><span class="mono schema-field-name">{{ row.name }}</span
        ><el-tag
          v-if="showRequired && row.required"
          size="small"
          type="warning"
          effect="plain"
          style="margin-left: 8px"
          >必填</el-tag
        ></template
      ></el-table-column
    ><el-table-column prop="type" label="类型" width="125"
      ><template #default="{ row }"
        ><span class="schema-type">{{ row.type }}</span></template
      ></el-table-column
    ><el-table-column prop="description" label="说明" min-width="230" /><el-table-column
      prop="default"
      :label="showRequired ? '默认值 / 示例' : '取值 / 示例'"
      min-width="125"
  /></el-table>
</template>
<style scoped>
.field-list {
  margin: 0;
  border-block: 1px solid var(--app-border);
  overflow-wrap: anywhere;
}
.field-list > div {
  padding: 13px 0;
}
.field-list > div + div {
  border-top: 1px solid var(--app-divider);
}
.field-list dt {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 4px 9px;
  color: var(--app-text);
  font-size: 13px;
  font-weight: 600;
}
.field-list dd,
.fields-empty {
  color: var(--app-text-secondary);
  font-size: 13px;
  line-height: 1.65;
}
.field-list dd {
  margin: 4px 0 0;
}
.field-list small {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  font-family: ui-monospace, Consolas, monospace;
}
.field-type,
.field-required {
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
  background: var(--app-surface-soft);
  color: var(--app-text-secondary);
}
.field-type {
  font-family: ui-monospace, Consolas, monospace;
}
.field-required {
  color: #854d0e;
  background: #fef3c7;
}
.schema-fields {
  border: 1px solid var(--app-border);
  border-radius: 8px;
}
.schema-fields :deep(td.el-table__cell) {
  height: auto;
  padding: 14px 0;
}
.schema-fields :deep(.cell) {
  line-height: 1.8;
}
.schema-field-name {
  font-size: 12px;
  color: var(--app-text);
  overflow-wrap: anywhere;
}
.schema-type {
  color: #64748b;
  font-family: ui-monospace, Consolas, monospace;
  font-size: 11px;
  overflow-wrap: anywhere;
}
</style>
