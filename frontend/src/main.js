import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import './styles/workbench.css'
import { refreshModels } from './lib/models'

const ScanNew = () => import('./views/ScanNew.vue')
const ScanDashboard = () => import('./views/ScanDashboard.vue')
const ArticleList = () => import('./views/ArticleList.vue')
const TopicMining = () => import('./views/TopicMining.vue')
const ExpertList = () => import('./views/ExpertList.vue')
const MailCenter = () => import('./views/MailCenter.vue')
const TaskList = () => import('./views/TaskList.vue')

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/scan/new' },
    { path: '/settings/models', component: () => import('./views/ModelSettings.vue') },
    { path: '/scan/new', component: ScanNew },
    { path: '/tasks', component: TaskList },
    { path: '/scan/:id', component: ScanDashboard, props: true },
    { path: '/scan/:id/articles', component: ArticleList, props: true },
    { path: '/scan/:id/topics', component: TopicMining, props: true },
    { path: '/scan/:id/experts', component: ExpertList, props: true },
    { path: '/scan/:id/mail', component: MailCenter, props: true }
  ]
})

const app = createApp(App)
app.use(router)
app.use(ElementPlus)
refreshModels().finally(() => app.mount('#app'))
