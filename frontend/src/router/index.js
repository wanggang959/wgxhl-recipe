import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/LoginView.vue'),
  },
  {
    path: '/',
    component: () => import('../views/MainLayout.vue'),
    children: [
      {
        path: '',
        redirect: '/recipes',
      },
      {
        path: '/recipes',
        name: 'RecipeList',
        component: () => import('../views/RecipeList.vue'),
      },
      {
        path: '/favorites',
        name: 'FavoriteList',
        component: () => import('../views/FavoriteList.vue'),
      },
      {
        path: '/want',
        name: 'WantList',
        component: () => import('../views/WantList.vue'),
      },
      {
        path: '/todo',
        name: 'TodoList',
        component: () => import('../views/TodoList.vue'),
      },
      {
        path: '/todo/create',
        name: 'TodoCreate',
        component: () => import('../views/TodoEditor.vue'),
      },
      {
        path: '/todo/:id/edit',
        name: 'TodoEdit',
        component: () => import('../views/TodoEditor.vue'),
      },
      {
        path: '/todo/:id',
        name: 'TodoDetail',
        component: () => import('../views/TodoDetailView.vue'),
      },
      {
        path: '/todo/summary',
        name: 'TodoSummary',
        component: () => import('../views/TodoSummary.vue'),
      },
      {
        path: '/profile',
        name: 'Profile',
        component: () => import('../views/ProfileView.vue'),
      },
    ],
  },
  {
    path: '/manage/base',
    name: 'BaseManage',
    component: () => import('../views/BaseManage.vue'),
  },
  {
    path: '/recipe/create',
    name: 'RecipeCreate',
    component: () => import('../views/RecipeEditor.vue'),
  },
  {
    path: '/recipe/:id/edit',
    name: 'RecipeEdit',
    component: () => import('../views/RecipeEditor.vue'),
  },
  {
    path: '/recipe/:id',
    name: 'RecipeDetail',
    component: () => import('../views/RecipeDetail.vue'),
  },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

function getCurrentUser() {
  const raw = localStorage.getItem('wgxhl_recipe_user')
  if (!raw) return null
  try {
    const user = JSON.parse(raw)
    if (!user?.id || !user?.token || !user?.userRole) {
      localStorage.removeItem('wgxhl_recipe_user')
      return null
    }
    return user
  } catch (error) {
    localStorage.removeItem('wgxhl_recipe_user')
    return null
  }
}

router.beforeEach((to) => {
  const user = getCurrentUser()
  const isLogin = Boolean(user?.id && user?.token && user?.userRole)

  if (to.path === '/login') {
    if (isLogin) return '/recipes'
    return true
  }

  if (!isLogin) {
    return {
      path: '/login',
      query: { redirect: to.fullPath },
    }
  }

  const adminOnly = to.path === '/manage/base'
    || to.path === '/recipe/create'
    || /^\/recipe\/[^/]+\/edit$/.test(to.path)
  if (!adminOnly) return true
  if (user.userRole === 'admin' || user.userRole === 'super_admin') return true
  return '/recipes'
})

export default router
