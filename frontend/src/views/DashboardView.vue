<template>
  <div class="page-container">
    <el-card class="section-card">
      <div style="display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:10px;">
        <div>
          <div style="font-size:16px; font-weight:600;">当前活动</div>
          <div style="color:#909399; font-size:12px; margin-top:4px;">
            {{ current?.name }} · {{ current?.location }} · {{ current?.eventDate }}
          </div>
        </div>
        <el-select v-model="eventId" style="width:300px" placeholder="选择活动" @change="onChange">
          <el-option v-for="e in events" :key="e.id" :label="`${e.name}（${statusLabel(e.status)}）`" :value="e.id" />
        </el-select>
      </div>
      <el-descriptions v-if="current" :column="3" border style="margin-top:14px;">
        <el-descriptions-item label="公益目标">{{ current.publicGoal || '—' }}</el-descriptions-item>
        <el-descriptions-item label="筹款目标">¥ {{ fmtMoney(current.goalAmount) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="small">{{ statusLabel(current.status) }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-row :gutter="14">
      <el-col :span="6" v-for="c in cards" :key="c.label">
        <div class="stat-card" :style="{ background: c.color }">
          <div class="num">{{ c.value }}</div>
          <div class="label">{{ c.label }}</div>
        </div>
      </el-col>
    </el-row>

    <el-card class="section-card" style="margin-top:16px;" header="快捷入口">
      <el-space wrap>
        <el-link v-if="auth.isAdmin" type="primary" :href="`#/events/${eventId}/applications`">报名审核</el-link>
        <el-link v-if="auth.isAdmin" type="primary" :href="`#/events/${eventId}/stalls`">摊位平面图</el-link>
        <el-link v-if="auth.isAdmin" type="primary" :href="`#/events/${eventId}/allocate`">智能分配</el-link>
        <el-link v-if="auth.isAdmin || auth.isStaff" type="primary" href="#/onsite/checkin">扫码核销</el-link>
        <el-link v-if="auth.isAdmin || auth.isStaff" type="primary" href="#/onsite/goods-change">现场换品审核</el-link>
        <el-link v-if="auth.isAdmin || auth.isStaff" type="primary" href="#/onsite/finance">收款捐赠核对</el-link>
        <el-link v-if="auth.isAdmin || auth.isStaff" type="primary" :href="`#/events/${eventId}/archive`">活动档案</el-link>
        <el-link v-if="auth.isAdmin || auth.isStaff" type="primary" :href="`#/events/${eventId}/review`">复盘建议</el-link>
        <el-link v-if="auth.isVendor" type="success" href="#/vendor/apply">我要报名</el-link>
        <el-link v-if="auth.isVendor" type="success" href="#/vendor/records">我的入场码</el-link>
        <el-link v-if="auth.isVendor" type="success" href="#/vendor/goods-changes">我的换品审核</el-link>
        <el-link type="info" href="#/public">居民公示端</el-link>
      </el-space>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useEvents, fmtMoney } from '../composables/useEvents'
import { useAuthStore } from '../stores/auth'
import { EVENT_STATUS, dictLabel } from '../dict'
import { api } from '../api/client'

const auth = useAuthStore()
const { events, currentEventId, loadEvents, selectEvent } = useEvents()
const stats = ref(null)

const eventId = computed({
  get: () => currentEventId.value,
  set: () => {}
})
const current = computed(() => events.value.find((e) => e.id === currentEventId.value))

function statusLabel(s) {
  return dictLabel(EVENT_STATUS, s)
}
function onChange(v) {
  selectEvent(v)
}

async function loadStats() {
  if (!currentEventId.value) {
    return
  }
  try {
    const archive = await api.get(`/events/${currentEventId.value}/archive`)
    stats.value = archive.stats
  } catch {
    stats.value = null
  }
}

const cards = computed(() => {
  const s = stats.value
  if (auth.isVendor) {
    return [
      { label: '我的信用分', value: auth.user?.creditScore ?? '-', color: '#67c23a' },
      { label: '活动名称', value: current.value?.name?.slice(0, 6) || '-', color: '#409eff' },
      { label: '公益目标', value: '¥' + fmtMoney(current.value?.goalAmount), color: '#e6a23c' },
      { label: '活动状态', value: current.value ? statusLabel(current.value.status) : '-', color: '#909399' }
    ]
  }
  return [
    { label: '报名摊位数', value: s?.totalApplications ?? '-', color: '#409eff' },
    { label: '审核通过 / 已入场', value: `${s?.approved ?? '-'} / ${s?.checkedIn ?? '-'}`, color: '#67c23a' },
    { label: '现场客流', value: s?.visitorCount ?? '-', color: '#e6a23c' },
    { label: '公益捐赠总额(元)', value: fmtMoney(s?.totalDonation), color: '#f56c6c' }
  ]
})

watch(currentEventId, loadStats)
onMounted(async () => {
  await loadEvents()
  await loadStats()
})
</script>
