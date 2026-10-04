<template>
  <div class="page-container">
    <el-row :gutter="14">
      <el-col :span="10">
        <el-card header="现场换品登记（临时增加/更换售卖内容）">
          <el-form :model="form" label-width="118px">
            <el-form-item label="选择摊位">
              <el-select v-model="form.applicationId" filterable placeholder="选择摊主" style="width:100%">
                <el-option v-for="a in checkedInApps" :key="a.id"
                           :label="`${a.shopName}（${a.stall ? a.stall.code : '未分配'}）`" :value="a.id" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="selected" label="原申报内容">
              <el-text type="info">{{ selected.goodsDescription }}</el-text>
            </el-form-item>
            <el-form-item label="换品方式">
              <el-radio-group v-model="form.changeType">
                <el-radio value="ADD">临时新增</el-radio>
                <el-radio value="REPLACE">更换卖品</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="新增/更换内容" required>
              <el-input v-model="form.newGoods" type="textarea" :rows="2"
                        placeholder="如：临时增加自制奶油蛋糕、纸杯蛋糕" />
            </el-form-item>
            <el-form-item label="换品后品类" required>
              <el-select v-model="form.newCategory" style="width:100%" @change="onCategoryChange">
                <el-option v-for="t in STALL_TYPES" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>

            <el-divider content-position="left">食品资质（涉及食品制售时核验）</el-divider>
            <el-form-item label="涉及制售食品">
              <el-switch v-model="form.foodRelated" />
              <el-text type="info" size="small" style="margin-left:8px;">选品类为食品时自动开启</el-text>
            </el-form-item>
            <template v-if="form.foodRelated">
              <el-form-item label="食品资质状态" required>
                <el-radio-group v-model="form.foodQualification">
                  <el-radio v-for="q in FOOD_QUAL" :key="q.value" :value="q.value">{{ q.label }}</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="证照/健康证">
                <el-input v-model="form.foodLicenseNo" placeholder="证照编号或办理情况说明" />
              </el-form-item>
            </template>

            <el-divider content-position="left">摊位条件现场核验</el-divider>
            <el-form-item label="新增设备功率(W)">
              <el-input-number v-model="form.powerNeededWatt" :min="0" :step="100" />
            </el-form-item>
            <el-form-item label="摊位可供电">
              <el-switch v-model="form.stallPowerAvailable" />
              <el-input-number v-if="form.stallPowerAvailable" v-model="form.stallPowerCapacity"
                               :min="0" :step="100" style="margin-left:12px; width:130px;" />
              <el-text v-if="form.stallPowerAvailable && form.powerNeededWatt > form.stallPowerCapacity"
                       type="danger" size="small" style="margin-left:8px;">超容量，不能放行</el-text>
            </el-form-item>
            <template v-if="form.foodRelated">
              <el-form-item label="位于食品区">
                <el-switch v-model="form.foodZone" />
              </el-form-item>
              <el-form-item label="卫生条件合格">
                <el-switch v-model="form.hygieneReady" />
                <el-text type="info" size="small" style="margin-left:8px;">操作台/清洁用水/防尘防蝇</el-text>
              </el-form-item>
            </template>
            <el-form-item label="摊位条件备注">
              <el-input v-model="form.stallConditionNote" type="textarea" :rows="2"
                        placeholder="空间、明火、相邻摊位等情况" />
            </el-form-item>
            <el-form-item label="现场照片">
              <el-input v-model="form.photoUrls" type="textarea" :rows="2"
                        placeholder="照片链接或编号，多个用逗号分隔；下架处置必须保留现场照片" />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="form.remark" />
            </el-form-item>
            <el-button type="primary" style="width:100%" @click="submit">提交换品登记，进入审核</el-button>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="14">
        <el-card>
          <template #header>
            <div style="display:flex; justify-content:space-between; align-items:center;">
              <span>现场换品审核记录</span>
              <el-radio-group v-model="filter" size="small" @change="load">
                <el-radio-button value="">全部</el-radio-button>
                <el-radio-button value="PENDING">待审核</el-radio-button>
                <el-radio-button value="APPROVED">允许售卖</el-radio-button>
                <el-radio-button value="REMOVED">已下架</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <el-table :data="changes" border size="small">
            <el-table-column prop="createdAt" label="时间" width="160" />
            <el-table-column prop="shopName" label="摊位" min-width="110" show-overflow-tooltip />
            <el-table-column prop="newGoods" label="换品内容" min-width="130" show-overflow-tooltip />
            <el-table-column label="品类" width="70">
              <template #default="{ row }">
                <el-tag size="small">{{ row.newCategoryLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="食品资质" width="95">
              <template #default="{ row }">
                <el-tag v-if="row.foodRelated" size="small"
                        :type="row.foodQualification === 'COMPLETE' ? 'success' : 'danger'">
                  {{ row.foodQualificationLabel }}
                </el-tag>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column label="审核结果" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="statusType(row.status)">{{ row.statusLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="170" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="showDetail(row)">详情</el-button>
                <template v-if="row.status === 'PENDING'">
                  <el-button size="small" type="success" @click="openReview(row, 'APPROVED')">允许售卖</el-button>
                  <el-button size="small" type="danger" @click="openReview(row, 'REMOVE')">要求下架</el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 审核处理弹窗 -->
    <el-dialog v-model="reviewDlg" :title="decision === 'APPROVED' ? '允许现场售卖' : '资质不足，要求下架'" width="560px">
      <template v-if="current">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="摊位">{{ current.shopName }}</el-descriptions-item>
          <el-descriptions-item label="换品内容">{{ current.newGoods }}（{{ current.changeType === 'ADD' ? '临时新增' : '更换卖品' }}）</el-descriptions-item>
          <el-descriptions-item v-if="current.foodRelated" label="食品资质">
            <el-tag size="small" :type="current.foodQualification === 'COMPLETE' ? 'success' : 'danger'">
              {{ current.foodQualificationLabel }}
            </el-tag>
            {{ current.foodLicenseNo }}
          </el-descriptions-item>
          <el-descriptions-item label="摊位条件">
            供电{{ current.stallPowerAvailable ? current.stallPowerCapacity + 'W' : '无' }}
            <template v-if="current.foodRelated">｜食品区{{ current.foodZone ? '是' : '否' }}｜卫生条件{{ current.hygieneReady ? '合格' : '不合格' }}</template>
          </el-descriptions-item>
        </el-descriptions>

        <template v-if="decision === 'APPROVED'">
          <el-alert type="success" :closable="false" style="margin:12px 0;">
            放行后将更新报名品类、用电需求并向摊主公示食品安全提示；资质/卫生/食品区/用电任一不满足将被拦截。
          </el-alert>
          <el-form label-width="110px">
            <el-form-item v-if="current.foodRelated" label="食品安全提示">
              <el-input v-model="reviewForm.foodSafetyNotice" type="textarea" :rows="3"
                        placeholder="留空使用默认提示：健康证、明码标价、防尘防蝇、生熟分开、当日剩余不得续售等" />
            </el-form-item>
            <el-form-item label="审核意见">
              <el-input v-model="reviewForm.reviewComment" placeholder="如：资质已核验，限当日售完即止" />
            </el-form-item>
          </el-form>
        </template>

        <template v-else>
          <el-alert type="error" :closable="false" style="margin:12px 0;">
            下架将保留现场照片、登记食品风险事件并扣减信用（-15，已售需退款再 -5）；
            摊主将被标记食品风险，后续食品类报名受限，且下次报名需管理员提前复核。
          </el-alert>
          <el-form label-width="110px">
            <el-form-item label="现场照片">
              <el-input v-model="current.photoUrls" type="textarea" :rows="2" disabled />
              <el-text type="danger" v-if="!current.photoUrls">缺少现场照片，下架前请回到登记表补充照片</el-text>
            </el-form-item>
            <el-form-item label="下架前已售数量">
              <el-input-number v-model="reviewForm.soldQuantity" :min="0" />
              <el-text type="info" size="small" style="margin-left:8px;">份</el-text>
            </el-form-item>
            <el-form-item label="是否需要退款">
              <el-switch v-model="reviewForm.refundNeeded" />
            </el-form-item>
            <el-form-item v-if="reviewForm.refundNeeded" label="退款处置说明">
              <el-input v-model="reviewForm.refundNote" type="textarea" :rows="2"
                        placeholder="如：已现场登记购买居民联系方式，48 小时内原路退款" />
            </el-form-item>
            <el-form-item label="处理意见">
              <el-input v-model="reviewForm.reviewComment" placeholder="责令下架原因与整改要求" />
            </el-form-item>
          </el-form>
        </template>
      </template>
      <template #footer>
        <el-button @click="reviewDlg = false">取消</el-button>
        <el-button :type="decision === 'APPROVED' ? 'success' : 'danger'" @click="submitReview">
          确认{{ decision === 'APPROVED' ? '允许售卖' : '责令下架' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailDlg" title="换品审核单详情" width="600px">
      <template v-if="current">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="摊位">{{ current.shopName }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="statusType(current.status)">{{ current.statusLabel }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="原申报内容" :span="2">{{ current.originalGoods }}</el-descriptions-item>
          <el-descriptions-item label="换品内容" :span="2">{{ current.newGoods }}（{{ current.changeType === 'ADD' ? '临时新增' : '更换卖品' }}）</el-descriptions-item>
          <el-descriptions-item label="换品品类">{{ current.newCategoryLabel }}</el-descriptions-item>
          <el-descriptions-item label="涉及食品">{{ current.foodRelated ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item v-if="current.foodRelated" label="食品资质">
            {{ current.foodQualificationLabel }} {{ current.foodLicenseNo }}
          </el-descriptions-item>
          <el-descriptions-item v-if="current.foodRelated" label="卫生条件">{{ current.hygieneReady ? '合格' : '不合格' }}</el-descriptions-item>
          <el-descriptions-item label="摊位供电">{{ current.stallPowerAvailable ? current.stallPowerCapacity + 'W' : '无' }}</el-descriptions-item>
          <el-descriptions-item label="食品区">{{ current.foodZone ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="新增设备功率">{{ current.powerNeededWatt }}W</el-descriptions-item>
          <el-descriptions-item label="条件备注" :span="2">{{ current.stallConditionNote || '—' }}</el-descriptions-item>
          <el-descriptions-item label="现场照片" :span="2">{{ current.photoUrls || '—' }}</el-descriptions-item>
          <el-descriptions-item label="登记时间">{{ current.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="处理时间">{{ current.reviewedAt || '—' }}</el-descriptions-item>
          <el-descriptions-item v-if="current.status !== 'PENDING'" label="审核意见" :span="2">{{ current.reviewComment || '—' }}</el-descriptions-item>
          <el-descriptions-item v-if="current.foodSafetyNotice" label="食品安全提示" :span="2">{{ current.foodSafetyNotice }}</el-descriptions-item>
          <el-descriptions-item v-if="current.status === 'REMOVED'" label="已售数量">{{ current.soldQuantity }} 份</el-descriptions-item>
          <el-descriptions-item v-if="current.status === 'REMOVED'" label="退款">{{ current.refundNeeded ? '需要：' + (current.refundNote || '') : '无需退款' }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api/client'
import { STALL_TYPES, FOOD_QUAL } from '../../dict'

const eventId = Number(localStorage.getItem('cm_event_id'))
const apps = ref([])
const changes = ref([])
const filter = ref('PENDING')

const form = reactive({
  applicationId: null,
  newGoods: '',
  changeType: 'ADD',
  newCategory: 'FOOD',
  foodRelated: true,
  foodQualification: 'INCOMPLETE',
  foodLicenseNo: '',
  powerNeededWatt: 0,
  stallPowerAvailable: false,
  stallPowerCapacity: 0,
  foodZone: false,
  hygieneReady: false,
  stallConditionNote: '',
  photoUrls: '',
  remark: ''
})

const checkedInApps = computed(() =>
  apps.value.filter((a) => a.status === 'APPROVED' && a.checkInStatus === 'CHECKED_IN'))
const selected = computed(() => apps.value.find((a) => a.id === form.applicationId))

// 选择摊位后自动回填摊位条件（工作人员可现场修改）
watch(() => form.applicationId, () => {
  const a = selected.value
  if (!a?.stall) {
    return
  }
  form.stallPowerAvailable = !!a.stall.powerAvailable
  form.stallPowerCapacity = a.stall.powerCapacity || 0
  form.foodZone = a.stall.zone === '食品区'
})

const reviewDlg = ref(false)
const detailDlg = ref(false)
const decision = ref('APPROVED')
const current = ref(null)
const reviewForm = reactive({
  foodSafetyNotice: '',
  reviewComment: '',
  soldQuantity: 0,
  refundNeeded: false,
  refundNote: ''
})

function statusType(s) {
  return s === 'APPROVED' ? 'success' : s === 'REMOVED' ? 'danger' : 'warning'
}
function onCategoryChange(v) {
  form.foodRelated = v === 'FOOD'
}

async function submit() {
  if (!form.applicationId || !form.newGoods) {
    ElMessage.warning('请选择摊位并填写换品内容')
    return
  }
  if (form.foodRelated && !form.foodQualification) {
    ElMessage.warning('请核验食品资质状态')
    return
  }
  await api.post('/onsite/goods-changes', { ...form })
  ElMessage.success('换品已登记，可在右侧进行审核处理')
  Object.assign(form, {
    newGoods: '', changeType: 'ADD', newCategory: 'FOOD', foodRelated: true,
    foodQualification: 'INCOMPLETE', foodLicenseNo: '', powerNeededWatt: 0,
    stallPowerAvailable: false, stallPowerCapacity: 0, foodZone: false,
    hygieneReady: false, stallConditionNote: '', photoUrls: '', remark: ''
  })
  load()
}

function openReview(row, dec) {
  current.value = row
  decision.value = dec
  Object.assign(reviewForm, {
    foodSafetyNotice: '', reviewComment: '',
    soldQuantity: row.soldQuantity || 0,
    refundNeeded: !!row.refundNeeded, refundNote: row.refundNote || ''
  })
  reviewDlg.value = true
}
function showDetail(row) {
  current.value = row
  detailDlg.value = true
}
async function submitReview() {
  if (decision.value === 'REMOVE' && !current.value.photoUrls) {
    ElMessage.error('下架处置必须保留现场照片')
    return
  }
  try {
    await api.post(`/onsite/goods-changes/${current.value.id}/review`, {
      decision: decision.value,
      ...reviewForm
    })
    ElMessage.success(decision.value === 'APPROVED'
      ? '已允许售卖，品类/用电/食品安全提示已更新'
      : '已责令下架，食品风险事件与信用处置已记录')
    reviewDlg.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function load() {
  apps.value = await api.get(`/events/${eventId}/applications`)
  const url = `/onsite/events/${eventId}/goods-changes` + (filter.value ? `?status=${filter.value}` : '')
  changes.value = await api.get(url)
}
onMounted(load)
</script>
