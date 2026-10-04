<template>
  <div class="page-container">
    <el-page-header :icon="null" content="" style="margin-bottom:10px;">
      <template #content>
        <el-button link @click="$router.back()">← 返回</el-button>
      </template>
      <template #title>
      </template>
    </el-page-header>

    <el-card class="section-card">
      <template #header>
        <span>活动统一档案 · {{ archive.event?.name }}</span>
      </template>
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="报名/通过">{{ stats.totalApplications }} / {{ stats.approved }}</el-descriptions-item>
        <el-descriptions-item label="已入场/提前离场">{{ stats.checkedIn }} / {{ stats.earlyLeft }}</el-descriptions-item>
        <el-descriptions-item label="客流">{{ stats.visitorCount }}</el-descriptions-item>
        <el-descriptions-item label="到场率">{{ stats.attendanceRate }}%</el-descriptions-item>
        <el-descriptions-item label="营业总额">¥{{ fmtMoney(stats.totalSales) }}</el-descriptions-item>
        <el-descriptions-item label="公益捐赠总额">
          <b style="color:#f56c6c;">¥{{ fmtMoney(stats.totalDonation) }}</b>
        </el-descriptions-item>
        <el-descriptions-item label="筹款目标">¥{{ fmtMoney(stats.goalAmount) }}
          <el-tag size="small" :type="stats.goalReached ? 'success' : 'warning'">{{ stats.goalReached ? '已达成' : '未达成' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="投诉/异常">{{ stats.complaintCount }} / {{ stats.incidentCount }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card>
      <el-tabs v-model="tab">
        <el-tab-pane label="报名·审核·摊位·收款·捐赠（按摊主）" name="apps">
          <el-table :data="archive.applications" border size="small">
            <el-table-column label="摊主" min-width="130">
              <template #default="{ row }">
                {{ row.shopName }}
                <div style="font-size:11px; color:#909399;">{{ row.vendor?.realName }}｜信用{{ row.vendor?.creditScore }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="categoryLabel" label="品类" width="80" />
            <el-table-column label="审核" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="dictType(APP_STATUS, row.status)">{{ row.statusLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="摊位" width="110">
              <template #default="{ row }">{{ row.stall ? row.stall.zone + ' ' + row.stall.code : '—' }}</template>
            </el-table-column>
            <el-table-column label="入场" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="dictType(CHECKIN_STATUS, row.checkInStatus)">{{ row.checkInStatusLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="流水合计" width="100">
              <template #default="{ row }">¥{{ fmtMoney(row.salesAmount) }}</template>
            </el-table-column>
            <el-table-column label="应捐/实捐" width="150">
              <template #default="{ row }">
                <template v-if="row.donation">
                  ¥{{ fmtMoney(row.donation.expectedAmount) }} /
                  <b :class="row.donation.status === 'DISPUTED' ? 'dispute' : ''">¥{{ fmtMoney(row.donation.actualAmount) }}</b>
                  <el-tag size="small" :type="dictType(DONATION_STATUS, row.donation.status)">
                    {{ dictLabel(DONATION_STATUS, row.donation.status) }}
                  </el-tag>
                </template>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column label="异常/投诉" width="90">
              <template #default="{ row }">{{ row.incidents.length }} / {{ row.complaints.length }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="`收款流水(${archive.payments?.length || 0})`" name="payments">
          <el-table :data="archive.payments" border size="small">
            <el-table-column prop="recordedAt" label="时间" width="170" />
            <el-table-column prop="applicationId" label="报名ID" width="80" />
            <el-table-column prop="paymentMethod" label="方式" width="90" />
            <el-table-column prop="amount" label="金额(元)" width="110" />
            <el-table-column prop="receiptNo" label="凭证号" width="160" />
            <el-table-column prop="remark" label="备注" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="`捐赠记录(${archive.donations?.length || 0})`" name="donations">
          <el-table :data="archive.donations" border size="small">
            <el-table-column prop="applicationId" label="报名ID" width="80" />
            <el-table-column prop="rate" label="比例" width="80">
              <template #default="{ row }">{{ row.rate }}%</template>
            </el-table-column>
            <el-table-column prop="salesAmount" label="流水" width="100" />
            <el-table-column prop="expectedAmount" label="应捐" width="100" />
            <el-table-column prop="actualAmount" label="实捐" width="100" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="dictType(DONATION_STATUS, row.status)">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="voucherNo" label="凭证" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="`投诉(${archive.complaints?.length || 0})`" name="complaints">
          <el-timeline>
            <el-timeline-item v-for="c in archive.complaints" :key="c.id" :timestamp="c.createdAt">
              <el-tag size="small" :type="dictType(COMPLAINT_STATUS, c.status)">{{ c.status }}</el-tag>
              {{ c.complaintType }}｜{{ c.reporterName }}：{{ c.content }}
              <div v-if="c.handleResult" style="color:#67c23a; font-size:12px;">处理：{{ c.handleResult }}</div>
            </el-timeline-item>
          </el-timeline>
        </el-tab-pane>

        <el-tab-pane :label="`异常档案(${archive.incidents?.length || 0})`" name="incidents">
          <el-table :data="archive.incidents" border size="small">
            <el-table-column prop="createdAt" label="时间" width="170" />
            <el-table-column label="类型" width="160">
              <template #default="{ row }">{{ dictLabel(INCIDENT_TYPES, row.type) }}</template>
            </el-table-column>
            <el-table-column prop="description" label="描述" min-width="220" />
            <el-table-column label="级别" width="80">
              <template #default="{ row }">
                <el-tag size="small" :type="row.severity >= 3 ? 'danger' : row.severity === 2 ? 'warning' : 'info'">
                  {{ row.severity }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'RESOLVED' ? 'success' : 'danger'">
                  {{ row.status === 'RESOLVED' ? '已处理' : '待处理' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="resolution" label="处理结果" min-width="160" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="`换品审核(${archive.goodsChanges?.length || 0})`" name="goodsChanges">
          <el-table :data="archive.goodsChanges || []" border size="small">
            <el-table-column prop="createdAt" label="时间" width="170" />
            <el-table-column label="报名ID" prop="applicationId" width="80" />
            <el-table-column label="换品内容" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <el-tag size="small" style="margin-right:4px;">{{ dictLabel(STALL_TYPES, row.newCategory) }}</el-tag>
                {{ row.newGoodsDescription }}
                <el-tag v-if="row.involvesFood" size="small" type="danger" style="margin-left:4px;">涉食</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="dictType(CHANGE_STATUS, row.status)">
                  {{ dictLabel(CHANGE_STATUS, row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="处理结果" min-width="220">
              <template #default="{ row }">
                <template v-if="row.status === 'REMOVED'">
                  已售 {{ row.soldQuantity }} 件｜{{ row.refundNeeded ? '需退款' : '无需退款' }}
                  {{ row.refundNote || '' }}｜照片：{{ row.photoUrls }}
                </template>
                <template v-else-if="row.status === 'APPROVED'">
                  <div v-if="row.foodSafetyNotice">食品安全提示：{{ row.foodSafetyNotice }}</div>
                  <div v-if="row.powerUpdateNote">用电提示：{{ row.powerUpdateNote }}</div>
                </template>
                <span v-else>—</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="公示" name="publicity">
          <el-empty v-if="!archive.publicity" description="尚未发布公示" />
          <el-descriptions v-else :column="1" border>
            <el-descriptions-item label="标题">{{ archive.publicity.title }}</el-descriptions-item>
            <el-descriptions-item label="总捐赠">¥{{ fmtMoney(archive.publicity.totalDonation) }}</el-descriptions-item>
            <el-descriptions-item label="用途">{{ archive.publicity.fundUsage }}</el-descriptions-item>
            <el-descriptions-item label="履约">{{ archive.publicity.fulfillmentSummary }}</el-descriptions-item>
            <el-descriptions-item label="异常处理">{{ archive.publicity.incidentSummary }}</el-descriptions-item>
            <el-descriptions-item label="发布状态">
              <el-tag size="small" :type="archive.publicity.published ? 'success' : 'info'">
                {{ archive.publicity.published ? '已发布' : '草稿' }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../../api/client'
import { fmtMoney } from '../../composables/useEvents'
import {
  APP_STATUS, CHECKIN_STATUS, DONATION_STATUS, COMPLAINT_STATUS, INCIDENT_TYPES,
  STALL_TYPES, CHANGE_STATUS, dictLabel, dictType
} from '../../dict'

const route = useRoute()
const eventId = Number(route.params.id)
const archive = ref({})
const tab = ref('apps')
const stats = computed(() => archive.value.stats || {})

onMounted(async () => {
  archive.value = await api.get(`/events/${eventId}/archive`)
})
</script>

<style scoped>
.dispute { color: #f56c6c; }
</style>
