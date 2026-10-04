<template>
  <div class="page-container">
    <el-row :gutter="14">
      <el-col :span="10">
        <el-card header="现场换品登记（工作人员）">
          <el-alert type="info" :closable="false" style="margin-bottom:12px;">
            摊主现场临时增加/更换售卖品类（如二手玩具摊加售自制蛋糕）时，在此记录换品内容、食品资质与摊位条件，提交后进入审核。
          </el-alert>
          <el-form :model="form" label-width="110px">
            <el-form-item label="选择摊位">
              <el-select v-model="form.applicationId" filterable placeholder="选择摊主" style="width:100%">
                <el-option v-for="a in apps" :key="a.id"
                           :label="`${a.shopName}（${a.stall ? a.stall.code : '未分配'}）`" :value="a.id" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="selected" label="原报名">
              <el-text type="info">{{ selected.categoryLabel }}｜{{ selected.goodsDescription }}</el-text>
            </el-form-item>
            <el-form-item label="换品后品类">
              <el-radio-group v-model="form.newCategory">
                <el-radio v-for="t in STALL_TYPES" :key="t.value" :value="t.value">{{ t.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="换品内容" required>
              <el-input v-model="form.newGoodsDescription" type="textarea" :rows="2"
                        placeholder="如：新增自制蛋糕、曲奇售卖" />
            </el-form-item>
            <el-form-item label="涉及食品">
              <el-switch v-model="form.involvesFood" :disabled="form.newCategory === 'FOOD'" />
              <el-text v-if="form.newCategory === 'FOOD'" type="warning" size="small" style="margin-left:8px;">
                食品类自动涉及
              </el-text>
            </el-form-item>
            <template v-if="foodInvolved">
              <el-divider content-position="left">食品资质（现场核验）</el-divider>
              <el-form-item label="食品资质" required>
                <el-radio-group v-model="form.foodQualification">
                  <el-radio value="COMPLETE">资质齐全</el-radio>
                  <el-radio value="INCOMPLETE">资质不完整</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="证照编号/说明">
                <el-input v-model="form.foodLicenseNo" placeholder="食品经营许可证/健康证情况" />
              </el-form-item>
            </template>
            <el-divider content-position="left">摊位条件</el-divider>
            <el-form-item label="条件满足">
              <el-switch v-model="form.stallConditionOk" active-text="满足" inactive-text="不满足" />
            </el-form-item>
            <el-form-item label="条件记录">
              <el-input v-model="form.stallConditionNote" type="textarea" :rows="2"
                        placeholder="操作台/上下水/冷藏/防蝇防尘等现场条件" />
            </el-form-item>
            <el-form-item label="需要用电">
              <el-switch v-model="form.powerNeeded" />
              <el-input-number v-if="form.powerNeeded" v-model="form.powerWanted" :min="100" :step="100"
                               style="margin-left:12px; width:140px;" />
            </el-form-item>
            <el-button type="primary" style="width:100%" @click="submit">提交换品审核</el-button>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="14">
        <el-card>
          <template #header>
            <div style="display:flex; justify-content:space-between; align-items:center;">
              <span>换品审核记录</span>
              <el-radio-group v-model="filter" size="small">
                <el-radio-button value="">全部</el-radio-button>
                <el-radio-button value="PENDING">待审核</el-radio-button>
                <el-radio-button value="APPROVED">允许换品</el-radio-button>
                <el-radio-button value="REMOVED">已下架</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <el-table :data="filtered" border size="small">
            <el-table-column prop="createdAt" label="时间" width="160" />
            <el-table-column label="摊位" width="130">
              <template #default="{ row }">{{ shopName(row.applicationId) }}</template>
            </el-table-column>
            <el-table-column label="换品内容" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <el-tag size="small" style="margin-right:4px;">{{ dictLabel(STALL_TYPES, row.newCategory) }}</el-tag>
                {{ row.newGoodsDescription }}
              </template>
            </el-table-column>
            <el-table-column label="食品" width="60">
              <template #default="{ row }">
                <el-tag v-if="row.involvesFood" size="small" type="danger">涉食</el-tag>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column label="资质/条件" width="110">
              <template #default="{ row }">
                <div v-if="row.involvesFood">
                  <el-tag size="small" :type="row.foodQualification === 'COMPLETE' ? 'success' : 'danger'">
                    {{ dictLabel(FOOD_QUAL, row.foodQualification) }}
                  </el-tag>
                </div>
                <el-tag size="small" :type="row.stallConditionOk ? 'success' : 'danger'">
                  条件{{ row.stallConditionOk ? '满足' : '不足' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="dictType(CHANGE_STATUS, row.status)">
                  {{ dictLabel(CHANGE_STATUS, row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 'PENDING'" size="small" type="primary" @click="openReview(row)">
                  审核
                </el-button>
                <el-button v-else size="small" text @click="openDetail(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!filtered.length" description="暂无换品审核记录" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 审核弹窗 -->
    <el-dialog v-model="reviewDlg" title="换品审核处理" width="620px">
      <template v-if="current">
        <el-descriptions :column="2" border size="small" style="margin-bottom:12px;">
          <el-descriptions-item label="摊位">{{ shopName(current.applicationId) }}</el-descriptions-item>
          <el-descriptions-item label="换品后品类">{{ dictLabel(STALL_TYPES, current.newCategory) }}</el-descriptions-item>
          <el-descriptions-item label="换品内容" :span="2">{{ current.newGoodsDescription }}</el-descriptions-item>
          <el-descriptions-item label="食品资质" v-if="current.involvesFood">
            <el-tag size="small" :type="current.foodQualification === 'COMPLETE' ? 'success' : 'danger'">
              {{ dictLabel(FOOD_QUAL, current.foodQualification) }}
            </el-tag>
            {{ current.foodLicenseNo }}
          </el-descriptions-item>
          <el-descriptions-item label="摊位条件">
            <el-tag size="small" :type="current.stallConditionOk ? 'success' : 'danger'">
              {{ current.stallConditionOk ? '满足' : '不满足' }}
            </el-tag>
            {{ current.stallConditionNote }}
          </el-descriptions-item>
          <el-descriptions-item label="用电" :span="2">
            {{ current.powerNeeded ? `需要，申报 ${current.powerWanted}W` : '不需要' }}
          </el-descriptions-item>
        </el-descriptions>

        <el-alert v-if="!canApprove" type="error" :closable="false" style="margin-bottom:12px;">
          资质不足或摊位条件不满足，按规则必须下架处理。
        </el-alert>

        <el-form label-width="110px">
          <el-form-item label="处理决定">
            <el-radio-group v-model="review.decision">
              <el-radio value="APPROVE" :disabled="!canApprove">允许售卖</el-radio>
              <el-radio value="REMOVE">要求下架</el-radio>
            </el-radio-group>
          </el-form-item>

          <template v-if="review.decision === 'APPROVE'">
            <el-alert type="success" :closable="false" style="margin-bottom:12px;">
              允许后将自动更新该摊位的品类、用电申报与食品安全提示，并扣减少量信用（未提前申报）。
            </el-alert>
            <el-form-item v-if="current.involvesFood" label="食品安全提示">
              <el-input v-model="review.foodSafetyNotice" type="textarea" :rows="2"
                        placeholder="留空则使用系统默认提示" />
            </el-form-item>
            <el-form-item label="用电变更提示">
              <el-input v-model="review.powerUpdateNote" type="textarea" :rows="2"
                        placeholder="留空则按申报功率自动生成" />
            </el-form-item>
          </template>

          <template v-else>
            <el-alert type="warning" :closable="false" style="margin-bottom:12px;">
              下架将保留现场照片、登记已售数量与退款需求，扣减摊主信用并标记「下次报名需提前复核」；涉及食品的计入食品风险档案。
            </el-alert>
            <el-form-item label="现场照片" required>
              <el-input v-model="review.photoText" type="textarea" :rows="2"
                        placeholder="照片 URL 或文件名，多张用逗号分隔（留档备查，必填）" />
            </el-form-item>
            <el-form-item label="已售数量" required>
              <el-input-number v-model="review.soldQuantity" :min="0" />
            </el-form-item>
            <el-form-item label="需要退款" required>
              <el-radio-group v-model="review.refundNeeded">
                <el-radio :value="true">需要退款</el-radio>
                <el-radio :value="false">无需退款</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item v-if="review.refundNeeded" label="退款说明">
              <el-input v-model="review.refundNote" placeholder="退款金额/方式，如：按收款流水原路退回" />
            </el-form-item>
          </template>

          <el-form-item label="审核意见">
            <el-input v-model="review.reviewComment" type="textarea" :rows="2" />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="reviewDlg = false">取消</el-button>
        <el-button :type="review.decision === 'APPROVE' ? 'success' : 'danger'" @click="submitReview">
          确认{{ review.decision === 'APPROVE' ? '允许售卖' : '下架' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailDlg" title="换品处理详情" width="560px">
      <el-descriptions v-if="current" :column="1" border size="small">
        <el-descriptions-item label="状态">
          <el-tag size="small" :type="dictType(CHANGE_STATUS, current.status)">
            {{ dictLabel(CHANGE_STATUS, current.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="换品内容">{{ current.newGoodsDescription }}</el-descriptions-item>
        <el-descriptions-item label="审核意见">{{ current.reviewComment || '—' }}</el-descriptions-item>
        <el-descriptions-item v-if="current.foodSafetyNotice" label="食品安全提示">{{ current.foodSafetyNotice }}</el-descriptions-item>
        <el-descriptions-item v-if="current.powerUpdateNote" label="用电变更提示">{{ current.powerUpdateNote }}</el-descriptions-item>
        <template v-if="current.status === 'REMOVED'">
          <el-descriptions-item label="已售数量">{{ current.soldQuantity }} 件</el-descriptions-item>
          <el-descriptions-item label="退款">
            {{ current.refundNeeded ? '需要退款' : '无需退款' }} {{ current.refundNote || '' }}
          </el-descriptions-item>
          <el-descriptions-item label="现场照片">{{ current.photoUrls }}</el-descriptions-item>
        </template>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api/client'
import { STALL_TYPES, FOOD_QUAL, CHANGE_STATUS, dictLabel, dictType } from '../../dict'

const eventId = Number(localStorage.getItem('cm_event_id'))
const apps = ref([])
const changes = ref([])
const filter = ref('')

const form = reactive({
  applicationId: null, newCategory: 'FOOD', newGoodsDescription: '',
  involvesFood: false, foodQualification: 'COMPLETE', foodLicenseNo: '',
  stallConditionOk: true, stallConditionNote: '', powerNeeded: false, powerWanted: 500
})

const reviewDlg = ref(false)
const detailDlg = ref(false)
const current = ref(null)
const review = reactive({
  decision: 'APPROVE', reviewComment: '',
  foodSafetyNotice: '', powerUpdateNote: '',
  photoText: '', soldQuantity: 0, refundNeeded: false, refundNote: ''
})

const selected = computed(() => apps.value.find((a) => a.id === form.applicationId))
const foodInvolved = computed(() => form.involvesFood || form.newCategory === 'FOOD')
const filtered = computed(() => changes.value.filter((c) => !filter.value || c.status === filter.value))
const canApprove = computed(() => current.value
  && (!current.value.involvesFood || current.value.foodQualification === 'COMPLETE')
  && current.value.stallConditionOk)

function shopName(id) {
  return apps.value.find((a) => a.id === id)?.shopName || ('#' + id)
}

async function submit() {
  if (!form.applicationId || !form.newGoodsDescription) {
    ElMessage.warning('请选择摊位并填写换品内容')
    return
  }
  if (foodInvolved.value && !form.foodQualification) {
    ElMessage.warning('涉及食品必须登记食品资质状态')
    return
  }
  try {
    await api.post('/onsite/goods-changes', {
      ...form,
      involvesFood: foodInvolved.value,
      foodQualification: foodInvolved.value ? form.foodQualification : 'NONE'
    })
    ElMessage.success('换品已登记，等待审核处理')
    Object.assign(form, {
      applicationId: null, newGoodsDescription: '', involvesFood: false,
      foodQualification: 'COMPLETE', foodLicenseNo: '', stallConditionOk: true,
      stallConditionNote: '', powerNeeded: false, powerWanted: 500
    })
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openReview(row) {
  current.value = row
  const approve = (!row.involvesFood || row.foodQualification === 'COMPLETE') && row.stallConditionOk
  Object.assign(review, {
    decision: approve ? 'APPROVE' : 'REMOVE', reviewComment: '',
    foodSafetyNotice: '', powerUpdateNote: '',
    photoText: '', soldQuantity: 0, refundNeeded: false, refundNote: ''
  })
  reviewDlg.value = true
}

function openDetail(row) {
  current.value = row
  detailDlg.value = true
}

async function submitReview() {
  const body = {
    decision: review.decision,
    reviewComment: review.reviewComment,
    foodSafetyNotice: review.foodSafetyNotice,
    powerUpdateNote: review.powerUpdateNote
  }
  if (review.decision === 'REMOVE') {
    const photos = review.photoText.split(/[,，\n]/).map((s) => s.trim()).filter(Boolean)
    if (!photos.length) {
      ElMessage.warning('下架处理必须填写现场照片（留档）')
      return
    }
    Object.assign(body, {
      photoUrls: photos,
      soldQuantity: review.soldQuantity,
      refundNeeded: review.refundNeeded,
      refundNote: review.refundNote
    })
  }
  try {
    await api.post(`/onsite/goods-changes/${current.value.id}/review`, body)
    ElMessage.success(review.decision === 'APPROVE'
      ? '已允许售卖：品类/用电/食品安全提示已更新'
      : '已下架：照片留档、已售与退款信息已记录，摊主信用已扣减')
    reviewDlg.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function load() {
  apps.value = (await api.get(`/events/${eventId}/applications`)).filter((a) => a.status === 'APPROVED')
  changes.value = await api.get(`/onsite/goods-changes/events/${eventId}`)
}
onMounted(load)
</script>
