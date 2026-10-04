<template>
  <div class="page-container">
    <el-row :gutter="14">
      <el-col :span="9">
        <el-card header="扫码 / 输入码核销">
          <el-input v-model="code" size="large" placeholder="扫描或输入摊主入场码，如 CM1-3-A1B2C3" clearable
                    @keyup.enter="checkin">
            <template #prepend><el-icon><Aim /></el-icon></template>
          </el-input>
          <el-button type="primary" size="large" style="width:100%; margin-top:12px;" :loading="loading" @click="checkin">
            核销入场
          </el-button>
          <el-divider>或选择待入场摊主</el-divider>
          <el-table :data="waiting" border size="small" max-height="360" highlight-current-row>
            <el-table-column prop="shopName" label="摊主" min-width="120" />
            <el-table-column label="摊位" width="100">
              <template #default="{ row }">{{ row.stall?.zone }} {{ row.stall?.code }}</template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="code = row.checkinCode; checkin()">入场</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="15">
        <el-card v-if="result" class="section-card">
          <template #header>
            <div style="display:flex; justify-content:space-between; align-items:center;">
              <span>核销成功：{{ result.application.shopName }}（{{ result.application.categoryLabel }}）</span>
              <el-button size="small" type="success" :disabled="result.application.materialsCollected" @click="collect">
                {{ result.application.materialsCollected ? '物资已领取' : '确认物资已发放' }}
              </el-button>
            </div>
          </template>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="摊主">{{ result.application.vendor?.realName }} {{ result.application.vendor?.phone }}</el-descriptions-item>
            <el-descriptions-item label="摊位">{{ result.application.stall?.zone }} {{ result.application.stall?.code }}</el-descriptions-item>
            <el-descriptions-item label="申报内容">{{ result.application.goodsDescription }}</el-descriptions-item>
            <el-descriptions-item label="食品资质">
              <el-tag size="small" :type="result.application.foodQualification === 'COMPLETE' ? 'success'
                : result.application.foodQualification === 'INCOMPLETE' ? 'danger' : 'info'">
                {{ result.application.foodQualificationLabel }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
          <el-divider>物资领取清单</el-divider>
          <el-table :data="result.materials" border size="small">
            <el-table-column prop="name" label="物资" />
            <el-table-column prop="qty" label="数量" width="80" />
            <el-table-column prop="unit" label="单位/说明" />
          </el-table>
          <el-alert v-if="result.application.foodQualification === 'INCOMPLETE'" type="error" :closable="false"
                    style="margin-top:10px;">
            ⚠ 该食品摊资质不完整，已自动登记高优先级异常，请现场限制制售并通知负责人
          </el-alert>
          <el-alert v-if="result.application.foodSafetyNotice" type="warning" :closable="false"
                    style="margin-top:10px;">
            🍰 食品安全提示（现场换品审核）：{{ result.application.foodSafetyNotice }}
          </el-alert>
        </el-card>

        <el-card header="今日入场动态">
          <el-table :data="today" border size="small">
            <el-table-column prop="shopName" label="摊主" min-width="120" />
            <el-table-column label="品类" width="90">
              <template #default="{ row }">
                <el-tag size="small">{{ row.categoryLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="摊位" width="100">
              <template #default="{ row }">{{ row.stall ? row.stall.zone + ' ' + row.stall.code : '—' }}</template>
            </el-table-column>
            <el-table-column label="入场状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="dictType(CHECKIN_STATUS, row.checkInStatus)">{{ row.checkInStatusLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="物资" width="80">
              <template #default="{ row }">
                <el-icon v-if="row.materialsCollected" color="#67c23a"><CircleCheckFilled /></el-icon>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150">
              <template #default="{ row }">
                <el-button size="small" @click="showMaterials(row)">物资清单</el-button>
                <el-button v-if="row.checkInStatus === 'CHECKED_IN'" size="small" type="danger" @click="leave(row)">
                  提前离场
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../../api/client'
import { CHECKIN_STATUS, dictType } from '../../dict'

const route = useRoute()
const eventId = ref(Number(route.params.id) || Number(localStorage.getItem('cm_event_id')))
const code = ref('')
const loading = ref(false)
const result = ref(null)
const apps = ref([])

const waiting = computed(() => apps.value.filter((a) => a.status === 'APPROVED' && a.stallId && a.checkInStatus === 'NOT_CHECKED'))
const today = computed(() => apps.value.filter((a) => a.status === 'APPROVED'))

async function checkin() {
  if (!code.value) {
    ElMessage.warning('请输入或扫描入场码')
    return
  }
  loading.value = true
  try {
    result.value = await api.post('/onsite/checkin', { code: code.value.trim() })
    ElMessage.success('核销成功')
    code.value = ''
    load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}
async function collect() {
  await api.post(`/onsite/applications/${result.value.application.id}/materials/collect`, {})
  ElMessage.success('物资发放已登记')
  result.value.application.materialsCollected = true
  load()
}
async function showMaterials(row) {
  const r = await api.get(`/onsite/applications/${row.id}/materials`)
  await ElMessageBox.alert(
    r.materials.map((m) => `${m.name} × ${m.qty}${m.unit}`).join('<br/>'),
    `${row.shopName} 物资清单`,
    { dangerouslyUseHTMLString: true }
  )
}
async function leave(row) {
  await ElMessageBox.confirm(`确认 ${row.shopName} 提前离场？将记录异常并扣减信用分`, '提示', { type: 'warning' })
  await api.post(`/onsite/applications/${row.id}/leave-early`, {})
  ElMessage.success('已登记提前离场')
  load()
}
async function load() {
  apps.value = await api.get(`/events/${eventId.value}/applications`)
}
onMounted(async () => {
  if (!eventId.value) {
    const events = await api.get('/events')
    eventId.value = events[0]?.id
  }
  load()
})
</script>
