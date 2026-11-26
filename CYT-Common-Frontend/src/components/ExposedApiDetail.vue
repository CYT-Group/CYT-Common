<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { ArrowLeft, CircleCheck, CopyDocument, Refresh, Warning } from '@element-plus/icons-vue';
import { api } from '~/api';
import type { CustomApi, PlatformModule } from '~/api/types';
import { confirm, useAction, useRequest } from '~/composables/useRequest';
import type { CatalogOperation } from '~/utils/authoring';
import ResponseFields from '~/components/ResponseFields.vue';
import SchemaFields from '~/components/SchemaFields.vue';

const props = defineProps<{
  operation: CatalogOperation;
  config: CustomApi;
  service?: PlatformModule;
  section: string;
  environment: 'test' | 'production';
}>();
const emit = defineEmits<{ back: []; section: [value: string]; updated: [] }>();
const route = useRoute();
const router = useRouter();
const {
  data: check,
  loading,
  error,
  load,
} = useRequest(() => api.customApiPreflight(props.config.id));
const { busy, run } = useAction();
const debugDialog = ref(false);
const grantSystem = ref('');
const gatewayPath = computed(() => check.value?.gateway_path || props.operation.path);
const environment = computed(() => check.value?.upstream_environment || props.environment);
const environmentLabel = computed(() => (environment.value === 'production' ? '正式' : '测试'));
const splitAddresses = computed(
  () =>
    check.value?.environment_specific ??
    (props.service?.test_base_url != null || props.service?.production_base_url != null),
);
const upstreamAddress = computed(
  () =>
    check.value?.upstream_base_url ??
    (splitAddresses.value
      ? (environment.value === 'production'
          ? props.service?.production_base_url
          : props.service?.test_base_url) || ''
      : props.config.base_url || props.service?.base_url || ''),
);
const apiEnabled = computed(() => check.value?.api_enabled ?? props.config.enabled);
const available = computed(
  () => Boolean(upstreamAddress.value) && (check.value?.enabled ?? props.operation.available),
);
const currentSection = computed({
  get: () => props.section,
  set: (value) => emit('section', value),
});
const callerRows = computed(
  () => check.value?.callers.filter((caller) => caller.allowed || caller.rules?.length) || [],
);
const grantCandidates = computed(
  () => check.value?.callers.filter((caller) => caller.enabled && !caller.allowed) || [],
);
const credentialLabel = computed(() => {
  const status = check.value?.credential_status;
  return status
    ? {
        not_required: '无需上游凭据',
        missing_reference: '未配置凭据引用',
        missing_value: '凭据值未配置',
        configured: '已配置',
        unverified: '待核验',
      }[status]
    : '待检查';
});
const releaseLabel = computed(() =>
  available.value
    ? '已启用'
    : !apiEnabled.value
      ? '未启用'
      : !upstreamAddress.value
        ? `${environmentLabel.value}地址待配置`
        : '后端已停用',
);

const publishChecks = computed(() => {
  const result = check.value;
  if (!result) return [];
  const backendReady = result.provider_enabled && result.upstream_address_configured;
  const credentialsReady = ['configured', 'not_required'].includes(result.credential_status);
  const callersReady = result.authorized_count > 0;
  const reasons = (matches: (code: string) => boolean) =>
    result.blocking_reasons
      .filter(({ code }) => matches(code))
      .map(({ message }) => message)
      .join('，');
  return [
    {
      label: '后端连接',
      ready: backendReady,
      detail: backendReady
        ? `${environmentLabel.value}地址已配置，服务连接已启用`
        : reasons((code) => code === 'provider_disabled' || code === 'upstream_address_missing'),
      actionLabel: backendReady ? '查看连接' : '配置连接',
      action: !backendReady && props.service ? editBackend : () => emit('section', 'backend'),
    },
    {
      label: '上游凭据',
      ready: credentialsReady,
      detail: credentialsReady
        ? credentialLabel.value
        : reasons((code) => code.startsWith('credential_')),
      actionLabel: credentialsReady ? '查看凭据' : '配置凭据',
      action:
        result.credential_status === 'missing_value'
          ? openCredentials
          : () => emit('section', 'backend'),
    },
    {
      label: '调用授权',
      ready: callersReady,
      detail: callersReady
        ? `${result.authorized_count} 个调用方已获授权`
        : reasons((code) => code === 'caller_missing'),
      actionLabel: callersReady ? '查看授权' : '配置授权',
      action: () => emit('section', 'access'),
    },
  ];
});
const completedChecks = computed(() => publishChecks.value.filter((item) => item.ready).length);
const additionalBlockers = computed(
  () =>
    check.value?.blocking_reasons.filter(
      ({ code }) =>
        !['provider_disabled', 'upstream_address_missing', 'caller_missing'].includes(code) &&
        !code.startsWith('credential_'),
    ) || [],
);

onMounted(load);

function editBackend() {
  if (!props.service) return;
  void router.push({
    path: '/modules',
    query: {
      tab: 'modules',
      edit: props.service.id,
      kind: 'internal',
      operation: props.operation.key,
      section: props.section,
      provider: route.query.provider,
    },
  });
}

function editApi() {
  void router.push({
    path: '/modules',
    query: {
      tab: 'apis',
      edit: props.config.id,
      kind: 'internal',
      operation: props.operation.key,
      section: props.section,
      provider: route.query.provider,
    },
  });
}

function openCredentials() {
  void router.push({ path: '/settings', query: { tab: 'credentials' } });
}

function createCaller() {
  void router.push({
    path: '/systems',
    query: { action: 'create', intent: 'api-authorization', operation: props.operation.key },
  });
}

function openDebug() {
  if (available.value) {
    void router.push({
      path: '/playground',
      query: { operation: props.operation.key, direction: 'internal' },
    });
    return;
  }
  debugDialog.value = true;
}

async function copyPath() {
  try {
    await navigator.clipboard.writeText(gatewayPath.value);
    ElMessage.success('调用路径已复制');
  } catch {
    ElMessage.info('请选择路径手动复制');
  }
}

async function changePublication(enabled: boolean) {
  if (enabled && !check.value?.can_publish) return;
  const action = enabled ? '启用接口' : '停用接口';
  if (
    !(await confirm(
      enabled
        ? '启用后，获授权的系统即可通过网关调用此接口。'
        : '停用后，调用方将无法通过网关调用此接口。',
      action,
    ))
  )
    return;
  const result = await run(() => api.setApiPublication(props.config.id, enabled));
  if (!result?.ok) return;
  ElMessage.success(enabled ? '接口已启用' : '接口已停用');
  await load();
  emit('updated');
}

async function changeGrant(key: string, enabled: boolean) {
  const caller = check.value?.callers.find((item) => item.key === key);
  if (!caller) return;
  const action = enabled ? '授予调用权限' : '禁止调用';
  const warning = enabled && props.operation.sensitive ? '这是敏感接口，授权仅适用于此操作。' : '';
  const name = caller.display_name || caller.key;
  const message = enabled
    ? `允许「${name}」调用此接口？${warning}`
    : `确认禁止「${name}」调用此接口？`;
  if (!(await confirm(message, action))) return;
  const result = await run(() =>
    api.toggleGrant(key, props.operation.key, enabled, props.operation.sensitive),
  );
  if (!result) return;
  grantSystem.value = '';
  await load();
  const decision = check.value?.callers.find((item) => item.key === key);
  if (decision?.allowed === enabled) ElMessage.success(enabled ? '调用权限已保存' : '已禁止调用');
  else if (decision) ElMessage.warning(decision.reason || '请核对最终授权结果');
}
</script>

<template>
  <div class="api-detail">
    <el-button class="api-back" link :icon="ArrowLeft" @click="emit('back')"
      >返回 API 列表</el-button
    >

    <section class="panel api-summary" aria-labelledby="exposed-api-title">
      <div class="api-summary-heading">
        <div class="api-title">
          <div class="title-line">
            <h1 id="exposed-api-title">{{ operation.summary }}</h1>
            <el-tag :type="available ? 'success' : 'info'" effect="light">{{
              releaseLabel
            }}</el-tag>
          </div>
          <p v-if="operation.description">{{ operation.description }}</p>
        </div>
        <div class="api-actions">
          <el-button @click="editApi">编辑接口</el-button>
          <el-button @click="openDebug">调试接口</el-button>
        </div>
      </div>

      <div class="gateway-entry">
        <div class="gateway-label">
          网关调用入口
          <el-tag
            effect="plain"
            size="small"
            :type="environment === 'production' ? 'warning' : 'info'"
            >{{ environmentLabel }}环境</el-tag
          >
        </div>
        <el-button :icon="CopyDocument" text aria-label="复制调用路径" @click="copyPath"
          >复制路径</el-button
        >
        <div class="api-route-line">
          <span class="method">{{ operation.http_method || 'POST' }}</span
          ><code>{{ gatewayPath }}</code>
        </div>
      </div>

      <dl class="summary-fields">
        <div>
          <dt>后端服务</dt>
          <dd>{{ service?.name || operation.moduleKey || '未配置' }}</dd>
        </div>
        <div>
          <dt>操作键</dt>
          <dd>
            <code>{{ operation.key }}</code>
          </dd>
        </div>
        <div>
          <dt>获授权调用方</dt>
          <dd>{{ check ? `${check.authorized_count} 个` : '待检查' }}</dd>
        </div>
      </dl>
    </section>

    <section class="panel api-sections" aria-label="API 详情">
      <el-tabs v-model="currentSection" class="api-tabs">
        <el-tab-pane label="发布检查" name="release">
          <div class="section-body" v-loading="loading">
            <div class="section-heading">
              <div>
                <h2>发布检查</h2>
                <p>检查后端与调用权限。实际联调请使用测试环境和测试数据。</p>
              </div>
              <el-button :icon="Refresh" @click="load">重新检查</el-button>
            </div>
            <el-alert
              v-if="error"
              title="暂时无法读取发布检查"
              type="error"
              show-icon
              :closable="false"
            />
            <div class="publish-layout">
              <ul v-if="check" class="check-list" aria-label="启用前检查项">
                <li v-for="item in publishChecks" :key="item.label" class="check-row">
                  <el-icon :class="item.ready ? 'check-ready' : 'check-pending'" aria-hidden="true">
                    <component :is="item.ready ? CircleCheck : Warning" />
                  </el-icon>
                  <div class="check-content">
                    <div class="check-title">
                      <h3>{{ item.label }}</h3>
                      <el-tag v-if="!item.ready" size="small" type="warning" effect="plain"
                        >待处理</el-tag
                      >
                      <span v-else class="ready-label">已就绪</span>
                    </div>
                    <p>{{ item.detail }}</p>
                  </div>
                  <el-button
                    class="check-action"
                    :type="item.ready ? 'info' : 'primary'"
                    link
                    @click="item.action"
                    >{{ item.actionLabel }}</el-button
                  >
                </li>
              </ul>
              <aside class="publication-summary" aria-labelledby="publication-title">
                <div aria-live="polite" id="publication-readiness">
                  <h3 id="publication-title">{{ apiEnabled ? releaseLabel : '当前发布状态' }}</h3>
                  <p v-if="check" class="publish-count">
                    {{ completedChecks }} / {{ publishChecks.length }} 项已就绪
                  </p>
                  <p v-else>{{ error ? '重新检查后可查看启用条件。' : '正在检查当前环境…' }}</p>
                  <el-progress
                    v-if="check"
                    :percentage="Math.round((100 * completedChecks) / publishChecks.length)"
                    :show-text="false"
                    :stroke-width="5"
                    aria-label="启用前检查进度"
                  />
                </div>
                <p>
                  {{
                    check?.can_publish
                      ? `仅获授权的系统可在${environmentLabel}环境调用。`
                      : '处理待办项后，即可启用当前环境的接口。'
                  }}
                </p>
                <el-button
                  v-if="apiEnabled"
                  :disabled="loading"
                  :loading="busy"
                  @click="changePublication(false)"
                  >停用接口</el-button
                >
                <el-button
                  v-else
                  type="primary"
                  aria-describedby="publication-readiness"
                  :disabled="!check?.can_publish || loading"
                  :loading="busy"
                  @click="changePublication(true)"
                  >启用接口</el-button
                >
              </aside>
            </div>
            <el-alert
              v-for="reason in additionalBlockers"
              :key="reason.code"
              :title="reason.message"
              type="warning"
              show-icon
              :closable="false"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane label="接口契约" name="contract" lazy>
          <div class="section-body">
            <div class="section-heading">
              <div>
                <h2>请求与响应</h2>
                <p>调用方使用自己的 API Key 访问 Common。</p>
              </div>
              <el-button
                v-if="available"
                @click="
                  router.push({
                    path: '/docs',
                    query: { direction: 'internal', operation: operation.key },
                  })
                "
                >接入文档</el-button
              >
            </div>
            <code class="auth-example">Authorization: Bearer &lt;API Key&gt;</code>
            <h3>请求参数</h3>
            <SchemaFields :schema="operation.schema" />
            <h3>响应字段</h3>
            <ResponseFields :operation="operation" />
          </div>
        </el-tab-pane>

        <el-tab-pane label="后端连接" name="backend" lazy>
          <div class="section-body">
            <div class="section-heading">
              <div>
                <h2>Common 转发配置</h2>
                <p>Common 使用这里的上游凭据访问后端服务。</p>
              </div>
              <el-button v-if="service" @click="editBackend">编辑后端服务</el-button>
            </div>
            <dl class="backend-fields">
              <div>
                <dt>后端服务</dt>
                <dd>
                  {{ service?.name || operation.moduleKey || '未配置' }}
                  <code v-if="service">{{ service.key }}</code>
                </dd>
              </div>
              <div>
                <dt>上游路径</dt>
                <dd class="api-route-line">
                  <span class="method">{{ config.method }}</span
                  ><code>{{ config.path }}</code>
                </dd>
              </div>
              <template v-if="splitAddresses">
                <div v-for="target in ['test', 'production'] as const" :key="target">
                  <dt>
                    {{ target === 'test' ? '测试基础地址' : '正式基础地址'
                    }}<el-tag v-if="environment === target" effect="plain" size="small" type="info"
                      >当前环境</el-tag
                    >
                  </dt>
                  <dd>
                    <code
                      v-if="
                        target === 'test' ? service?.test_base_url : service?.production_base_url
                      "
                      >{{
                        target === 'test' ? service?.test_base_url : service?.production_base_url
                      }}</code
                    >
                    <span v-else>未配置</span>
                  </dd>
                </div>
              </template>
              <div v-else>
                <dt>共用基础地址</dt>
                <dd>
                  <code v-if="upstreamAddress">{{ upstreamAddress }}</code
                  ><span v-else>未配置</span>
                </dd>
              </div>
              <div>
                <dt>凭据状态</dt>
                <dd>
                  {{ credentialLabel
                  }}<code v-for="reference in check?.credential_refs" :key="reference">{{
                    reference
                  }}</code>
                </dd>
              </div>
            </dl>
            <div v-if="check && check.credential_status !== 'not_required'" class="api-actions">
              <el-button v-if="config.auth_type !== 'inherit'" link type="primary" @click="editApi"
                >编辑接口鉴权</el-button
              >
              <el-button
                v-if="check.credential_refs.length"
                link
                type="primary"
                @click="openCredentials"
                >管理凭据</el-button
              >
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="调用授权" name="access" lazy>
          <div class="section-body" v-loading="loading">
            <div class="section-heading">
              <div>
                <h2>调用方权限</h2>
                <p>
                  {{
                    operation.sensitive
                      ? '敏感接口需要按操作键精确授权。'
                      : '调用方须获得此操作的有效授权。'
                  }}
                </p>
              </div>
              <el-button
                @click="
                  router.push({
                    path: '/grants',
                    query: {
                      operation: operation.key,
                      platform: operation.platform,
                      direction: 'internal',
                    },
                  })
                "
                >查看规则</el-button
              >
            </div>
            <el-alert
              v-if="error"
              title="暂时无法读取调用授权"
              type="error"
              show-icon
              :closable="false"
            >
              <el-button link type="primary" @click="load">重新加载授权</el-button>
            </el-alert>
            <template v-if="check">
              <div class="grant-controls">
                <el-select
                  v-model="grantSystem"
                  filterable
                  clearable
                  placeholder="选择调用方"
                  aria-label="选择调用方"
                >
                  <el-option
                    v-for="caller in grantCandidates"
                    :key="caller.key"
                    :value="caller.key"
                    :label="`${caller.display_name || caller.key} (${caller.key})`"
                  />
                </el-select>
                <el-button
                  type="primary"
                  :disabled="!grantSystem || loading"
                  :loading="busy"
                  @click="changeGrant(grantSystem, true)"
                  >授予权限</el-button
                >
                <el-button link type="primary" @click="createCaller">新增调用方</el-button>
              </div>
              <p v-if="!callerRows.length" class="empty-callers">
                此接口还没有匹配的调用授权规则。
              </p>
              <ul v-else class="caller-list" aria-label="调用方授权结果">
                <li v-for="caller in callerRows" :key="caller.key" class="caller-row">
                  <div class="caller-details">
                    <div class="check-title">
                      <strong>{{ caller.display_name || caller.key }}</strong>
                      <el-tag
                        size="small"
                        :type="caller.allowed ? 'success' : 'info'"
                        effect="plain"
                      >
                        {{
                          !caller.enabled ? '调用方已停用' : caller.allowed ? '已授权' : '未授权'
                        }}
                      </el-tag>
                    </div>
                    <div class="caller-meta">
                      <code>{{ caller.key }}</code>
                      <span v-if="caller.allowed && caller.quota">配额 {{ caller.quota }}</span>
                      <span v-else-if="caller.enabled && caller.reason">{{ caller.reason }}</span>
                    </div>
                  </div>
                  <el-button
                    v-if="caller.enabled && caller.allowed"
                    link
                    type="danger"
                    :disabled="busy || loading"
                    @click="changeGrant(caller.key, false)"
                    >禁止调用</el-button
                  >
                </li>
              </ul>
            </template>
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-dialog v-model="debugDialog" title="调试接口" width="min(460px, 92vw)" append-to-body>
      <el-alert title="当前接口尚不可实际调用" type="info" :closable="false" show-icon />
      <p class="debug-note">可以先查看配置检查。在联调环境启用后，进入调试台使用测试数据。</p>
      <template #footer>
        <el-button @click="debugDialog = false">关闭</el-button>
        <el-button
          type="primary"
          @click="
            debugDialog = false;
            emit('section', 'release');
            load();
          "
          >查看配置检查</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.api-detail {
  display: grid;
  gap: 16px;
}
.api-back {
  justify-self: start;
}
.api-summary {
  padding: 22px 24px 0;
  margin-bottom: 0;
}
.api-summary-heading,
.section-heading {
  display: flex;
  justify-content: space-between;
  align-items: start;
  gap: 14px 20px;
  flex-wrap: wrap;
}
.api-title {
  flex: 1 1 310px;
  min-width: 0;
}
.title-line,
.api-actions,
.api-route-line,
.grant-controls {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.api-actions .el-button + .el-button {
  margin-left: 0;
}
.title-line h1 {
  color: var(--app-text);
  font-size: clamp(21px, 2vw, 25px);
  line-height: 1.35;
  text-wrap: balance;
}
.api-title p,
.section-heading p,
.empty-callers {
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.65;
  margin-top: 5px;
}
.gateway-entry {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 4px 8px;
  margin-top: 18px;
  padding: 12px 14px;
  border-radius: 8px;
  background: var(--app-primary-soft);
  border: 1px solid var(--app-border);
}
.gateway-entry > div {
  min-width: 0;
}
.gateway-entry .api-route-line {
  grid-column: 1 / -1;
}
.gateway-label {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  color: var(--app-text-secondary);
  font-size: 12px;
}
.api-route-line {
  gap: 9px;
}
.api-route-line code,
.summary-fields code,
.backend-fields code {
  overflow-wrap: anywhere;
}
.method {
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 650;
  white-space: nowrap;
}
.backend-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 24px;
  margin: 0;
  padding-block: 5px;
}
.summary-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 28px;
  margin: 0;
  padding: 12px 0 15px;
}
.summary-fields > div {
  display: flex;
  min-width: 0;
  align-items: baseline;
  gap: 6px;
}
.summary-fields dt {
  margin: 0;
  flex-shrink: 0;
}
dt {
  font-size: 12px;
  color: var(--app-text-muted);
  margin-bottom: 5px;
}
.backend-fields dt .el-tag {
  margin-left: 8px;
}
dd {
  margin: 0;
  color: var(--app-text);
  font-size: 13px;
  min-width: 0;
  overflow-wrap: anywhere;
}
.backend-fields dd > code + code,
.backend-fields dd > code:not(:first-child) {
  margin-left: 8px;
}
.api-tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 0 24px;
}
.api-tabs :deep(.el-tabs__item) {
  height: 49px;
}
.section-body {
  padding: 20px 24px;
  display: grid;
  gap: 15px;
}
.section-heading h2 {
  font-size: 16px;
  font-weight: 650;
}
.section-body h3 {
  font-size: 14px;
  font-weight: 600;
}
.publish-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(200px, 28%);
  align-items: start;
  gap: 18px;
}
.check-list,
.caller-list {
  margin: 0;
  padding: 0;
  list-style: none;
  border: 1px solid var(--app-border);
  border-radius: 8px;
}
.caller-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
}
.caller-row + .caller-row {
  border-top: 1px solid var(--app-divider);
}
.caller-details {
  min-width: 0;
  overflow-wrap: anywhere;
}
.caller-meta {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 4px 10px;
  margin-top: 5px;
  font-size: 12px;
  color: var(--app-text-secondary);
}
.caller-row > .el-button {
  flex-shrink: 0;
  min-height: 36px;
}
.check-row {
  display: grid;
  align-items: center;
  grid-template-columns: 20px minmax(0, 1fr) auto;
  gap: 10px;
  padding: 12px 14px;
  min-height: 66px;
}
.check-row + .check-row {
  border-top: 1px solid var(--app-divider);
}
.check-row > .el-icon {
  font-size: 18px;
}
.check-ready {
  color: var(--el-color-success-dark-2);
}
.check-pending {
  color: var(--el-color-warning-dark-2);
}
.check-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 8px;
}
.check-title .ready-label {
  font-size: 12px;
  color: var(--app-text-secondary);
}
.check-content p,
.publication-summary p {
  color: var(--app-text-secondary);
  font-size: 13px;
  line-height: 1.55;
  margin-top: 4px;
  overflow-wrap: anywhere;
}
.check-action {
  font-weight: 600;
  min-height: 36px;
}
.publication-summary {
  display: grid;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--app-border);
  border-radius: 8px;
  background: var(--app-surface-soft);
}
.publication-summary .publish-count {
  font-variant-numeric: tabular-nums;
  margin: 6px 0;
}
.publication-summary .el-button {
  justify-self: start;
}
.publication-summary > p {
  margin: 0;
}
@media (max-width: 780px) {
  .publish-layout {
    grid-template-columns: minmax(0, 1fr);
    gap: 12px;
  }
}
.grant-controls .el-select {
  width: min(100%, 270px);
}
.auth-example {
  justify-self: start;
  padding: 8px 10px;
  background: var(--app-surface-soft);
  border-radius: 5px;
  overflow-wrap: anywhere;
}
.debug-note {
  margin-top: 12px;
  line-height: 1.7;
}
@media (max-width: 650px) {
  .backend-fields {
    grid-template-columns: minmax(0, 1fr);
  }
  .api-summary {
    padding: 16px 16px 0;
  }
  .section-body {
    padding: 16px;
  }
  .api-tabs :deep(.el-tabs__header) {
    padding: 0 16px;
  }
  .api-tabs :deep(.el-tabs__item) {
    padding: 0 9px;
  }
  .check-row {
    gap: 8px;
    padding-inline: 11px;
  }
}
</style>
