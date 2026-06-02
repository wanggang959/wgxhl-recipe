import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
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

export default createRouter({
  history: createWebHashHistory(),
  routes,
})
