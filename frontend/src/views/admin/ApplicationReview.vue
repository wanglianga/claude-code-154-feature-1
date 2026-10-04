<template>
  <div class="page-container">
    <el-card class="section-card">
      <template #header>
        <div style="display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:8px;">
          <span>摊主报名审核 · 活动规则校验</span>
          <el-radio-group v-model="filter" size="small" @change="load">
            <el-radio-button value="">全部</el-radio-button>
            <el-radio-button value="SUBMITTED">待审核</el-radio-button>
            <el-radio-button value="APPROVED">已通过</el-radio-button>
            <el-radio-button value="REJECTED">已驳回</el-radio-button>
          </el-radio-group>
        </div>
      </template>

      <el-table :data="apps" border stripe @row-click="openDetail">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="摊主" width="160">
          <template #default="{ row }">
            <div>{{ row.shopName }}</div>
            <div style="font-size:12px; color:#909399;">
              {{ row.vendor?.realName }} · 信用
              <el-tag size="small" :type="creditType(row.vendor?.creditScore)">{{ row.vendor?.creditScore }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="品类" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.categoryLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="goodsDescription" label="申报售卖内容" min-width="180" show-overflow-tooltip />
        <el-table-column label="食品资质" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="qualType(row.foodQualification)">{{ row.foodQualificationLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="捐赠比例" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="Number(row.donationRate) >= 20 ? 'danger' : 'info'">{{ row.donationRate }}%</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="现场需求" min-width="170">
          <template #default="{ row }">
            <el-tag v-if="row.powerNeeded" size="small" type="warning">用电{{ row.powerWanted }}W</el-tag>
            <el-tag v-if="row.tableNeeded" size="small" type="info">桌{{ row.tableCount }}</el-tag>
            <el-tag v-if="row.chairNeeded" size="small" type="info">椅{{ row.chairCount }}</el-tag>
            <el-tag v-if="row.familyFriendly" size="small" type="success">亲子</el-tag>
            <el-tag v-if="row.noiseMaking" size="small" type="danger">有噪音</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="dictType(APP_STATUS, row.status)">{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核操作" width="170" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'SUBMITTED'">
              <el-button size="small" type="success" @click.stop="openReview(row, true)">通过</el-button>
              <el-button size="small" type="danger" @click.stop="openReview(row, false)">驳回</el-button>
            </template>
            <span v-else style="color:#c0c4cc; font-size:12px;">
              {{ row.reviewComment || '已审核' }}
            </span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 审核弹窗 -->
    <el-dialog v-model="reviewDlg" :title="(approve ? '通过报名' : '驳回报名')" width="560px">
      <el-alert v-if="current" :type="ruleCheck(current).ok ? 'success' : 'error'" :closable="false" style="margin-bottom:10px;">
        <div v-for="(t, i) in ruleCheck(current).tips" :key="i">· {{ t }}</div>
      </el-alert>
      <el-descriptions v-if="current" :column="2" border size="small">
        <el-descriptions-item label="店铺">{{ current.shopName }}</el-descriptions-item>
        <el-descriptions-item label="品类">{{ current.categoryLabel }}</el-descriptions-item>
        <el-descriptions-item label="售卖内容" :span="2">{{ current.goodsDescription }}</el-descriptions-item>
        <el-descriptions-item label="食品资质">{{ current.foodQualificationLabel }} {{ current.foodLicenseNo }}</el-descriptions-item>
        <el-descriptions-item label="捐赠比例">{{ current.donationRate }}%</el-descriptions-item>
        <el-descriptions-item label="收款方式">{{ payLabel(current.paymentMethod) }} / {{ current.paymentAccount }}</el-descriptions-item>
        <el-descriptions-item label="摊主信用">{{ current.vendor?.creditScore }}</el-descriptions-item>
      </el-descriptions>
      <el-input v-model="comment" type="textarea" :rows="2" style="margin-top:12px;" placeholder="审核意见（可选）" />
      <template #footer>
        <el-button @click="reviewDlg = false">取消</el-button>
        <el-button :type="approve ? 'success' : 'danger'" @click="submitReview">
          确认{{ approve ? '通过（将生成入场二维码）' : '驳回' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailDlg" title="报名详情（活动档案）" size="520px">
      <template v-if="current">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="摊主">{{ current.vendor?.realName }}（{{ current.vendor?.phone }}）信用 {{ current.vendor?.creditScore }}</el-descriptions-item>
          <el-descriptions-item label="审核">{{ current.statusLabel }} / {{ current.reviewer?.realName }} / {{ current.reviewComment }}</el-descriptions-item>
          <el-descriptions-item label="摊位">
            {{ current.stall ? `${current.stall.zone} ${current.stall.code}` : '未分配' }}
            <div v-if="current.allocationReason" style="color:#909399; font-size:12px;">{{ current.allocationReason }}</div>
          </el-descriptions-item>
          <el-descriptions-item label="入场状态">{{ current.checkInStatusLabel }} {{ current.checkInAt }}</el-descriptions-item>
        </el-descriptions>
        <el-divider>收款 / 捐赠</el-divider>
        <div v-if="current.donation">
          流水合计 ¥{{ current.salesAmount }}，应捐 ¥{{ current.donation.expectedAmount }}，
          实捐 ¥{{ current.donation.actualAmount }}
          <el-tag size="small" :type="dictType(DONATION_STATUS, current.donation.status)">{{ current.donation.status }}</el-tag>
        </div>
        <div v-else style="color:#909399;">暂无收款/捐赠记录</div>
        <el-divider>异常 / 投诉 / 巡检</el-divider>
        <el-timeline>
          <el-timeline-item v-for="g in current.goodsChanges" :key="'g'+g.id" :timestamp="g.createdAt"
                            :type="g.status === 'APPROVED' ? 'success' : g.status === 'REMOVED' ? 'danger' : 'warning'">
            现场换品（{{ g.statusLabel }}）：{{ g.newGoods }}
            <span v-if="g.foodRelated">｜资质：{{ g.foodQualificationLabel }}</span>
            <div v-if="g.status === 'REMOVED'" style="color:#f56c6c;">
              已售 {{ g.soldQuantity || 0 }} 份{{ g.refundNeeded ? '，需退款：' + (g.refundNote || '') : '，无需退款' }}
            </div>
            <div v-if="g.foodSafetyNotice" style="color:#e6a23c;">食品安全提示：{{ g.foodSafetyNotice }}</div>
          </el-timeline-item>
          <el-timeline-item v-for="i in current.incidents" :key="'i'+i.id" :timestamp="i.createdAt" type="danger">
            [{{ incidentLabel(i.type) }}] {{ i.description }}
          </el-timeline-item>
          <el-timeline-item v-for="c in current.complaints" :key="'c'+c.id" :timestamp="c.createdAt" type="warning">
            投诉（{{ c.complaintType }}）：{{ c.content }}
          </el-timeline-item>
          <el-timeline-item v-for="p in current.inspections" :key="'p'+p.id" :timestamp="p.createdAt" type="primary">
            巡检：实际「{{ p.actualGoods }}」{{ p.goodsChanged ? '，临时换品：' + p.changeDescription : '' }}
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '../../api/client'
import { APP_STATUS, DONATION_STATUS, PAY_METHODS, INCIDENT_TYPES, dictLabel, dictType } from '../../dict'

const route = useRoute()
const eventId = Number(route.params.id)
const apps = ref([])
const filter = ref('SUBMITTED')
const reviewDlg = ref(false)
const detailDlg = ref(false)
const approve = ref(true)
const current = ref(null)
const comment = ref('')

function creditType(s) {
  return s >= 100 ? 'success' : s >= 80 ? 'warning' : 'danger'
}
function qualType(q) {
  return q === 'COMPLETE' ? 'success' : q === 'INCOMPLETE' ? 'danger' : 'info'
}
function payLabel(v) {
  return dictLabel(PAY_METHODS, v)
}
function incidentLabel(v) {
  return dictLabel(INCIDENT_TYPES, v)
}

/** 按品类的活动规则校验 */
function ruleCheck(a) {
  const tips = []
  let ok = true
  switch (a.category) {
    case 'FOOD':
      if (a.foodQualification === 'INCOMPLETE') {
        ok = false
        tips.push('食品摊位资质不完整（' + a.foodLicenseNo + '），按规则应驳回，补齐健康证/经营许可后再报名')
      } else {
        tips.push('食品资质齐全，需安排在食品区并核验用电')
      }
      break
    case 'HANDMADE':
      tips.push('手作类：需确认为亲手制作而非批量进货，建议安排中心/边角区')
      break
    case 'SECONDHAND':
      tips.push('二手物品：需确认物品来源合法、无假冒伪劣，可接受较低人流位置')
      break
    case 'CHARITY':
      tips.push('公益义卖：捐赠比例 ' + a.donationRate + '%，优先安排高人流动线，收款建议走募捐专户')
      break
    case 'COMMERCIAL':
      tips.push('商业摊位：控制占比，捐赠比例 ' + a.donationRate + '%，有噪音设备需避开噪音敏感区')
      break
  }
  if (a.noiseMaking) {
    tips.push('申报有音响/叫卖，分配时必须避开噪音敏感区')
  }
  if (a.powerNeeded) {
    tips.push(`申报用电 ${a.powerWanted}W，需匹配可供电摊位`)
  }
  if (a.vendor?.creditScore < 80) {
    ok = false
    tips.push('摊主信用分低于 80，建议审慎通过并限制黄金摊位')
  }
  return { ok, tips }
}

function openReview(row, pass) {
  current.value = row
  approve.value = pass
  comment.value = pass ? '符合活动规则' : ''
  reviewDlg.value = true
}
function openDetail(row) {
  current.value = row
  detailDlg.value = true
}
async function submitReview() {
  await api.post(`/applications/${current.value.id}/review`, { approved: approve.value, comment: comment.value })
  ElMessage.success(approve.value ? '已通过，入场二维码已生成' : '已驳回')
  reviewDlg.value = false
  load()
}
async function load() {
  const url = `/events/${eventId}/applications` + (filter.value ? `?status=${filter.value}` : '')
  apps.value = await api.get(url)
}
onMounted(load)
</script>
