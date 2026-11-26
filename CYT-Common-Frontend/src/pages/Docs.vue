<script setup lang="ts">
import { computed, nextTick, onMounted, ref, useId, watch } from 'vue';
import { useRoute, useRouter, type LocationQueryRaw } from 'vue-router';
import {
  ArrowRight,
  Connection,
  CopyDocument,
  Document,
  Download,
  Key,
  Link,
  Lock,
  Search,
  Tickets,
} from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { api } from '~/api';
import type { Operation } from '~/api/types';
import DocumentationExample from '~/components/DocumentationExample.vue';
import JsonViewer from '~/components/JsonViewer.vue';
import PageState from '~/components/PageState.vue';
import ListFilterBar from '~/components/ListFilterBar.vue';
import ResponseFields from '~/components/ResponseFields.vue';
import SchemaFields from '~/components/SchemaFields.vue';
import { useAction, useRequest } from '~/composables/useRequest';
import type { ExampleLanguage } from '~/utils/documentation';
import { operationDirection, type Direction } from '~/utils/integration-direction';
import {
  filterOperationModule,
  operationModule,
  operationModules,
} from '~/utils/operation-modules';

const props = defineProps<{ fixedSystem?: string; embedded?: boolean }>();
const route = useRoute();
const router = useRouter();
const queryText = (key: string) => {
  const value = route.query[key];
  return typeof value === 'string' ? value : '';
};
const system = computed(() => props.fixedSystem || queryText('system'));
const operationKey = computed(() => queryText('operation'));
const view = computed(() =>
  operationKey.value || queryText('view') === 'reference' ? 'reference' : 'quickstart',
);

const { data, loading, error, load } = useRequest(async () => {
  const [docs, callers, platforms, modules] = await Promise.all([
    api.docs(),
    api.systems(),
    api.platforms(),
    api.modules(),
  ]);
  return {
    operations: docs.operations,
    systems: callers.systems,
    platforms: platforms.platforms,
    modules: modules.items,
  };
});
const access = useRequest(async () => {
  const key = system.value;
  return { ...(await api.effective(key)), system: key };
});
const direction = computed<Direction>(() => {
  const value = queryText('direction');
  if (value === 'internal' || value === 'third_party') return value;
  const operation = data.value?.operations.find((item) => item.key === operationKey.value);
  return operation ? operationDirection(operation, data.value?.modules) : 'third_party';
});
const caller = computed(() => data.value?.systems.find((item) => item.key === system.value));
const accessData = computed(() =>
  access.data.value?.system === system.value ? access.data.value : undefined,
);
const pending = computed(
  () =>
    loading.value ||
    (!data.value && !error.value) ||
    (!!system.value && (access.loading.value || (!accessData.value && !access.error.value))),
);
const failed = computed(() => error.value || (!!system.value && access.error.value));
const ready = computed(() => !pending.value && !failed.value);
const supportsContracts = computed(
  () =>
    !!data.value?.operations.length &&
    data.value.operations.every((item) => item.integration_kind && item.response_schema),
);
const exportReady = computed(
  () => ready.value && supportsContracts.value && (!system.value || caller.value?.enabled),
);
const platformNames = computed(() =>
  Object.fromEntries(
    (data.value?.platforms || []).map((item) => [item.platform, item.display_name]),
  ),
);
const operations = computed(() => {
  const scoped = (data.value?.operations || []).filter(
    (item) => operationDirection(item, data.value?.modules) === direction.value,
  );
  if (!system.value) return scoped;
  const allowed = new Map(
    (accessData.value?.operations || [])
      .filter((item) => item.allowed)
      .map((item) => [item.operation, item]),
  );
  return scoped.flatMap((item) => {
    const decision = allowed.get(item.key);
    return decision ? [{ ...item, quota: decision.quota || item.quota }] : [];
  });
});
const platforms = computed(() => [...new Set(operations.value.map((item) => item.platform))]);
const search = ref('');
const platform = ref(queryText('platform'));
const category = ref(queryText('category'));
const platformOperations = computed(() =>
  operations.value.filter((item) => !platform.value || item.platform === platform.value),
);
const modules = computed(() => operationModules(platformOperations.value));
const filtered = computed(() => {
  const words = search.value.toLocaleLowerCase().trim().split(/\s+/).filter(Boolean);
  return filterOperationModule(platformOperations.value, category.value).filter((item) => {
    const text = [
      item.summary,
      item.key,
      item.path,
      item.http_method || 'POST',
      operationModule(item),
      item.platform,
      platformNames.value[item.platform],
    ]
      .join(' ')
      .toLocaleLowerCase();
    return words.every((word) => text.includes(word));
  });
});
const groups = computed(() => {
  const result = new Map<string, Operation[]>();
  for (const item of filtered.value) {
    const name = platform.value
      ? operationModule(item)
      : platformNames.value[item.platform] || item.platform;
    if (!result.has(name)) result.set(name, []);
    result.get(name)!.push(item);
  }
  return [...result];
});
const preferred = (items: Operation[]) =>
  items.find((item) => item.tags.includes('read')) || items[0];
const selected = computed(
  () => filtered.value.find((item) => item.key === operationKey.value) || preferred(filtered.value),
);
const unavailable = computed(
  () =>
    ready.value &&
    !!operationKey.value &&
    !operations.value.some((item) => item.key === operationKey.value),
);
const quickKey = ref('');
const quickOperation = computed(
  () => operations.value.find((item) => item.key === quickKey.value) || preferred(operations.value),
);
const language = ref<ExampleLanguage>('http');
const address = ref(window.location.origin);
const contractTab = ref('request');
const heading = ref<HTMLElement>();
const catalogList = ref<HTMLElement>();
const catalogId = useId();
const catalogExpanded = ref(!operationKey.value);
const { busy, run } = useAction();

watch(system, () => {
  if (system.value && data.value) void access.load();
});
watch(
  () => [queryText('platform'), queryText('category')],
  ([nextPlatform, nextCategory]) => {
    platform.value = nextPlatform || '';
    category.value = nextCategory || '';
  },
);
watch([operations, ready], () => {
  if (!ready.value) return;
  if (platform.value && !platforms.value.includes(platform.value)) platform.value = '';
  if (category.value && !modules.value.some((item) => item.name === category.value))
    category.value = '';
});
watch(
  () => selected.value?.key,
  () => {
    contractTab.value = 'request';
  },
);
watch(
  [selected, catalogList, catalogExpanded],
  () => {
    const list = catalogList.value;
    const active = list?.querySelector<HTMLElement>('[aria-current="page"]');
    if (!list || !active) return;
    const bounds = list.getBoundingClientRect();
    const item = active.getBoundingClientRect();
    if (item.top < bounds.top || item.bottom > bounds.bottom) {
      list.scrollTop += item.top - bounds.top - 10;
    }
  },
  { flush: 'post' },
);
watch([search, platform, category], () => {
  catalogExpanded.value = true;
});

async function reload() {
  await load();
  if (system.value && !error.value) await access.load();
}
onMounted(reload);

function location(query: LocationQueryRaw) {
  return { path: route.path, query: { ...route.query, ...query } };
}
function clearFilters() {
  search.value = '';
  platform.value = '';
  category.value = '';
  if (queryText('platform') || queryText('category'))
    void router.replace(location({ platform: undefined, category: undefined }));
}
function changeScope(query: LocationQueryRaw) {
  search.value = '';
  platform.value = '';
  category.value = '';
  quickKey.value = '';
  void router.push(
    location({
      direction: direction.value,
      view: view.value === 'reference' ? 'reference' : undefined,
      operation: undefined,
      platform: undefined,
      category: undefined,
      ...query,
    }),
  );
}
function viewLocation(target: 'reference' | 'quickstart') {
  const operation = target === 'reference' ? quickOperation.value : undefined;
  return location({
    view: target === 'reference' && !operation ? 'reference' : undefined,
    operation: operation?.key,
    direction: direction.value,
    platform: undefined,
    category: undefined,
  });
}
function operationLocation(operation: Operation) {
  return location({
    direction: direction.value,
    view: undefined,
    operation: operation.key,
    platform: platform.value || undefined,
    category: category.value || undefined,
  });
}
async function revealOperation(
  event: MouseEvent,
  navigate: (event: MouseEvent) => Promise<unknown>,
) {
  if (
    event.defaultPrevented ||
    event.button !== 0 ||
    event.metaKey ||
    event.ctrlKey ||
    event.shiftKey ||
    event.altKey
  )
    return;
  // Keep an explicitly opened interface visible even when the catalog is docked beside long fields.
  await navigate(event);
  catalogExpanded.value = false;
  await nextTick();
  const top = heading.value?.getBoundingClientRect().top;
  if (top !== undefined && (top < 80 || top > window.innerHeight * 0.62)) {
    heading.value?.scrollIntoView({ block: 'start' });
  }
  if (!catalogList.value?.getClientRects().length) {
    heading.value?.focus({ preventScroll: true });
  }
}
function changePlatform(value: unknown) {
  platform.value = typeof value === 'string' ? value : '';
  category.value = '';
}
function manage(tab: string) {
  void router.push(
    system.value
      ? {
          path: '/systems/' + encodeURIComponent(system.value),
          query: { tab, direction: direction.value },
        }
      : '/systems',
  );
}
function exportDocs() {
  if (!exportReady.value) return;
  return run(() =>
    system.value
      ? api.exportConsumerOpenApi(system.value, direction.value)
      : api.exportOpenApi(direction.value),
  );
}
function exportKit() {
  if (system.value ? !exportReady.value : !ready.value) return;
  return run(() =>
    system.value
      ? api.exportConsumerPackage(system.value, direction.value)
      : api.exportBuiltinPackage(),
  );
}
function debug(operation: Operation) {
  void router.push({
    path: '/playground',
    query: {
      operation: operation.key,
      direction: direction.value,
      ...(system.value ? { system: system.value } : {}),
    },
  });
}
async function copy(text: string, success: string) {
  try {
    await navigator.clipboard.writeText(text);
    ElMessage.success(success);
  } catch {
    ElMessage.warning('浏览器未开放剪贴板，链接可从浏览器地址栏复制，路径可选中后复制。');
  }
}
function copyLink(operation: Operation) {
  const target = new URL(router.resolve(operationLocation(operation)).href, window.location.href);
  void copy(target.href, '已复制接口链接');
}

const responseExample = {
  ok: true,
  data: { 业务数据: '以接口实际返回为准' },
  meta: { trace_id: '请求追踪标识', mock: false },
};
const problems = [
  ['401 / 403', '检查 API Key、调用方状态、接口授权和拒绝规则。'],
  ['422', '检查必填参数、数据类型与业务编号。'],
  ['409', '核对原请求结果与幂等键，重试沿用同一个业务请求号。'],
  ['429', '达到调用配额，按返回提示延后重试。'],
  ['502 / 503', '将 error.trace_id 交给管理员，核对上游连接、凭据和调用日志。'],
];
const commonRoutes = [
  ['GET /health', '检查网关与数据库连通状态。'],
  [
    'POST /integrations/batch',
    '提交 1–100 项 calls。每项提供 operation 和 params，并检查 data.results[]。',
  ],
  [
    'POST /integrations/shopify/stores/list',
    '传入最多 250 个店铺域名，需要 shopify.orders.list 授权。',
  ],
  [
    'POST /integrations/docusign/webhooks/events',
    'DocuSign 签名事件回调，也支持 /webhooks/{system}/events。',
  ],
  ['GET / POST /integrations/wecom/webhooks/{system}/events', '企业微信回调地址验证与事件推送。'],
  [
    'POST /integrations/{provider}/webhooks/{account}/dispute-closed',
    'Stripe 和 PayPal 争议关闭事件回调。',
  ],
];
</script>

<template>
  <div class="documentation-page">
    <header class="docs-masthead">
      <div>
        <h1 v-if="!embedded">接入文档</h1>
        <h2 v-else>调用文档</h2>
        <p>从第一个请求开始，查阅接口契约并交付接入资料。</p>
      </div>
      <div class="docs-actions">
        <el-button :icon="Download" :loading="busy" :disabled="!exportReady" @click="exportDocs">
          {{ system ? '专属 OpenAPI' : '导出 OpenAPI' }}
        </el-button>
        <el-button
          type="primary"
          :icon="Download"
          :loading="busy"
          :disabled="system ? !exportReady : !ready"
          @click="exportKit"
        >
          {{ system ? '下载专属接入包' : '下载内置接口接入包' }}
        </el-button>
      </div>
    </header>

    <section class="docs-workspace" aria-label="接入文档">
      <nav class="docs-nav" aria-label="文档导航">
        <RouterLink
          :to="viewLocation('quickstart')"
          :aria-current="view === 'quickstart' ? 'page' : undefined"
        >
          <el-icon aria-hidden="true"><Document /></el-icon>快速接入
        </RouterLink>
        <RouterLink
          :to="view === 'reference' ? location({}) : viewLocation('reference')"
          :aria-current="view === 'reference' ? 'page' : undefined"
        >
          <el-icon aria-hidden="true"><Tickets /></el-icon>接口参考
        </RouterLink>
      </nav>

      <div class="docs-scope">
        <div class="docs-scope-fields">
          <label class="docs-scope-field">
            <span>调用方向</span>
            <el-select
              :model-value="direction"
              aria-label="文档调用方向"
              @change="changeScope({ direction: $event })"
            >
              <el-option label="第三方服务" value="third_party" />
              <el-option label="自有系统对外能力" value="internal" />
            </el-select>
          </label>
          <label v-if="!fixedSystem" class="docs-scope-field">
            <span>调用方</span>
            <el-select
              :model-value="system"
              :empty-values="[null, undefined]"
              filterable
              aria-label="文档调用方"
              @change="changeScope({ system: $event || undefined })"
            >
              <el-option label="全部接口" value="" />
              <el-option
                v-for="item in data?.systems || []"
                :key="item.key"
                :value="item.key"
                :label="item.display_name + ' · ' + item.key + (item.enabled ? '' : ' · 已停用')"
              />
            </el-select>
          </label>
          <span v-else class="docs-fixed-scope">{{ caller?.display_name || system }}</span>
        </div>
        <div class="docs-scope-aside">
          <span v-if="ready">{{ operations.length }} 项{{ system ? '已授权' : '' }}接口</span>
          <el-button v-if="system" link type="primary" :icon="Lock" @click="manage('access')">
            配置授权
          </el-button>
        </div>
      </div>

      <div v-if="(caller && !caller.enabled) || (data && !supportsContracts)" class="docs-notices">
        <el-alert
          v-if="caller && !caller.enabled"
          title="此调用方已停用，启用后才能调用接口或导出专属资料。"
          type="warning"
          :closable="false"
          show-icon
        />
        <el-alert
          v-if="data && !supportsContracts"
          title="当前后端尚未加载方向与响应契约，请更新服务后再按方向导出。"
          type="info"
          :closable="false"
          show-icon
        />
      </div>

      <PageState :loading="pending" :error="failed" @retry="reload">
        <div v-if="view === 'quickstart'" class="docs-quickstart">
          <section class="quickstart-layout" aria-label="发起第一个请求">
            <div class="quickstart-intro">
              <h2>发起第一个请求</h2>
              <p>选一个已获授权的只读接口，在业务后端验证连通和返回结果。</p>
              <ol class="docs-steps">
                <li>
                  <div>
                    <h3>准备调用密钥与授权</h3>
                    <p v-if="caller">
                      {{ caller.display_name }}有 {{ caller.apikey_count }} 把有效密钥。
                    </p>
                    <p v-else>为业务系统申请独立 API Key，并授权所需接口。</p>
                    <el-button link type="primary" :icon="Key" @click="manage('keys')">
                      {{ system ? '管理调用密钥' : '前往调用方管理' }}
                    </el-button>
                  </div>
                </li>
                <li>
                  <div>
                    <h3>设置网关地址并复制示例</h3>
                    <p>
                      将密钥保存在后端环境变量 <code>CYT_API_KEY</code>，替换示例中的业务参数。SDK
                      可从接入包获取。
                    </p>
                  </div>
                </li>
                <li>
                  <div>
                    <h3>核对响应</h3>
                    <p>
                      检查 <code>ok</code> 和 <code>meta.mock</code>，逐项处理
                      <code>data.partial</code> 或 <code>errors</code>。
                    </p>
                  </div>
                </li>
              </ol>
            </div>
            <div v-if="quickOperation" class="quickstart-example">
              <label>
                <span>选一个接口</span>
                <el-select
                  :model-value="quickOperation.key"
                  filterable
                  aria-label="首个调用接口"
                  @change="quickKey = $event"
                >
                  <el-option
                    v-for="item in operations"
                    :key="item.key"
                    :value="item.key"
                    :label="item.summary + ' · ' + item.key"
                  />
                </el-select>
              </label>
              <p
                v-if="quickOperation.sensitive || quickOperation.tags.includes('write')"
                class="docs-callout"
              >
                写操作运行前请核对业务参数。接口要求幂等键时，使用稳定的业务请求号。
              </p>
              <DocumentationExample
                v-model:address="address"
                v-model:language="language"
                :operation="quickOperation"
              />
              <div class="quickstart-links">
                <RouterLink :to="operationLocation(quickOperation)"
                  >查看参数与响应 <el-icon aria-hidden="true"><ArrowRight /></el-icon
                ></RouterLink>
                <el-button link type="primary" @click="debug(quickOperation)">打开调试</el-button>
              </div>
            </div>
            <div v-else class="docs-empty">
              <el-icon aria-hidden="true"><Connection /></el-icon>
              <h3>当前范围暂无接口</h3>
              <p>
                {{
                  system
                    ? '给调用方分配接口后即可查看示例。'
                    : '切换调用方向，或前往服务管理查看可用接口。'
                }}
              </p>
              <el-button v-if="system" @click="manage('access')">配置能力授权</el-button>
              <RouterLink v-else :to="direction === 'internal' ? '/exposed' : '/platforms'"
                >查看服务与能力</RouterLink
              >
            </div>
          </section>
          <div class="docs-quickstart-secondary">
            <section class="docs-section">
              <h2>如何确认调用成功</h2>
              <p>
                SDK 成功时直接返回 <code>data</code>。分页接口用 <code>page.has_more</code> 和
                <code>next_cursor</code> 判断是否还有结果。
              </p>
              <JsonViewer :value="responseExample" />
            </section>
            <section class="docs-section">
              <h2>常见错误</h2>
              <dl class="docs-pairs">
                <div v-for="[code, detail] in problems" :key="code">
                  <dt>
                    <code>{{ code }}</code>
                  </dt>
                  <dd>{{ detail }}</dd>
                </div>
              </dl>
            </section>
          </div>
          <details v-if="!system" class="docs-common">
            <summary>网关通用路由与回调</summary>
            <dl class="docs-pairs">
              <div v-for="[path, detail] in commonRoutes" :key="path">
                <dt>
                  <code>{{ path }}</code>
                </dt>
                <dd>{{ detail }}</dd>
              </div>
            </dl>
          </details>
        </div>

        <div v-else class="docs-reference">
          <aside class="docs-catalog" aria-label="接口目录">
            <div class="docs-catalog-controls">
              <div class="docs-catalog-title">
                <h2>接口目录</h2>
                <button
                  v-if="groups.length"
                  class="docs-catalog-toggle"
                  type="button"
                  :aria-controls="catalogId"
                  :aria-expanded="catalogExpanded"
                  @click="catalogExpanded = !catalogExpanded"
                >
                  {{ catalogExpanded ? '收起列表' : '展开列表' }}
                </button>
              </div>
              <ListFilterBar class="docs-catalog-filter" label="筛选接口目录">
                <el-input
                  v-model="search"
                  class="list-filter-search"
                  :prefix-icon="Search"
                  type="search"
                  clearable
                  placeholder="搜索名称、路径或标识"
                  aria-label="搜索文档接口"
                />
                <el-select
                  :model-value="platform"
                  :empty-values="[null, undefined]"
                  aria-label="文档平台"
                  filterable
                  @change="changePlatform"
                >
                  <el-option label="全部平台" value="" />
                  <el-option
                    v-for="item in platforms"
                    :key="item"
                    :value="item"
                    :label="platformNames[item] || item"
                  />
                </el-select>
                <el-select
                  v-model="category"
                  :empty-values="[null, undefined]"
                  aria-label="文档业务模块"
                  filterable
                >
                  <el-option label="全部模块" value="" />
                  <el-option
                    v-for="item in modules"
                    :key="item.name"
                    :value="item.name"
                    :label="item.name + ' · ' + item.total"
                  />
                </el-select>
                <template #actions>
                  <span class="list-filter-count" role="status"
                    >共 <strong>{{ filtered.length }}</strong> 项</span
                  >
                </template>
              </ListFilterBar>
            </div>
            <nav
              v-if="groups.length"
              :id="catalogId"
              ref="catalogList"
              class="docs-catalog-list"
              :class="{ 'is-collapsed': !catalogExpanded }"
              aria-label="接口列表"
            >
              <section v-for="[name, items] in groups" :key="name" class="docs-catalog-group">
                <h3>{{ name }}</h3>
                <RouterLink
                  v-for="item in items"
                  :key="item.key"
                  :to="operationLocation(item)"
                  custom
                  v-slot="{ href, navigate }"
                >
                  <a
                    :href="href"
                    :aria-current="selected?.key === item.key ? 'page' : undefined"
                    @click="revealOperation($event, navigate)"
                  >
                    <span>{{ item.summary }}</span
                    ><code>{{ item.key }}</code>
                  </a>
                </RouterLink>
              </section>
            </nav>
          </aside>

          <div class="docs-reference-body">
            <el-alert
              v-if="unavailable"
              class="docs-unavailable"
              title="链接中的接口在当前调用方向或授权范围内不可用。"
              type="info"
              :closable="false"
              show-icon
            />
            <article v-if="selected" class="docs-operation">
              <header class="docs-operation-header">
                <div class="docs-operation-context">
                  {{ platformNames[selected.platform] || selected.platform
                  }}<el-icon aria-hidden="true"><ArrowRight /></el-icon
                  >{{ operationModule(selected) }}
                </div>
                <h2 ref="heading" tabindex="-1">{{ selected.summary }}</h2>
                <div class="docs-endpoint">
                  <span class="docs-method" :class="{ 'is-put': selected.http_method === 'PUT' }">{{
                    selected.http_method || 'POST'
                  }}</span>
                  <code>{{ selected.path }}</code>
                  <button
                    type="button"
                    aria-label="复制请求路径"
                    title="复制请求路径"
                    @click="copy(selected.path, '已复制请求路径')"
                  >
                    <el-icon aria-hidden="true"><CopyDocument /></el-icon>
                  </button>
                </div>
                <p v-if="selected.description" class="docs-operation-description">
                  {{ selected.description }}
                </p>
                <div class="docs-operation-tools">
                  <div class="docs-operation-meta">
                    <span v-if="selected.quota"
                      >配额 <code>{{ selected.quota }}</code></span
                    >
                    <span v-if="selected.tags.includes('idempotency_required')">必填幂等键</span>
                    <span v-else-if="selected.idempotent">支持幂等键</span>
                    <span v-if="selected.sensitive">敏感操作</span>
                    <span v-if="selected.deprecated">已弃用</span>
                  </div>
                  <div class="docs-actions">
                    <el-button link type="primary" :icon="Link" @click="copyLink(selected)"
                      >复制链接</el-button
                    >
                    <el-button link type="primary" :icon="Connection" @click="debug(selected)"
                      >打开调试</el-button
                    >
                  </div>
                </div>
              </header>
              <div class="docs-operation-body">
                <div class="docs-contract">
                  <el-tabs v-model="contractTab" aria-label="接口契约">
                    <el-tab-pane
                      :label="selected.transport === 'raw_binary' ? 'Query 参数' : '请求参数'"
                      name="request"
                    >
                      <p v-if="selected.transport === 'raw_binary'" class="docs-callout">
                        请求体使用原始文件流。将 <code>Content-Type</code> 设为文件
                        MIME，并填写准确的 <code>Content-Length</code>，避免使用 multipart 或
                        chunked。
                      </p>
                      <SchemaFields :schema="selected.schema" readable />
                      <details class="docs-raw-schema">
                        <summary>完整请求 Schema</summary>
                        <JsonViewer :value="selected.schema" />
                      </details>
                    </el-tab-pane>
                    <el-tab-pane label="响应字段" name="response" lazy>
                      <ResponseFields :key="selected.key" :operation="selected" readable />
                    </el-tab-pane>
                  </el-tabs>
                </div>
                <div class="docs-operation-example">
                  <DocumentationExample
                    v-model:address="address"
                    v-model:language="language"
                    :operation="selected"
                  />
                </div>
              </div>
            </article>
            <div v-else class="docs-empty">
              <el-icon aria-hidden="true"><Search /></el-icon>
              <h2>{{ operations.length ? '没有匹配的接口' : '当前范围暂无接口' }}</h2>
              <p>
                {{
                  operations.length
                    ? '试试更短的关键词，或清除平台与模块筛选。'
                    : system
                      ? '给调用方分配此方向的接口后即可查阅。'
                      : '切换调用方向，查看其他接口。'
                }}
              </p>
              <el-button v-if="operations.length" @click="clearFilters">清除筛选</el-button>
              <el-button v-else-if="system" @click="manage('access')">配置能力授权</el-button>
            </div>
          </div>
        </div>
      </PageState>
    </section>
  </div>
</template>

<style scoped src="./documentation.css"></style>
