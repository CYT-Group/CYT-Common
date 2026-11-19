<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox, type ButtonInstance, type InputInstance } from 'element-plus';
import { Check, CopyDocument, Plus } from '@element-plus/icons-vue';
import { api } from '~/api';
import { dateTime, useAction, useRequest } from '~/composables/useRequest';
import PageState from './PageState.vue';

type CallerKey = Awaited<ReturnType<typeof api.keys>>['items'][number];

const props = defineProps<{ system: string; enabled: boolean }>();
const emit = defineEmits<{ countChange: [count: number] }>();
const { data, loading, error, load } = useRequest(() => api.keys(props.system));
const { busy, run } = useAction();
const filter = ref<'all' | 'active' | 'revoked'>('active');
const keys = computed(() => data.value?.items || []);
const activeCount = computed(() => keys.value.filter((key) => !key.revoked).length);
const visibleKeys = computed(() =>
  filter.value === 'all'
    ? keys.value
    : keys.value.filter((key) => key.revoked === (filter.value === 'revoked')),
);

const dialogOpen = ref(false);
const name = ref('');
const nameInput = ref<InputInstance>();
const created = ref<{ key: string; name: string }>();
const revealed = ref(false);
const copyState = ref<'ready' | 'copied' | 'manual'>('ready');
const copyButton = ref<ButtonInstance>();
const secretInput = ref<HTMLInputElement>();

onMounted(refreshKeys);

async function refreshKeys() {
  await load();
  if (data.value) emit('countChange', activeCount.value);
}

function keyName(key: CallerKey) {
  return key.name?.trim() || '未命名密钥';
}

function openCreate() {
  name.value = '';
  dialogOpen.value = true;
}

function clearSecret() {
  created.value = undefined;
  revealed.value = false;
  copyState.value = 'ready';
}

async function create() {
  const keyName = name.value.trim();
  if (!props.enabled || !dialogOpen.value || created.value || !keyName) return;
  await run(async () => {
    const result = await api.createKey(props.system, keyName);
    created.value = { key: result.key, name: keyName };
    if (filter.value === 'revoked') filter.value = 'active';
    void refreshKeys();
    await nextTick();
    copyButton.value?.ref?.focus();
  });
}

async function copy() {
  const current = created.value;
  if (!current) return;
  try {
    await navigator.clipboard.writeText(current.key);
    if (created.value === current) copyState.value = 'copied';
  } catch {
    if (created.value !== current) return;
    revealed.value = true;
    copyState.value = 'manual';
    await nextTick();
    secretInput.value?.focus();
    secretInput.value?.select();
  }
}

async function manageKey(key: CallerKey) {
  const action = key.revoked ? '删除' : '吊销';
  const target = `「${keyName(key)}」（cyt_${key.prefix}_…）`;
  const message = key.revoked
    ? `删除${target}后，记录将从密钥列表移除。此操作无法撤销。`
    : `吊销${target}后，使用它的服务将立即无法调用网关。`;
  try {
    await ElMessageBox.confirm(message, `${action}密钥`, {
      confirmButtonText: action,
      confirmButtonType: 'danger',
      cancelButtonText: '取消',
      type: 'warning',
    });
  } catch {
    return;
  }
  await run(async () => {
    if (key.revoked) {
      await api.deleteRevokedKey(props.system, key.id);
      ElMessage.success('密钥已删除');
    } else {
      await api.revokeKey(key.id);
      ElMessage.success('密钥已吊销，可在「已吊销」中删除');
    }
    await refreshKeys();
  });
}
</script>

<template>
  <section class="panel" aria-label="调用密钥">
    <div class="panel-head keys-heading">
      <div>
        <h2>调用密钥</h2>
        <p>供此调用方的后端访问已授权的网关接口。</p>
      </div>
      <el-button type="primary" :icon="Plus" :disabled="!enabled || busy" @click="openCreate"
        >创建密钥</el-button
      >
    </div>

    <PageState :loading="loading" :error="error" @retry="refreshKeys">
      <template v-if="keys.length">
        <div class="key-filters">
          <el-radio-group v-model="filter" size="small" aria-label="筛选密钥状态">
            <el-radio-button value="all">全部 {{ keys.length }}</el-radio-button>
            <el-radio-button value="active">有效 {{ activeCount }}</el-radio-button>
            <el-radio-button value="revoked"
              >已吊销 {{ keys.length - activeCount }}</el-radio-button
            >
          </el-radio-group>
        </div>
        <table v-if="visibleKeys.length" class="key-table" role="table" aria-label="密钥列表">
          <thead role="rowgroup">
            <tr role="row">
              <th scope="col" role="columnheader">密钥</th>
              <th scope="col" role="columnheader">创建时间</th>
              <th scope="col" role="columnheader">最近使用</th>
              <th scope="col" role="columnheader">状态</th>
              <th scope="col" role="columnheader">操作</th>
            </tr>
          </thead>
          <tbody role="rowgroup">
            <tr v-for="key in visibleKeys" :key="key.id" role="row">
              <th scope="row" role="rowheader" class="key-identity">
                <span>{{ keyName(key) }}</span>
                <code class="mono">cyt_{{ key.prefix }}_…</code>
              </th>
              <td role="cell" class="key-date">
                <span class="mobile-label">创建时间</span>
                {{ dateTime(key.created_at) }}
              </td>
              <td role="cell" class="key-date">
                <span class="mobile-label">最近使用</span>
                {{ key.last_used_at ? dateTime(key.last_used_at) : '尚未使用' }}
              </td>
              <td role="cell" class="key-status">
                <el-tag :type="key.revoked ? 'info' : 'success'" effect="light" size="small" round>
                  {{ key.revoked ? '已吊销' : '有效' }}
                </el-tag>
              </td>
              <td role="cell" class="key-actions">
                <el-button
                  size="small"
                  :disabled="busy"
                  :aria-label="`${key.revoked ? '删除' : '吊销'} ${keyName(key)}`"
                  @click="manageKey(key)"
                  >{{ key.revoked ? '删除' : '吊销' }}</el-button
                >
              </td>
            </tr>
          </tbody>
        </table>
        <el-empty
          v-else
          :image-size="60"
          :description="filter === 'active' ? '没有有效密钥' : '没有已吊销的密钥'"
        />
      </template>
      <el-empty v-else :image-size="60" description="暂无调用密钥" />
    </PageState>

    <el-dialog
      v-model="dialogOpen"
      :title="created ? '密钥已创建' : '创建调用密钥'"
      width="min(540px, calc(100vw - 32px))"
      append-to-body
      :close-on-click-modal="false"
      :close-on-press-escape="!created && !busy"
      :show-close="!created && !busy"
      destroy-on-close
      @opened="nameInput?.focus()"
      @close="clearSecret"
    >
      <div v-if="created" class="key-delivery">
        <p class="key-help">
          将「{{ created.name }}」保存到调用方后端。关闭此窗口后，无法再次查看完整密钥。
        </p>
        <label for="created-caller-key">调用密钥</label>
        <div class="key-transfer">
          <div class="key-field">
            <input
              id="created-caller-key"
              ref="secretInput"
              class="mono"
              :type="revealed ? 'text' : 'password'"
              :value="created.key"
              autocomplete="off"
              spellcheck="false"
              readonly
            />
            <el-button link @click="revealed = !revealed">{{
              revealed ? '隐藏' : '显示'
            }}</el-button>
          </div>
          <el-button
            ref="copyButton"
            type="primary"
            :icon="copyState === 'copied' ? Check : CopyDocument"
            @click="copy"
            >{{ copyState === 'copied' ? '已复制' : '复制密钥' }}</el-button
          >
        </div>
        <p v-if="copyState === 'manual'" class="key-help" role="status">
          已选中密钥，请使用复制快捷键或系统复制菜单。
        </p>
      </div>
      <el-form v-else label-position="top" @submit.prevent="create">
        <el-form-item label="密钥名称" for="caller-key-name" required>
          <el-input
            id="caller-key-name"
            ref="nameInput"
            v-model="name"
            placeholder="例如：研发联调、CI 构建"
            maxlength="64"
            :disabled="busy"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-if="created" @click="dialogOpen = false">已保存，关闭</el-button>
        <template v-else>
          <el-button :disabled="busy" @click="dialogOpen = false">取消</el-button>
          <el-button
            type="primary"
            :disabled="!enabled || !name.trim()"
            :loading="busy"
            @click="create"
            >创建密钥</el-button
          >
        </template>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.keys-heading h2 {
  font-size: 16px;
}
.keys-heading > .el-button {
  flex: none;
}
.key-filters {
  padding: 14px var(--app-panel-padding);
}
.key-table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
}
.key-table th,
.key-table td {
  padding: 13px 12px;
  border-top: 1px solid var(--app-divider);
}
.key-table th:first-child {
  padding-left: var(--app-panel-padding);
}
.key-table td:last-child,
.key-table thead th:last-child {
  padding-right: var(--app-panel-padding);
  text-align: right;
}
.key-table thead {
  background: var(--app-surface-soft);
  color: var(--app-text-secondary);
  font-size: 12px;
}
.key-table thead th {
  font-weight: 500;
}
.key-identity {
  width: 38%;
  font-weight: 600;
  overflow-wrap: anywhere;
}
.key-identity code {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  font-weight: 400;
  color: var(--app-text-muted);
}
.key-date {
  color: var(--app-text-secondary);
}
.mobile-label {
  display: none;
}
.key-delivery {
  display: grid;
  gap: 10px;
}
.key-delivery label {
  color: var(--app-text);
  font-weight: 600;
}
.key-help {
  margin: 0;
  line-height: 1.55;
  color: var(--app-text-secondary);
  overflow-wrap: anywhere;
}
.key-transfer,
.key-field {
  display: flex;
  align-items: center;
  gap: 8px;
}
.key-field {
  min-width: 0;
  flex: 1;
  min-height: var(--app-control-height);
  padding: 0 10px;
  border: 1px solid var(--app-border);
  border-radius: var(--el-border-radius-base);
  background: var(--app-surface-soft);
}
.key-field:focus-within {
  border-color: var(--app-primary);
}
.key-field input {
  flex: 1;
  min-width: 0;
  padding: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--app-text);
  font-size: 13px;
}
@media (max-width: 800px) {
  .key-table thead {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip-path: inset(50%);
  }
  .key-table,
  .key-table tbody {
    display: block;
  }
  .key-table tbody tr {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 8px 12px;
    padding: 14px var(--app-panel-padding);
    border-top: 1px solid var(--app-divider);
  }
  .key-table tbody th:first-child,
  .key-table tbody td {
    padding: 0;
    border: 0;
  }
  .key-table tbody td:last-child {
    padding-right: 0;
  }
  .key-identity {
    width: auto;
  }
  .key-date {
    grid-column: 1;
    font-size: 12px;
  }
  .mobile-label {
    display: inline;
    margin-right: 4px;
  }
  .key-status {
    grid-column: 2;
    grid-row: 1;
    justify-self: end;
  }
  .key-actions {
    grid-column: 2;
    grid-row: 2 / span 2;
    align-self: end;
    justify-self: end;
  }
}
@media (max-width: 480px) {
  .key-transfer {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
