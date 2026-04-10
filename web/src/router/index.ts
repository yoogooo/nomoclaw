import { createRouter, createWebHashHistory } from "vue-router";
import ChatPage from "@/pages/ChatPage.vue";
import CronPage from "@/pages/CronPage.vue";
import AgentsPage from "@/pages/AgentsPage.vue";
import SettingsPage from "@/pages/SettingsPage.vue";
import ChannelsPage from "@/pages/ChannelsPage.vue";
import ModelsPage from "@/pages/ModelsPage.vue";
import ForbiddenPage from "@/pages/ForbiddenPage.vue";
import DesignSystemPage from "@/pages/design-system/DesignSystemPage.vue";

export const router = createRouter({
  history: createWebHashHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: "/",
      name: "chat",
      component: ChatPage
    },
    {
      path: "/index.html",
      redirect: "/"
    },
    {
      path: "/cron",
      name: "cron",
      component: CronPage
    },
    {
      path: "/agents",
      name: "agents",
      component: AgentsPage
    },
    {
      path: "/settings",
      name: "settings",
      component: SettingsPage
    },
    {
      path: "/channels",
      name: "channels",
      component: ChannelsPage
    },
    {
      path: "/models",
      name: "models",
      component: ModelsPage
    },
    {
      path: "/design-system",
      redirect: "/design-system/light"
    },
    {
      path: "/design-system/light",
      name: "design-system-light",
      component: DesignSystemPage
    },
    {
      path: "/design-system/dark",
      name: "design-system-dark",
      component: DesignSystemPage
    },
    {
      path: "/forbidden",
      name: "forbidden",
      component: ForbiddenPage
    }
  ]
});
