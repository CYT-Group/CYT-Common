<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue';
import { Download, Refresh, Search } from '@element-plus/icons-vue';
import { api } from '~/api';
import { useRequest, dateTime, useAction } from '~/composables/useRequest';
import type { CallLog } from '~/api/types';
import PageHeader from '~/components/PageHeader.vue';
import PageState from '~/components/PageState.vue';
import ListFilterBar from '~/components/ListFilterBar.vue';
import JsonViewer from '~/components/JsonViewer.vue';
import { useRoute } from 'vue-router';
const route = useRoute();
const query = reactive({
  page: 1,
  page_size: 20,
  q: '',
  system: String(route.query.system || ''),
  platform: '',
  status: '',
  hours: 168,
});
const range = ref<[Date, Date]>();
function params() {
  return { ...query, start: range.value?.[0].toISOString(), end: range.value?.[1].toISOString() };
}
const { data, loading, error, load } = useRequest(() => api.calls(params()));
const { busy, run } = useAction();
const detail = ref<CallLog>();
function search() {
  query.page = 1;
  void load();
}
function downloadCalls() {
  return run(() => api.exportCalls(params()));
}
watch(data, (result) => {
  if (!result) return;
  const lastPage = Math.max(1, Math.ceil(result.total / query.page_size));
  if (query.page > lastPage) {
    query.page = lastPage;
    void load();
  }
});
onMounted(load);
</script>
<template>
  <div>
    <PageHeader title="调用记录" description="按调用方、平台与时间定位请求，追踪每一次调用。"
      ><el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button></PageHeader
    >
    <section class="panel">
      <ListFilterBar class="call-filters" label="筛选调用记录">
        <el-input
          v-model="query.q"
          class="list-filter-search"
          placeholder="搜索操作、Trace ID"
          aria-label="搜索调用记录"
          :prefix-icon="Search"
          clearable
          @keyup.enter="search"
          @clear="search"
        /><el-select
          v-model="query.status"
          placeholder="全部结果"
          aria-label="筛选调用结果"
          clearable
          @change="search"
          ><el-option label="成功" value="ok" /><el-option label="失败" value="error" /></el-select
        ><el-input
          v-model="query.system"
          class="list-filter-field call-key-filter"
          placeholder="调用系统"
          aria-label="筛选调用系统"
          clearable
          @keyup.enter="search"
          @clear="search"
        /><el-input
          v-model="query.platform"
          class="list-filter-field call-key-filter"
          placeholder="接入平台"
          aria-label="筛选接入平台"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <div class="list-filter-field call-time-filter">
          <el-date-picker
            v-model="range"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            @change="search"
          />
        </div>
        <el-button class="call-query-button" @click="search">查询</el-button>
        <template #actions>
          <span class="list-filter-count" role="status"
            >共 <strong>{{ data?.total || 0 }}</strong> 条</span
          >
          <el-button link :icon="Download" :loading="busy" @click="downloadCalls">导出</el-button>
        </template>
      </ListFilterBar>
      <PageState :loading="loading" :error="error" @retry="load"
        ><el-table
          :data="data?.items || []"
          @row-click="(row: CallLog) => (detail = row)"
          style="cursor: pointer"
          ><el-table-column label="调用操作" min-width="240"
            ><template #default="{ row }"
              ><span class="mono">{{ row.operation }}</span>
              <div class="subtext mono">{{ row.trace_id }}</div></template
            ></el-table-column
          ><el-table-column prop="system" label="调用系统" width="110" /><el-table-column
            prop="platform"
            label="接入平台"
            width="110"
          /><el-table-column label="结果" width="130"
            ><template #default="{ row }"
              ><el-tag :type="row.status === 'ok' ? 'success' : 'danger'" effect="plain"
                >{{ row.http_status }} · {{ row.status === 'ok' ? '成功' : '失败' }}</el-tag
              ><span v-if="row.mock" class="subtext" style="display: block"
                >模拟调用</span
              ></template
            ></el-table-column
          ><el-table-column label="耗时" width="100"
            ><template #default="{ row }">{{ row.latency_ms }} ms</template></el-table-column
          ><el-table-column label="调用时间" width="180"
            ><template #default="{ row }">{{ dateTime(row.called_at) }}</template></el-table-column
          ></el-table
        >
        <footer v-if="(data?.total || 0) > query.page_size" class="table-footer">
          <el-pagination
            v-model:current-page="query.page"
            v-model:page-size="query.page_size"
            :total="data?.total || 0"
            :page-sizes="[20, 50, 100]"
            :pager-count="5"
            layout="sizes, prev, pager, next"
            @current-change="load"
            @size-change="search"
          /></footer
      ></PageState>
    </section>
    <el-drawer :model-value="!!detail" title="调用详情" size="650px" @close="detail = undefined"
      ><template v-if="detail"
        ><el-descriptions :column="1" border
          ><el-descriptions-item label="Trace ID"
            ><span class="mono">{{ detail.trace_id }}</span></el-descriptions-item
          ><el-descriptions-item label="操作">{{ detail.operation }}</el-descriptions-item
          ><el-descriptions-item label="系统">{{ detail.system }}</el-descriptions-item
          ><el-descriptions-item label="时间">{{ dateTime(detail.called_at) }}</el-descriptions-item
          ><el-descriptions-item v-if="detail.error_message" label="错误">{{
            detail.error_message
          }}</el-descriptions-item></el-descriptions
        >
        <h3 class="detail-title">请求参数</h3>
        <JsonViewer :value="detail.request_summary" />
        <h3 class="detail-title">响应数据</h3>
        <JsonViewer :value="detail.response_summary" /></template
    ></el-drawer>
  </div>
</template>

<style scoped>
.call-filters {
  --list-filter-search-width: 180px;
}
.call-key-filter {
  --list-filter-field-width: 94px;
  --list-filter-field-limit: 130px;
}
.call-time-filter {
  --list-filter-field-width: 218px;
  --list-filter-field-limit: 330px;
  display: flex;
}
.call-time-filter :deep(.el-date-editor) {
  width: 100%;
}
.call-query-button {
  padding-inline: 12px;
}
</style>
