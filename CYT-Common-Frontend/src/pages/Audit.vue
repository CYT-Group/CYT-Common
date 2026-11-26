<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { Search, Refresh } from '@element-plus/icons-vue';
import { api } from '~/api';
import type { Audit } from '~/api/types';
import { useRequest, dateTime } from '~/composables/useRequest';
import PageHeader from '~/components/PageHeader.vue';
import PageState from '~/components/PageState.vue';
import ListFilterBar from '~/components/ListFilterBar.vue';
import JsonViewer from '~/components/JsonViewer.vue';
const route = useRoute();
const query = reactive({
  page: 1,
  page_size: 20,
  q: String(route.query.q || ''),
  actor: String(route.query.actor || ''),
});
const { data, loading, error, load } = useRequest(() => api.audit(query));
const detail = ref<Audit>();
function search() {
  query.page = 1;
  void load();
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
watch(
  () => route.query,
  (params) => {
    query.q = String(params.q || '');
    query.actor = String(params.actor || '');
    search();
  },
);
</script>
<template>
  <div>
    <PageHeader
      title="审计日志"
      description="记录系统、授权、凭据及接口配置的管理变更，追溯操作人与影响范围。"
      ><el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button></PageHeader
    >
    <section class="panel">
      <ListFilterBar label="筛选审计日志">
        <el-input
          v-model="query.q"
          class="list-filter-search"
          :prefix-icon="Search"
          placeholder="搜索动作、目标"
          aria-label="搜索审计日志"
          clearable
          @keyup.enter="search"
          @clear="search"
        /><el-input
          v-model="query.actor"
          class="list-filter-field"
          placeholder="操作人"
          aria-label="筛选操作人"
          clearable
          @keyup.enter="search"
          @clear="search"
        /><el-button @click="search">查询</el-button>
        <template #actions>
          <span class="list-filter-count" role="status"
            >共 <strong>{{ data?.total || 0 }}</strong> 条记录</span
          >
          <span class="muted">审计记录仅追加</span>
        </template>
      </ListFilterBar>
      <PageState :loading="loading" :error="error" @retry="load"
        ><el-table
          :data="data?.items || []"
          @row-click="(row: Audit) => (detail = row)"
          style="cursor: pointer"
          ><el-table-column label="操作时间" width="190"
            ><template #default="{ row }">{{ dateTime(row.created_at) }}</template></el-table-column
          ><el-table-column prop="actor" label="操作人" min-width="155" /><el-table-column
            prop="action"
            label="动作"
            width="170"
          /><el-table-column prop="target" label="目标" min-width="230" /><el-table-column
            label="详情"
            width="85"
            ><template #default
              ><el-button link type="primary">查看</el-button></template
            ></el-table-column
          ></el-table
        >
        <footer v-if="(data?.total || 0) > query.page_size" class="table-footer">
          <el-pagination
            v-model:current-page="query.page"
            :page-size="query.page_size"
            :total="data?.total || 0"
            :pager-count="5"
            layout="prev, pager, next"
            @current-change="load"
          /></footer
      ></PageState>
    </section>
    <el-drawer :model-value="!!detail" title="审计详情" size="560px" @close="detail = undefined"
      ><template v-if="detail"
        ><el-descriptions :column="1" border
          ><el-descriptions-item label="操作人">{{ detail.actor }}</el-descriptions-item
          ><el-descriptions-item label="动作">{{ detail.action }}</el-descriptions-item
          ><el-descriptions-item label="目标">{{ detail.target }}</el-descriptions-item
          ><el-descriptions-item label="时间">{{
            dateTime(detail.created_at)
          }}</el-descriptions-item></el-descriptions
        >
        <div style="margin-top: 22px"><JsonViewer :value="detail.detail" /></div></template
    ></el-drawer>
  </div>
</template>
