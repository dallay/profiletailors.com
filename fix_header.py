import re

path = 'apps/web/app/src/App.vue'
with open(path, 'r') as f:
    content = f.read()

new_header = '''
      <SidebarHeader class="gap-3">
        <div ref="workspaceMenuRef" class="relative">
          <div
            v-if="workspaceMenuOpen"
            class="absolute top-0 left-0 z-50 w-full rounded-2xl border border-border-subtle bg-bg-surface p-2 shadow-2xl group-data-[collapsible=icon]:left-full group-data-[collapsible=icon]:ml-2 group-data-[collapsible=icon]:w-64"
          >
            <div class="px-2 py-2 group-data-[collapsible=icon]:hidden">
              <p class="truncate font-mono text-[10px] uppercase tracking-[0.14em] text-text-secondary">
                Workspaces
              </p>
            </div>

            <div class="my-2 border-t border-border-subtle" />

            <div class="space-y-1">
              <button
                v-for="account in accountOptions"
                :key="account.name"
                class="flex w-full items-center gap-3 rounded-xl border px-3 py-2 text-left text-sm transition-all"
                :class="activeAccount.name === account.name
                  ? 'border-border-visible bg-bg-primary text-text-display'
                  : 'border-transparent text-text-secondary hover:border-border-subtle hover:bg-bg-primary/70 hover:text-text-display'"
                type="button"
                @click="selectWorkspace(account)"
              >
                <div class="flex size-8 shrink-0 items-center justify-center rounded-lg border border-border-visible bg-bg-primary text-text-display">
                  <component :is="account.icon" class="size-4" />
                </div>
                <div class="min-w-0 flex-1">
                  <p class="truncate text-sm font-medium text-current">
                    {{ account.name }}
                  </p>
                  <p class="truncate text-[10px] text-text-secondary">
                    {{ account.plan }}
                  </p>
                </div>
              </button>
            </div>

            <div class="my-2 border-t border-border-subtle" />

            <button
              class="flex w-full items-center gap-3 rounded-xl border border-transparent px-3 py-2 text-left text-sm text-text-secondary transition-colors hover:border-border-subtle hover:bg-bg-primary/70 hover:text-text-display"
              type="button"
            >
              <Plus class="size-4 shrink-0" />
              <span>Add workspace</span>
            </button>
          </div>

          <button
            class="flex w-full items-center gap-3 rounded-2xl border border-border-subtle bg-bg-surface/70 px-3 py-2 transition-all hover:border-border-visible hover:bg-bg-surface group-data-[collapsible=icon]:justify-center group-data-[collapsible=icon]:px-2"
            type="button"
            @click.stop="toggleWorkspaceMenu"
          >
            <div class="flex size-10 shrink-0 items-center justify-center rounded-xl bg-text-display text-bg-primary shadow-lg">
              <component :is="activeAccount.icon" class="size-4" />
            </div>

            <div class="min-w-0 flex-1 text-left group-data-[collapsible=icon]:hidden">
              <p class="truncate font-mono text-[11px] font-bold uppercase tracking-[0.18em] text-text-display">
                {{ activeAccount.name }}
              </p>
              <p class="truncate text-xs text-text-secondary">
                {{ activeAccount.plan }}
              </p>
            </div>

            <ChevronsUpDown class="size-4 shrink-0 text-text-secondary group-data-[collapsible=icon]:hidden" />
          </button>
        </div>
      </SidebarHeader>
'''

pattern = re.compile(r'<SidebarHeader class=gap-3>.*?</SidebarHeader>', re.DOTALL)
if not pattern.search(content):
    pattern = re.compile(r'<SidebarHeader class="gap-3">.*?</SidebarHeader>', re.DOTALL)

content = pattern.sub(new_header, content)

with open(path, 'w') as f:
    f.write(content)
