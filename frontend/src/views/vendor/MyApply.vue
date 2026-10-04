<template>
  <div class="page-container">
    <el-card style="max-width:820px;">
      <template #header>
        <div style="display:flex; justify-content:space-between; align-items:center;">
          <span>摊主报名</span>
          <el-select v-model="eventId" placeholder="选择活动" style="width:300px;">
            <el-option v-for="e in events" :key="e.id"
                       :label="`${e.name}（${dictLabel(EVENT_STATUS, e.status)}）`" :value="e.id" />
          </el-select>
        </div>
      </template>

      <el-alert v-if="current" type="success" :closable="false" style="margin-bottom:14px;">
        活动：{{ current.name }}｜公益目标：{{ current.publicGoal }}｜目标筹款 ¥{{ fmtMoney(current.goalAmount) }}
      </el-alert>

      <el-alert v-if="auth.user?.foodRiskFlag" type="error" :closable="false" style="margin-bottom:14px;">
        <template #title>
          您有现场食品风险记录（{{ auth.user.foodRiskCount || 1 }} 次），当前不能报名食品类，
          请先联系社区完成提前复核
        </template>
        <div style="font-size:12px;">{{ auth.user.preReviewReason || '现场换品食品资质不足被下架' }}</div>
      </el-alert>
      <el-alert v-else-if="auth.user?.requirePreReview" type="warning" :closable="false" style="margin-bottom:14px;">
        您的下次报名需社区管理员提前复核：{{ auth.user.preReviewReason || '现场换品处置记录' }}
      </el-alert>

      <el-form :model="f" label-width="140px" v-if="!existing">
        <el-divider content-position="left">售卖信息</el-divider>
        <el-form-item label="店铺/摊位名称" required>
          <el-input v-model="f.shopName" />
        </el-form-item>
        <el-form-item label="售卖品类" required>
          <el-radio-group v-model="f.category">
            <el-radio v-for="t in STALL_TYPES" :key="t.value" :value="t.value">{{ t.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="具体售卖内容" required>
          <el-input v-model="f.goodsDescription" type="textarea" :rows="2"
                    placeholder="请如实填写，现场巡检将核对，不符会被登记异常并影响信用" />
        </el-form-item>

        <el-divider content-position="left">食品资质（食品类必填）</el-divider>
        <el-form-item label="食品资质状态" required>
          <el-radio-group v-model="f.foodQualification">
            <el-radio v-for="q in FOOD_QUAL" :key="q.value" :value="q.value">{{ q.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="f.foodQualification !== 'NONE'" label="证照编号/说明">
          <el-input v-model="f.foodLicenseNo" placeholder="食品经营许可证编号/健康证情况" />
        </el-form-item>

        <el-divider content-position="left">公益捐赠</el-divider>
        <el-form-item label="公益捐赠比例(%)" required>
          <el-slider v-model="f.donationRate" :min="0" :max="100" :step="1" show-input
                     :marks="{ 0: '0%', 20: '20%优先黄金位', 100: '100%' }" />
          <el-text type="info" size="small">承诺按营业额的该比例捐赠，现场按收款流水核对，捐赠越高越优先高人流动线</el-text>
        </el-form-item>

        <el-divider content-position="left">现场需求</el-divider>
        <el-form-item label="需要用电">
          <el-switch v-model="f.powerNeeded" />
        </el-form-item>
        <el-form-item v-if="f.powerNeeded" label="申报功率(W)">
          <el-input-number v-model="f.powerWanted" :min="100" :step="100" />
          <el-text type="danger" size="small" style="margin-left:8px;">不得超过摊位供电容量，现场超载将被处置</el-text>
        </el-form-item>
        <el-form-item label="需要桌子">
          <el-switch v-model="f.tableNeeded" />
          <el-input-number v-if="f.tableNeeded" v-model="f.tableCount" :min="1" :max="5" style="margin-left:12px; width:110px;" />
        </el-form-item>
        <el-form-item label="需要椅子">
          <el-switch v-model="f.chairNeeded" />
          <el-input-number v-if="f.chairNeeded" v-model="f.chairCount" :min="1" :max="8" style="margin-left:12px; width:110px;" />
        </el-form-item>
        <el-form-item label="亲子摊位">
          <el-switch v-model="f.familyFriendly" />
          <el-text type="info" size="small" style="margin-left:8px;">带儿童参与/亲子互动内容，优先安排安全大摊位</el-text>
        </el-form-item>
        <el-form-item label="音响/叫卖">
          <el-switch v-model="f.noiseMaking" />
          <el-text type="warning" size="small" style="margin-left:8px;">有噪音设备将被避开居民休息区</el-text>
        </el-form-item>

        <el-divider content-position="left">收款方式</el-divider>
        <el-form-item label="收款方式" required>
          <el-radio-group v-model="f.paymentMethod">
            <el-radio v-for="m in PAY_METHODS" :key="m.value" :value="m.value">{{ m.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="收款账户说明">
          <el-input v-model="f.paymentAccount" placeholder="如：微信昵称/支付宝账号/募捐专户，用于流水核对" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" size="large" @click="submit">提交报名</el-button>
        </el-form-item>
      </el-form>

      <el-result v-else :icon="existing.status === 'APPROVED' ? 'success' : existing.status === 'REJECTED' ? 'error' : 'warning'"
                 :title="`已报名：${existing.statusLabel}`"
                 :sub-title="existing.reviewComment || '等待社区审核，通过后将生成入场二维码'">
        <template #extra>
          <el-button type="primary" @click="$router.push('/vendor/records')">查看我的报名与入场码</el-button>
        </template>
      </el-result>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api/client'
import { useEvents, fmtMoney } from '../../composables/useEvents'
import { useAuthStore } from '../../stores/auth'
import { STALL_TYPES, FOOD_QUAL, PAY_METHODS, EVENT_STATUS, dictLabel } from '../../dict'

const auth = useAuthStore()
const { events, currentEventId, loadEvents, selectEvent } = useEvents()
const eventId = ref(currentEventId.value)
const existing = ref(null)

const f = reactive({
  shopName: '', category: 'HANDMADE', goodsDescription: '',
  foodQualification: 'NONE', foodLicenseNo: '', donationRate: 10,
  powerNeeded: false, powerWanted: 500,
  tableNeeded: true, tableCount: 1, chairNeeded: false, chairCount: 2,
  familyFriendly: false, noiseMaking: false,
  paymentMethod: 'CASH', paymentAccount: ''
})

const current = computed(() => events.value.find((e) => e.id === eventId.value))

watch(eventId, (v) => {
  selectEvent(v)
  loadMine()
})

async function submit() {
  if (!f.shopName || !f.goodsDescription) {
    ElMessage.warning('请填写店铺名称与售卖内容')
    return
  }
  if (f.category === 'FOOD' && f.foodQualification === 'NONE') {
    ElMessage.warning('食品类摊位必须选择食品资质状态')
    return
  }
  try {
    await api.post('/my/applications', { eventId: eventId.value, ...f })
    ElMessage.success('报名成功，等待社区审核')
    loadMine()
  } catch (e) {
    ElMessage.error(e.message)
  }
}
async function loadMine() {
  if (!eventId.value) {
    return
  }
  const list = await api.get('/my/applications')
  existing.value = list.find((a) => a.eventId === eventId.value) || null
}
onMounted(async () => {
  await loadEvents()
  if (!eventId.value && events.value[0]) {
    eventId.value = events.value[0].id
  }
  loadMine()
})
</script>
