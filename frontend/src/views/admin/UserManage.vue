<template>
  <div class="page-container">
    <el-card class="section-card">
      <template #header>
        <div style="display:flex; justify-content:space-between; align-items:center;">
          <span>账号管理（管理员可创建摊主 / 现场工作人员账号；摊主也可自助注册）</span>
          <el-button type="primary" :icon="Plus" @click="openDialog">新建账号</el-button>
        </div>
      </template>
      <el-table :data="users" border stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="username" label="用户名" width="130" />
        <el-table-column prop="realName" label="姓名/组织" width="180" />
        <el-table-column prop="phone" label="电话" width="140" />
        <el-table-column label="角色" width="120">
          <template #default="{ row }">
            <el-tag :type="roleTag(row.role)" size="small">{{ roleLabel(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="信用分" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.role === 'VENDOR'" :type="row.creditScore >= 100 ? 'success' : row.creditScore >= 80 ? 'warning' : 'danger'">
              {{ row.creditScore }}
            </el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="报名限制" min-width="200">
          <template #default="{ row }">
            <template v-if="row.role === 'VENDOR'">
              <el-tag v-if="row.nextApplyReviewRequired" size="small" type="danger" style="margin-right:4px;">
                下次报名需提前复核
              </el-tag>
              <el-tag v-if="row.foodRiskLevel >= 1" size="small" :type="row.foodRiskLevel >= 2 ? 'danger' : 'warning'">
                {{ dictLabel(FOOD_RISK_LEVELS, row.foodRiskLevel) }}
              </el-tag>
              <span v-if="!row.nextApplyReviewRequired && !row.foodRiskLevel" style="color:#c0c4cc;">无</span>
            </template>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button v-if="row.role === 'VENDOR' && row.nextApplyReviewRequired"
                       size="small" type="success" @click="clearFlag(row)">
              完成提前复核
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialog" title="新建账号" width="460px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="角色">
          <el-radio-group v-model="form.role">
            <el-radio value="VENDOR">摊主</el-radio>
            <el-radio value="STAFF">现场工作人员</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="用户名"><el-input v-model="form.username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" show-password /></el-form-item>
        <el-form-item label="姓名/组织"><el-input v-model="form.realName" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../../api/client'
import { FOOD_RISK_LEVELS, dictLabel } from '../../dict'

const users = ref([])
const dialog = ref(false)
const form = reactive({ role: 'VENDOR', username: '', password: '', realName: '', phone: '' })

function roleLabel(r) {
  return { ADMIN: '社区管理员', STAFF: '现场工作人员', VENDOR: '摊主' }[r] || r
}
function roleTag(r) {
  return { ADMIN: 'danger', STAFF: 'warning', VENDOR: 'success' }[r] || ''
}
function openDialog() {
  Object.assign(form, { role: 'VENDOR', username: '', password: '', realName: '', phone: '' })
  dialog.value = true
}
async function save() {
  if (!form.username || !form.password) {
    ElMessage.warning('请填写用户名和密码')
    return
  }
  await api.post(`/admin/users?role=${form.role}`, form)
  ElMessage.success('创建成功')
  dialog.value = false
  load()
}
async function load() {
  users.value = await api.get('/admin/users')
}
async function clearFlag(row) {
  await ElMessageBox.confirm(
    `确认已完成对「${row.realName || row.username}」的提前复核？复核通过后该摊主可正常报名下次活动。`,
    '完成提前复核', { type: 'warning' }
  )
  await api.post(`/admin/users/${row.id}/clear-review-flag`)
  ElMessage.success('已清除「下次报名需提前复核」标记')
  load()
}
onMounted(load)
</script>
