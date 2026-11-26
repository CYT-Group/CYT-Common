<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  Connection,
  Document,
  EditPen,
  Key,
  List,
  Plus,
  Refresh,
  Search,
} from '@element-plus/icons-vue';
import { api } from '~/api';
import type { PlatformModule } from '~/api/types';
import { useRequest } from '~/composables/useRequest';
import { buildCatalog, type CatalogOperation } from '~/utils/authoring';
import { operationDirection } from '~/utils/integration-direction';
import ExposedApiDetail from '~/components/ExposedApiDetail.vue';
import PageHeader from '~/components/PageHeader.vue';
import PageState from '~/components/PageState.vue';

const route = useRoute();
const router = useRouter();
const { data, loading, error, load } = useRequest(async () => {
  const [registry, custom, modules, environments] = await Promise.all([
    api.registry(),
    api.customApis(),
    api.modules(),
    api.environments(),
  ]);
  return {
    production:
      ['prod', 'production', 'prd'].includes(environments.current) ||
      environments.items.some((item) => item.key === environments.current && item.is_production),
    services: modules.items.filter((item) => item.service_kind === 'internal'),
    configs: custom.items,
    operations: buildCatalog(registry.operations, custom.items, modules.items).filter(
      (item) => operationDirection(item, modules.items) === 'internal',
    ),
  };
});

const sections = ['release', 'contract', 'backend', 'access'];
const pageSize = 15;
const query = ref('');
const status = ref('');
const category = ref('');
const page = ref(1);
const chooseService = ref(false);
const newService = ref('');
const services = computed(() => data.value?.services || []);
const environmentLabel = computed(() => (data.value?.production ? '正式' : '测试'));
const operations = computed(() => data.value?.operations || []);
const serviceByKey = computed(() => new Map(services.value.map((item) => [item.key, item])));
const configById = computed(() => new Map(data.value?.configs.map((item) => [item.id, item])));
const view = computed(() => (route.query.view === 'providers' ? 'providers' : 'interfaces'));
const provider = computed({
  get: () => (typeof route.query.provider === 'string' ? route.query.provider : ''),
  set: (key) => void router.replace({ path: '/exposed', query: { provider: key || undefined } }),
});
const operationKey = computed(() =>
  typeof route.query.operation === 'string' ? route.query.operation : '',
);
const section = computed(() => {
  const value = route.query.section;
  return typeof value === 'string' && sections.includes(value) ? value : 'release';
});
const selected = computed(() => operations.value.find((item) => item.key === operationKey.value));
const selectedConfig = computed(() =>
  selected.value?.configId ? configById.value.get(selected.value.configId) : undefined,
);
const categories = computed(() =>
  [
    ...new Set(
      operations.value
        .filter((item) => !provider.value || item.moduleKey === provider.value)
        .map((item) => item.category)
        .filter(Boolean),
    ),
  ].sort(),
);
const search = computed(() => query.value.trim().toLowerCase());
const filtered = computed(() =>
  operations.value.filter(
    (item) =>
      (!provider.value || item.moduleKey === provider.value) &&
      (!category.value || item.category === category.value) &&
      (!status.value || (releaseStatus(item) === '已启用') === (status.value === 'enabled')) &&
      [item.key, item.summary, item.description, item.path, providerName(item)]
        .join(' ')
        .toLowerCase()
        .includes(search.value),
  ),
);
const filteredServices = computed(() =>
  services.value.filter(
    (item) =>
      (!status.value || Boolean(item.enabled) === (status.value === 'enabled')) &&
      [
        item.name,
        item.key,
        item.description,
        item.base_url,
        item.test_base_url,
        item.production_base_url,
      ]
        .join(' ')
        .toLowerCase()
        .includes(search.value),
  ),
);
const count = computed(() =>
  view.value === 'providers' ? filteredServices.value.length : filtered.value.length,
);
const pagedOperations = computed(() =>
  filtered.value.slice((page.value - 1) * pageSize, page.value * pageSize),
);
const pagedServices = computed(() =>
  filteredServices.value.slice((page.value - 1) * pageSize, page.value * pageSize),
);
const hasFilters = computed(() =>
  Boolean(query.value || status.value || category.value || provider.value),
);

onMounted(load);
watch([query, status, category, provider], () => {
  page.value = 1;
});
watch(provider, () => {
  category.value = '';
});
watch(view, () => {
  query.value = '';
  status.value = '';
  category.value = '';
  page.value = 1;
});
watch(count, (total) => {
  page.value = Math.min(page.value, Math.max(1, Math.ceil(total / pageSize)));
});

function providerName(op: CatalogOperation) {
  return serviceByKey.value.get(op.moduleKey || '')?.name || op.moduleKey || op.platform;
}
function releaseStatus(op: CatalogOperation) {
  const config = configById.value.get(op.configId || 0);
  const service = serviceByKey.value.get(op.moduleKey || '');
  if (
    isEnvironmentScoped(service) &&
    !(data.value?.production ? service?.production_base_url : service?.test_base_url)
  )
    return `${environmentLabel.value}地址待配置`;
  if (op.available) return '已启用';
  if (!isEnvironmentScoped(service) && !(config?.base_url || service?.base_url))
    return '待配置后端';
  return config?.enabled && !service?.enabled ? '后端已停用' : '未启用';
}
function statusTone(op: CatalogOperation) {
  const current = releaseStatus(op);
  return current === '已启用' ? 'success' : current === '未启用' ? 'info' : 'warning';
}
function isEnvironmentScoped(service?: PlatformModule) {
  return service?.test_base_url != null || service?.production_base_url != null;
}
function serviceCount(key: string) {
  return operations.value.filter((item) => item.moduleKey === key).length;
}
function resetFilters() {
  query.value = '';
  status.value = '';
  category.value = '';
  if (provider.value) provider.value = '';
}
function changeView(value: string | number) {
  if (view.value !== value)
    void router.push({
      path: '/exposed',
      query: value === 'providers' ? { view: 'providers' } : {},
    });
}
function detailLink(op: CatalogOperation, section?: 'access') {
  return {
    path: '/exposed',
    query: { provider: provider.value || undefined, operation: op.key, section },
  };
}
function goToList() {
  void router.push({ path: '/exposed', query: { provider: provider.value || undefined } });
}
function selectSection(value: string) {
  if (value === section.value || !sections.includes(value)) return;
  void router.replace({
    path: '/exposed',
    query: { ...route.query, section: value === 'release' ? undefined : value },
  });
}
function createService() {
  void router.push({
    path: '/modules',
    query: { tab: 'modules', action: 'create', kind: 'internal', view: view.value },
  });
}
function editService(service: PlatformModule) {
  void router.push({
    path: '/modules',
    query: { tab: 'modules', edit: service.id, kind: 'internal', view: 'providers' },
  });
}
function createApi(service?: string) {
  if (!services.value.length) {
    createService();
    return;
  }
  const target = service || (services.value.length === 1 ? services.value[0]!.key : '');
  if (!target) {
    newService.value = provider.value;
    chooseService.value = true;
    return;
  }
  chooseService.value = false;
  void router.push({
    path: '/modules',
    query: {
      tab: 'apis',
      action: 'create',
      platform: target,
      kind: 'internal',
      view: view.value,
      provider: provider.value || undefined,
    },
  });
}
</script>

<template>
  <div class="exposed-page">
    <PageState :loading="!data && !error" :error="error" @retry="load">
      <ExposedApiDetail
        v-if="selected && selectedConfig"
        :key="selected.key"
        :operation="selected"
        :config="selectedConfig"
        :service="serviceByKey.get(selected.moduleKey || '')"
        :environment="data?.production ? 'production' : 'test'"
        :section="section"
        @back="goToList"
        @section="selectSection"
        @updated="load"
      />
      <div v-else-if="operationKey" class="panel">
        <el-result icon="warning" title="找不到该接口">
          <template #extra><el-button @click="goToList">返回 API 列表</el-button></template>
        </el-result>
      </div>
      <template v-else>
        <PageHeader title="API 管理" description="管理对接业务系统的 API、调用授权和启用状态。">
          <el-button
            :icon="Document"
            @click="router.push({ path: '/docs', query: { direction: 'internal' } })"
            >接入文档</el-button
          >
          <el-button v-if="view === 'providers'" type="primary" :icon="Plus" @click="createService"
            >登记后端服务</el-button
          >
          <el-button v-else type="primary" :icon="Plus" @click="createApi()">{{
            services.length ? '新增 API' : '登记后端服务'
          }}</el-button>
        </PageHeader>

        <section class="panel catalog-panel" v-loading="loading" aria-label="API 管理列表">
          <div class="catalog-heading">
            <el-tabs :model-value="view" class="catalog-tabs" @tab-change="changeView">
              <el-tab-pane name="interfaces" :label="'API 列表 (' + operations.length + ')'" />
              <el-tab-pane name="providers" :label="'后端服务 (' + services.length + ')'" />
            </el-tabs>
            <div class="catalog-actions">
              <el-tag :type="data?.production ? 'warning' : 'info'" effect="plain"
                >当前环境 · {{ environmentLabel }}</el-tag
              >
              <el-button :icon="Refresh" text circle aria-label="刷新列表" @click="load" />
            </div>
          </div>
          <div
            v-if="view === 'interfaces' ? operations.length : services.length"
            class="catalog-filters"
          >
            <el-input
              v-model="query"
              :prefix-icon="Search"
              clearable
              name="api-catalog-search"
              autocomplete="off"
              :placeholder="
                view === 'interfaces'
                  ? '搜索 API 名称、路径或操作键'
                  : '搜索后端服务名称、地址或标识'
              "
              :aria-label="view === 'interfaces' ? '搜索 API' : '搜索后端服务'"
            />
            <el-select
              v-if="view === 'interfaces' && services.length > 1"
              v-model="provider"
              filterable
              clearable
              placeholder="全部后端"
              aria-label="筛选后端服务"
            >
              <el-option
                v-for="service in services"
                :key="service.key"
                :value="service.key"
                :label="service.name"
              />
            </el-select>
            <el-select
              v-if="view === 'interfaces' && categories.length > 1"
              v-model="category"
              clearable
              placeholder="全部业务模块"
              aria-label="筛选业务模块"
            >
              <el-option v-for="item in categories" :key="item" :value="item" :label="item" />
            </el-select>
            <el-select v-model="status" clearable placeholder="全部状态" aria-label="筛选状态">
              <el-option value="enabled" label="已启用" /><el-option
                value="disabled"
                label="未启用"
              />
            </el-select>
            <el-button v-if="hasFilters && count" text @click="resetFilters">清除筛选</el-button>
          </div>

          <template v-if="view === 'interfaces'">
            <template v-if="pagedOperations.length">
              <el-table :data="pagedOperations" row-key="key" class="catalog-table">
                <el-table-column label="API 名称" min-width="205">
                  <template #default="{ row }">
                    <RouterLink class="api-name" :to="detailLink(row)">{{
                      row.summary
                    }}</RouterLink>
                    <p class="subtext mono">{{ row.key }}</p>
                  </template>
                </el-table-column>
                <el-table-column label="网关调用路径" min-width="240">
                  <template #default="{ row }"
                    ><div class="entry">
                      <span class="method">{{ row.http_method || 'POST' }}</span
                      ><code>{{ row.path }}</code>
                    </div></template
                  >
                </el-table-column>
                <el-table-column label="后端服务" min-width="140">
                  <template #default="{ row }"
                    >{{ providerName(row) }}
                    <p v-if="row.category" class="subtext">{{ row.category }}</p></template
                  >
                </el-table-column>
                <el-table-column label="发布状态" width="116">
                  <template #default="{ row }">
                    <el-tag size="small" effect="light" :type="statusTone(row)">
                      {{ releaseStatus(row) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="192">
                  <template #default="{ row }">
                    <div class="row-actions">
                      <RouterLink class="catalog-link catalog-action" :to="detailLink(row)"
                        >发布检查</RouterLink
                      >
                      <RouterLink class="catalog-link" :to="detailLink(row, 'access')">
                        {{ row.sensitive ? '精确授权' : '调用方授权' }}
                      </RouterLink>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
              <ul class="compact-catalog" aria-label="API 列表">
                <li v-for="item in pagedOperations" :key="item.key" class="compact-record">
                  <div class="compact-title">
                    <RouterLink class="api-name" :to="detailLink(item)">{{
                      item.summary
                    }}</RouterLink>
                    <el-tag size="small" effect="light" :type="statusTone(item)">{{
                      releaseStatus(item)
                    }}</el-tag>
                  </div>
                  <div class="compact-meta">
                    <span>{{ providerName(item) }}</span
                    ><code>{{ item.key }}</code>
                  </div>
                  <div class="entry compact-path">
                    <span class="method">{{ item.http_method || 'POST' }}</span
                    ><code>{{ item.path }}</code>
                  </div>
                  <div class="row-actions">
                    <RouterLink class="catalog-link compact-action" :to="detailLink(item)"
                      ><el-icon aria-hidden="true"><List /></el-icon>发布检查</RouterLink
                    >
                    <RouterLink class="catalog-link compact-action" :to="detailLink(item, 'access')"
                      ><el-icon aria-hidden="true"><Key /></el-icon
                      >{{ item.sensitive ? '精确授权' : '调用方授权' }}</RouterLink
                    >
                  </div>
                </li>
              </ul>
            </template>
            <el-empty v-else :description="operations.length ? '没有匹配的 API' : '暂无对外 API'">
              <el-button v-if="operations.length" @click="resetFilters">清除筛选</el-button>
              <el-button v-else type="primary" @click="createApi()">{{
                services.length ? '新增 API' : '登记后端服务'
              }}</el-button>
            </el-empty>
          </template>

          <template v-else>
            <template v-if="pagedServices.length">
              <el-table :data="pagedServices" row-key="key" class="catalog-table">
                <el-table-column label="后端服务" min-width="215">
                  <template #default="{ row }">
                    <div class="service-name">
                      <span class="service-icon"
                        ><el-icon><Connection /></el-icon
                      ></span>
                      <div>
                        <strong>{{ row.name }}</strong>
                        <p class="subtext mono">{{ row.key }}</p>
                      </div>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="基础地址" min-width="280">
                  <template #default="{ row }">
                    <div v-if="isEnvironmentScoped(row)" class="environment-addresses">
                      <span>测试</span><code v-if="row.test_base_url">{{ row.test_base_url }}</code
                      ><span v-else class="subtext">未配置</span> <span>正式</span
                      ><code v-if="row.production_base_url">{{ row.production_base_url }}</code
                      ><span v-else class="subtext">未配置</span>
                    </div>
                    <code v-else-if="row.base_url">{{ row.base_url }}</code>
                    <span v-else class="subtext">未配置地址</span>
                  </template>
                </el-table-column>
                <el-table-column label="关联 API" width="105">
                  <template #default="{ row }">
                    <RouterLink
                      v-if="serviceCount(row.key)"
                      :to="{ path: '/exposed', query: { provider: row.key } }"
                      >{{ serviceCount(row.key) }}</RouterLink
                    >
                    <span v-else>0</span>
                  </template>
                </el-table-column>
                <el-table-column label="连接状态" width="110">
                  <template #default="{ row }"
                    ><el-tag size="small" effect="light" :type="row.enabled ? 'success' : 'info'">{{
                      row.enabled ? '已启用' : '未启用'
                    }}</el-tag></template
                  >
                </el-table-column>
                <el-table-column label="操作" width="216">
                  <template #default="{ row }">
                    <div class="row-actions service-actions">
                      <el-button :icon="EditPen" @click="editService(row)">编辑连接</el-button>
                      <el-button text :icon="Plus" @click="createApi(row.key)">新增 API</el-button>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
              <ul class="compact-catalog" aria-label="后端服务列表">
                <li v-for="item in pagedServices" :key="item.key" class="compact-record">
                  <div class="compact-title">
                    <strong>{{ item.name }}</strong>
                    <el-tag size="small" effect="light" :type="item.enabled ? 'success' : 'info'">{{
                      item.enabled ? '已启用' : '未启用'
                    }}</el-tag>
                  </div>
                  <div class="compact-meta">
                    <code>{{ item.key }}</code
                    ><RouterLink
                      v-if="serviceCount(item.key)"
                      class="catalog-link"
                      :to="{ path: '/exposed', query: { provider: item.key } }"
                      >{{ serviceCount(item.key) }} 个关联 API</RouterLink
                    >
                  </div>
                  <dl class="compact-addresses">
                    <template v-if="isEnvironmentScoped(item)">
                      <dt>测试</dt>
                      <dd>{{ item.test_base_url || '未配置' }}</dd>
                      <dt>正式</dt>
                      <dd>{{ item.production_base_url || '未配置' }}</dd>
                    </template>
                    <template v-else
                      ><dt>基础地址</dt>
                      <dd>{{ item.base_url || '未配置' }}</dd></template
                    >
                  </dl>
                  <div class="row-actions service-actions">
                    <el-button :icon="EditPen" @click="editService(item)">编辑连接</el-button>
                    <el-button text :icon="Plus" @click="createApi(item.key)">新增 API</el-button>
                  </div>
                </li>
              </ul>
            </template>
            <el-empty v-else :description="services.length ? '没有匹配的后端服务' : '暂无后端服务'">
              <el-button v-if="services.length" @click="resetFilters">清除筛选</el-button>
              <el-button v-else type="primary" @click="createService">登记后端服务</el-button>
            </el-empty>
          </template>

          <div v-if="count" class="catalog-footer">
            <span>共 {{ count }} 个{{ view === 'providers' ? '后端服务' : 'API' }}</span>
            <el-pagination
              v-if="count > pageSize"
              v-model:current-page="page"
              :page-size="pageSize"
              :total="count"
              layout="prev,pager,next"
            />
          </div>
        </section>
      </template>
    </PageState>

    <el-dialog v-model="chooseService" title="选择后端服务" width="min(440px, 92vw)">
      <el-select
        v-model="newService"
        filterable
        placeholder="选择后端服务"
        aria-label="选择后端服务"
        class="service-select"
      >
        <el-option
          v-for="service in services"
          :key="service.key"
          :value="service.key"
          :label="service.name"
        />
      </el-select>
      <template #footer>
        <el-button @click="chooseService = false">取消</el-button>
        <el-button type="primary" :disabled="!newService" @click="createApi(newService)"
          >继续</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.exposed-page {
  display: grid;
  gap: 16px;
}
.exposed-page :deep(.page-header) {
  margin-bottom: 10px;
}
.catalog-panel {
  min-width: 0;
  container: api-catalog / inline-size;
}
.catalog-heading,
.catalog-filters,
.catalog-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 22px;
}
.catalog-heading,
.catalog-footer {
  justify-content: space-between;
}
.catalog-heading {
  border-bottom: 1px solid var(--app-divider);
}
.catalog-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}
.environment-addresses {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  align-items: baseline;
  gap: 3px 8px;
}
.environment-addresses > span:nth-child(odd) {
  color: var(--app-text-secondary);
  font-size: 12px;
}
.environment-addresses .subtext {
  margin-top: 0;
}
.catalog-tabs :deep(.el-tabs__header) {
  margin: 0;
}
.catalog-tabs :deep(.el-tabs__nav-wrap::after),
.catalog-tabs :deep(.el-tabs__content) {
  display: none;
}
.catalog-tabs :deep(.el-tabs__item) {
  height: 53px;
}
.catalog-filters {
  padding-block: 16px;
  flex-wrap: wrap;
  background: var(--app-surface-soft);
  border-bottom: 1px solid var(--app-divider);
}
.catalog-filters .el-input {
  flex: 1;
  min-width: min(220px, 100%);
}
.catalog-filters .el-select {
  width: 150px;
}
.catalog-footer {
  padding-block: 13px;
  color: var(--app-text-muted);
  font-size: 12px;
}
.api-name {
  color: var(--app-primary);
  font-weight: 650;
}
.api-name:hover {
  text-decoration: underline;
}
.catalog-table .subtext {
  margin-top: 4px;
}
.entry,
.service-name {
  display: flex;
  align-items: center;
  gap: 8px;
}
.entry {
  flex-wrap: wrap;
}
.catalog-table code {
  font-size: 12px;
  overflow-wrap: anywhere;
}
.method {
  color: var(--app-primary);
  font-size: 11px;
  font-weight: 650;
}
.catalog-link {
  color: var(--app-primary);
  font-size: 13px;
  text-underline-offset: 3px;
}
.catalog-link:hover {
  text-decoration: underline;
}
.catalog-action {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding-inline: 10px;
  border: 1px solid var(--app-border);
  border-radius: var(--el-border-radius-base);
  color: var(--app-text);
  white-space: nowrap;
}
.catalog-action:hover {
  color: var(--app-primary);
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
  text-decoration: none;
}
.row-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 14px;
}
.row-actions .el-button + .el-button {
  margin-left: 0;
}
.service-actions {
  gap: 4px;
}
.compact-catalog {
  display: none;
  margin: 0;
  padding: 0;
  list-style: none;
}
.compact-record {
  min-width: 0;
  display: grid;
  gap: 9px;
  padding: 15px;
}
.compact-record + .compact-record {
  border-top: 1px solid var(--app-divider);
}
.compact-title,
.compact-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 5px 10px;
}
.compact-title > .api-name {
  min-width: 0;
  overflow-wrap: anywhere;
}
.compact-meta {
  justify-content: flex-start;
  color: var(--app-text-secondary);
  font-size: 12px;
}
.compact-path {
  padding: 7px 9px;
  border-radius: 6px;
  background: var(--app-surface-soft);
}
.compact-meta code,
.compact-path code,
.compact-addresses dd {
  min-width: 0;
  overflow-wrap: anywhere;
}
.compact-path code {
  font-size: 12px;
}
.compact-action {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 36px;
  padding-inline: 8px;
  border: 1px solid var(--app-border);
  border-radius: 6px;
}
.compact-addresses {
  display: grid;
  grid-template-columns: max-content minmax(0, 1fr);
  gap: 4px 10px;
  margin: 0;
  font-size: 13px;
}
.compact-addresses dt {
  color: var(--app-text-secondary);
}
.compact-addresses dd {
  margin: 0;
}
@container api-catalog (max-width: 890px) {
  .catalog-table {
    display: none;
  }
  .compact-catalog {
    display: block;
  }
}
.service-icon {
  display: grid;
  place-items: center;
  min-width: 33px;
  min-height: 33px;
  border-radius: 7px;
  background: var(--app-surface-soft);
  border: 1px solid var(--app-border);
  color: var(--app-text-secondary);
}
.service-select {
  width: 100%;
}
@media (max-width: 650px) {
  .catalog-heading {
    flex-wrap: wrap;
    gap: 4px 8px;
  }
  .catalog-heading,
  .catalog-filters,
  .catalog-footer {
    padding-inline: 15px;
  }
  .catalog-filters .el-input {
    flex-basis: 100%;
  }
  .catalog-filters .el-select {
    flex: 1;
    min-width: 120px;
  }
}
@media (pointer: coarse) {
  .compact-action {
    min-height: 44px;
  }
}
</style>
