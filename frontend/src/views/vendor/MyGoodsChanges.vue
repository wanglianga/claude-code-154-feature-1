<template>
  <div class="page-container">
    <el-alert v-if="auth.user?.foodRiskFlag" type="error" :closable="false" class="section-card">
      <template #title>
        您有食品风险记录（共 {{ auth.user.foodRiskCount || 1 }} 次），食品类报名暂受限制，
        下次报名需社区管理员提前复核。
      </template>
      <div style="font-size:12px; margin-top:4px;">
        {{ auth.user.preReviewReason || '现场换品食品资质不足被下架' }}
        ；请尽快联系社区补齐健康证/食品经营许可并申请提前复核，复核通过后恢复食品类报名。
      </div>
    </el-alert>
    <el-alert v-else-if="auth.user?.requirePreReview" type="warning" :closable="false" class="section-card">
      您的下次报名需社区管理员提前复核：{{ auth.user.preReviewReason || '现场换品处置记录' }}
    </el-alert>

    <el-card class="section-card" header="我的现场换品审核记录">
      <el-table :data="list" border>
        <el-table-column prop="createdAt" label="登记时间" width="160" />
        <el-table-column prop="shopName" label="摊位" min-width="120" />
        <el-table-column prop="newGoods" label="换品内容" min-width="150" show-overflow-tooltip />
        <el-table-column label="品类" width="80">
          <template #default="{ row }">
            <el-tag size="small">{{ row.newCategoryLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="食品资质" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.foodRelated" size="small"
                    :type="row.foodQualification === 'COMPLETE' ? 'success' : 'danger'">
              {{ row.foodQualificationLabel }}
            </el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="审核结果" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处置明细" min-width="200">
          <template #default="{ row }">
            <div v-if="row.status === 'APPROVED'">
              <el-text type="success" size="small">品类/用电已更新，请按食品安全提示经营</el-text>
            </div>
            <div v-else-if="row.status === 'REMOVED'">
              <el-text type="danger" size="small">
                已下架，下架前已售 {{ row.soldQuantity || 0 }} 份，{{ row.refundNeeded ? '需退款：' + (row.refundNote || '') : '无需退款' }}
              </el-text>
            </div>
            <el-text v-else type="warning" size="small">等待工作人员审核</el-text>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button size="small" @click="open(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="list.length === 0" description="暂无现场换品记录" />
    </el-card>

    <el-dialog v-model="dlg" title="换品审核详情" width="540px">
      <template v-if="current">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="摊位">{{ current.shopName }}</el-descriptions-item>
          <el-descriptions-item label="换品内容">{{ current.newGoods }}（{{ current.changeType === 'ADD' ? '临时新增' : '更换卖品' }}）</el-descriptions-item>
          <el-descriptions-item v-if="current.foodRelated" label="食品资质">
            {{ current.foodQualificationLabel }} {{ current.foodLicenseNo }}
          </el-descriptions-item>
          <el-descriptions-item label="登记时间">{{ current.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="审核结果">{{ current.statusLabel }} {{ current.reviewedAt }}</el-descriptions-item>
          <el-descriptions-item v-if="current.foodSafetyNotice" label="食品安全提示">{{ current.foodSafetyNotice }}</el-descriptions-item>
          <el-descriptions-item v-if="current.reviewComment" label="审核意见">{{ current.reviewComment }}</el-descriptions-item>
          <template v-if="current.status === 'REMOVED'">
            <el-descriptions-item label="已售数量">{{ current.soldQuantity || 0 }} 份</el-descriptions-item>
            <el-descriptions-item label="退款处置">{{ current.refundNeeded ? '需要退款：' + (current.refundNote || '') : '无需退款' }}</el-descriptions-item>
            <el-descriptions-item label="现场照片">{{ current.photoUrls || '—' }}</el-descriptions-item>
          </template>
        </el-descriptions>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../../api/client'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const list = ref([])
const current = ref(null)
const dlg = ref(false)

function statusType(s) {
  return s === 'APPROVED' ? 'success' : s === 'REMOVED' ? 'danger' : 'warning'
}

function open(row) {
  current.value = row
  dlg.value = true
}
onMounted(async () => {
  list.value = await api.get('/my/goods-changes')
})
</script>
