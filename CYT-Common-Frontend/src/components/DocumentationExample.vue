<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ArrowDown, CopyDocument } from '@element-plus/icons-vue';
import type { Operation } from '~/api/types';
import { callExample, gatewayAddress, type ExampleLanguage } from '~/utils/documentation';
import { operationExample } from '~/utils/schema';

const props = defineProps<{ operation: Operation }>();
const address = defineModel<string>('address', { required: true });
const language = defineModel<ExampleLanguage>('language', { required: true });
const languages: { key: ExampleLanguage; label: string }[] = [
  { key: 'http', label: 'cURL' },
  { key: 'python', label: 'Python' },
  { key: 'typescript', label: 'TypeScript' },
];
const validAddress = computed(() => !!gatewayAddress(address.value));
const sample = computed(() =>
  callExample(props.operation, operationExample(props.operation), language.value, address.value),
);
const code = ref<HTMLElement>();
const copyStatus = ref('');
watch(sample, () => (copyStatus.value = ''));

async function copy() {
  try {
    await navigator.clipboard.writeText(sample.value);
    copyStatus.value = '已复制示例';
  } catch {
    if (code.value) {
      const range = document.createRange();
      range.selectNodeContents(code.value);
      window.getSelection()?.removeAllRanges();
      window.getSelection()?.addRange(range);
    }
    copyStatus.value = '已选中代码，请手动复制';
  }
}
</script>

<template>
  <section class="example" aria-label="调用示例">
    <div class="example-head">
      <h3>调用示例</h3>
      <button type="button" :disabled="!sample" @click="copy">
        <el-icon aria-hidden="true"><CopyDocument /></el-icon>
        {{ copyStatus === '已复制示例' ? '已复制' : '复制' }}
      </button>
    </div>
    <div class="example-languages" role="group" aria-label="示例语言">
      <button
        v-for="item in languages"
        :key="item.key"
        type="button"
        :aria-pressed="language === item.key"
        @click="language = item.key"
      >
        {{ item.label }}
      </button>
    </div>
    <pre v-if="sample" tabindex="0" aria-label="示例代码"><code ref="code">{{ sample }}</code></pre>
    <p v-else class="example-empty">请填写有效的网关地址以生成示例。</p>
    <div v-if="copyStatus" class="example-status" role="status">{{ copyStatus }}</div>
    <details class="example-address">
      <summary>
        <span>网关地址</span>
        <code>{{ gatewayAddress(address) || '请检查地址' }}</code>
        <el-icon aria-hidden="true"><ArrowDown /></el-icon>
      </summary>
      <div>
        <label>
          <span>网关基础地址</span>
          <el-input
            v-model="address"
            type="url"
            spellcheck="false"
            placeholder="https://gateway.example.com"
          />
        </label>
        <p v-if="!validAddress" role="alert">
          使用 HTTP 或 HTTPS 地址，移除账号密码、查询参数和锚点。
        </p>
        <p v-else>交付示例前，确认调用方的后端可以访问此地址。</p>
      </div>
    </details>
  </section>
</template>

<style scoped>
.example {
  min-width: 0;
  overflow: hidden;
  border: 1px solid #253954;
  border-radius: 12px;
  background: #101d32;
  color: #e8f1ff;
}
.example-head,
.example-languages,
.example-address summary {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-inline: 15px;
}
.example-head {
  justify-content: space-between;
  padding-block: 8px;
}
.example-head h3 {
  margin: 0;
  font-size: 14px;
}
.example button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 34px;
  padding: 4px 9px;
  border: 1px solid transparent;
  border-radius: 5px;
  color: #c5d5ec;
  background: transparent;
  cursor: pointer;
  font-size: 12px;
}
.example-head button {
  border-color: #415673;
}
.example button:hover,
.example button[aria-pressed='true'] {
  background: #263b59;
  color: #f1f7ff;
}
.example button:disabled {
  opacity: 0.55;
  cursor: default;
}
.example :is(button, pre, summary):focus-visible {
  outline: 2px solid #9cc9ff;
  outline-offset: -2px;
}
.example-languages {
  flex-wrap: wrap;
  border-bottom: 1px solid #304461;
  padding-bottom: 8px;
}
.example pre,
.example-empty {
  max-height: 270px;
  overflow: auto;
  margin: 0;
  padding: 15px;
  background: #14243b;
  color: #d8e9ff;
  font-size: 12px;
  line-height: 1.75;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  scrollbar-color: #5b7294 #14243b;
}
.example code {
  font-family: 'Cascadia Code', Consolas, ui-monospace, monospace;
}
.example-status {
  padding: 8px 15px;
  color: #a6f0cf;
  font-size: 12px;
}
.example-address {
  border-top: 1px solid #304461;
}
.example-address summary {
  padding-block: 9px;
  min-height: 42px;
  font-size: 12px;
  color: #c5d5ec;
  cursor: pointer;
}
.example-address summary::-webkit-details-marker {
  display: none;
}
.example-address summary span {
  white-space: nowrap;
}
.example-address summary code {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.example-address[open] summary .el-icon {
  transform: rotate(180deg);
}
.example-address > div {
  padding: 2px 15px 14px;
}
.example-address label {
  display: grid;
  gap: 6px;
  font-size: 12px;
}
.example-address p {
  margin: 8px 0 0;
  color: #c5d5ec;
  font-size: 12px;
}
.example-address p[role='alert'] {
  color: #ffd4ab;
}
@media (pointer: coarse) {
  .example button,
  .example-address summary {
    min-height: 44px;
  }
}
</style>
