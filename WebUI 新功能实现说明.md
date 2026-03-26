# WebUI 新功能实现说明

## 已完成后端 API

### 1. 公开插件列表 API（无需登录）
- **路径**: `GET /api/public/plugins`
- **功能**: 获取所有启用的插件列表
- **返回**: `PluginSimpleDTO[]`

### 2. 用户仪表盘 API
- **路径**: `GET /api/user/dashboard`
- **功能**: 获取统计信息（用户数、授权码数、插件数）
- **返回**: `{totalUsers, totalLicenses, totalPlugins}`

### 3. 插件领取 API
- **路径**: `POST /api/user/claim`
- **参数**: `{targetPluginId, excludePlugins?}`
- **功能**: 满足条件可领取插件授权码

### 4. 插件置换 API（管理员）
- **路径**: `POST /api/admin/plugins/exchange`
- **参数**: `{fromPluginId, toPluginId}`
- **功能**: 将插件 A 的所有授权码转换为插件 B

## 前端实现要点

### 需要在 index.html 添加的功能

#### 1. 插件列表页面（普通用户可访问）
```html
<!-- 在 sidebar 添加菜单 -->
<div class="menu-item" :class="{active: currentPage==='pluginlist'}" @click="currentPage='pluginlist'">🔌 插件列表</div>

<!-- 在主内容区添加页面 -->
<div v-if="currentPage==='pluginlist'" class="card">
    <div class="card-title">可用插件</div>
    <table>
        <thead><tr><th>插件名</th><th>显示名</th><th>描述</th><th>版本</th></tr></thead>
        <tbody>
            <tr v-for="p in publicPlugins" :key="p.id">
                <td>{{ p.name }}</td>
                <td>{{ p.displayName }}</td>
                <td>{{ p.description || '-' }}</td>
                <td>{{ p.version }}</td>
            </tr>
        </tbody>
    </table>
</div>
```

#### 2. 用户仪表盘页面
```html
<!-- 在 sidebar 添加菜单（用户可见） -->
<div class="menu-item" :class="{active: currentPage==='userdash'}" @click="currentPage='userdash'" v-if="!isAdmin">📊 我的统计</div>

<!-- 在主内容区添加页面 -->
<div v-if="currentPage==='userdash'" class="card">
    <div class="stats-grid">
        <div class="stat-card"><div class="stat-value">{{ userStats.totalUsers || 0 }}</div><div class="stat-label">总用户数</div></div>
        <div class="stat-card"><div class="stat-value">{{ userStats.totalLicenses || 0 }}</div><div class="stat-label">我的授权码</div></div>
        <div class="stat-card"><div class="stat-value">{{ userStats.totalPlugins || 0 }}</div><div class="stat-label">总插件数</div></div>
    </div>
</div>
```

#### 3. 插件置换 UI（管理员）
```html
<!-- 在插件管理页面添加置换按钮 -->
<button class="btn btn-warning btn-small" @click="showExchangeDialog=true">置换插件</button>

<!-- 置换弹窗 -->
<div v-if="showExchangeDialog" class="modal-overlay" @click.self="showExchangeDialog=false">
    <div class="modal">
        <div class="modal-title">插件置换</div>
        <div class="form-group">
            <label>从插件</label>
            <select v-model="exchangeForm.fromPluginId">
                <option :value="null">请选择</option>
                <option v-for="p in plugins" :key="p.id" :value="p.id">{{ p.displayName }}</option>
            </select>
        </div>
        <div class="form-group">
            <label>到插件</label>
            <select v-model="exchangeForm.toPluginId">
                <option :value="null">请选择</option>
                <option v-for="p in plugins" :key="p.id" :value="p.id">{{ p.displayName }}</option>
            </select>
        </div>
        <div class="modal-footer">
            <button class="btn" @click="showExchangeDialog=false">取消</button>
            <button class="btn btn-primary" @click="doExchange" :disabled="loading">置换</button>
        </div>
    </div>
</div>
```

#### 4. 插件领取 UI
```html
<!-- 在我的授权页面添加领取按钮 -->
<button class="btn btn-success btn-small" @click="showClaimDialog=true">领取插件</button>

<!-- 领取弹窗 -->
<div v-if="showClaimDialog" class="modal-overlay" @click.self="showClaimDialog=false">
    <div class="modal">
        <div class="modal-title">领取插件</div>
        <div class="form-group">
            <label>选择要领取的插件</label>
            <select v-model="claimForm.targetPluginId">
                <option :value="null">请选择</option>
                <option v-for="p in publicPlugins" :key="p.id" :value="p.id">{{ p.displayName }}</option>
            </select>
        </div>
        <div class="modal-footer">
            <button class="btn" @click="showClaimDialog=false">取消</button>
            <button class="btn btn-primary" @click="doClaim" :disabled="loading">领取</button>
        </div>
    </div>
</div>
```

#### 5. 生成授权码用户名输入优化
```html
<!-- 在生成授权码弹窗中修改用户名输入 -->
<div class="form-group">
    <label>授权给用户（可选，留空为通用码）</label>
    <div class="input-group">
        <input v-model="genForm.username" placeholder="输入 + 号展开选择" @input="onUsernameInput">
        <button v-if="genForm.username && genForm.username.startsWith('+')" 
                class="btn btn-primary" @click="showUserSelect=true">▼</button>
    </div>
    <!-- 用户选择下拉框 -->
    <div v-if="showUserSelect" style="margin-top:5px;max-height:150px;overflow-y:auto;border:1px solid #ddd;border-radius:4px;">
        <div v-for="u in users" :key="u.id" 
             style="padding:8px;cursor:pointer;" 
             @click="selectUser(u.username)"
             @mouseover="$event.target.style.background='#f5f7fa'"
             @mouseout="$event.target.style.background='white'">
            {{ u.username }}
        </div>
    </div>
</div>
```

## JavaScript 逻辑

在 `<script>` 部分添加以下变量和函数：

```javascript
const { createApp, ref, onMounted } = Vue;
createApp({
    setup() {
        
        // 新增变量
        const publicPlugins = ref([]), userStats = ref({}), showUserSelect = ref(false);
        const exchangeForm = ref({fromPluginId:null,toPluginId:null});
        const claimForm = ref({targetPluginId:null,excludePlugins:null});
        const showExchangeDialog = ref(false), showClaimDialog = ref(false);
        
        // 加载公开插件列表
        const loadPublicPlugins = async () => {
            try { 
                const r = await axios.get('/api/public/plugins'); 
                if(r.data.success) publicPlugins.value = r.data.data; 
            } catch(e){}
        };
        
        // 加载用户仪表盘
        const loadUserDashboard = async () => {
            try { 
                const r = await api.get('/user/dashboard'); 
                if(r.data.success) userStats.value = r.data.data; 
            } catch(e){}
        };
        
        // 插件置换
        const doExchange = async () => {
            loading.value = true;
            try { 
                const r = await api.post('/admin/plugins/exchange', exchangeForm.value); 
                if(r.data.success){
                    showMsg(r.data.message);
                    showExchangeDialog.value=false;
                    loadLicenses();
                } else {
                    showMsg(r.data.message,'error');
                }
            } catch(e){
                showMsg(e.response?.data?.message||'置换失败','error');
            }
            loading.value = false;
        };
        
        // 插件领取
        const doClaim = async () => {
            loading.value = true;
            try { 
                const r = await api.post('/user/claim', claimForm.value); 
                if(r.data.success){
                    showMsg(r.data.message);
                    showClaimDialog.value=false;
                    loadMyAuth();
                } else {
                    showMsg(r.data.message,'error');
                }
            } catch(e){
                showMsg(e.response?.data?.message||'领取失败','error');
            }
            loading.value = false;
        };
        
        // 用户名输入处理
        const onUsernameInput = () => {
            if(genForm.value.username && genForm.value.username.startsWith('+')){
                showUserSelect.value = true;
            } else {
                showUserSelect.value = false;
            }
        };
        
        // 选择用户
        const selectUser = (username) => {
            genForm.value.username = username;
            showUserSelect.value = false;
        };
        
        // 在 onMounted 中添加调用
        onMounted(() => {
            loadPublicPlugins(); // 加载公开插件列表
        });
        
        return { 
            // ... existing returns ...
            publicPlugins,userStats,exchangeForm,claimForm,showExchangeDialog,showClaimDialog,showUserSelect,
            loadPublicPlugins,loadUserDashboard,doExchange,doClaim,onUsernameInput,selectUser
        };
    }
}).mount('#app');
```

## 注意事项

1. 所有 API 调用都需要正确的认证头（JWT token）
2. 公开 API `/api/public/plugins` 不需要认证
3. 用户仪表盘和领取功能需要登录
4. 插件置换需要管理员权限
5. 样式可以根据实际需求调整

## 测试步骤

1. 访问 `/api/public/plugins` 确认公开 API 正常
2. 登录后访问用户仪表盘
3. 尝试领取插件
4. 管理员测试插件置换
5. 测试生成授权码时输入 `+` 号展开用户选择
