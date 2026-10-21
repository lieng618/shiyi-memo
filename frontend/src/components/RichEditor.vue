<!--
  富文本编辑器，基于 wangEditor 5，实现搬运自 art-design-pro 的 ArtWangEditor 组件。
  插件地址：https://www.wangeditor.com/
  与 art 版的差异：上传走站内文件接口（fileApi），并加了附件按钮和导出分组菜单。
-->
<script lang="ts">
  // 导出菜单与页面（NoteDetailView）的桥接：菜单全局注册，exec 时找当前打开的笔记页
  export const exportTriggerStore = globalThis as unknown as {
    __shiyiExportNote?: ((format: 'md' | 'html' | 'doc' | 'pdf' | 'png') => void) | null
  }
</script>
<template>
  <div
    class="rich-editor"
    @dragenter.capture="onDragEnter"
    @dragover.capture="onDragOver"
    @dragleave.capture="onDragLeave"
    @drop.capture="onDrop"
  >
    <div class="rich-editor-toolbar-row">
      <Toolbar
        class="rich-editor-toolbar"
        :editor="editorRef"
        :mode="mode"
        :defaultConfig="toolbarConfig"
      />
      <input ref="fileInput" type="file" hidden @change="onFilePicked" />
    </div>

    <div class="rich-editor-body" :style="bodyStyle">
      <Editor
        :style="{ height: '100%', overflowY: 'hidden' }"
        v-model="modelValue"
        :mode="mode"
        :defaultConfig="editorConfig"
        @onCreated="onCreateEditor"
      />

      <Transition name="attachment-overlay">
        <div v-if="dragActive" class="attachment-drop-overlay" aria-hidden="true">
          <div class="attachment-drop-panel">
            <span class="attachment-drop-icon">
              <el-icon :size="24"><UploadFilled /></el-icon>
            </span>
            <strong>松开以添加文件</strong>
            <span>图片会插入正文，其他文件会添加为附件</span>
          </div>
        </div>
      </Transition>

      <div
        v-if="uploadTasks.length"
        class="attachment-upload-stack"
        role="status"
        aria-live="polite"
        aria-label="文件上传状态"
      >
        <div
          v-for="task in visibleUploadTasks"
          :key="task.id"
          class="attachment-upload-item"
          :class="`is-${task.state}`"
        >
          <div class="attachment-upload-head">
            <el-icon class="attachment-upload-state" :size="16">
              <Loading v-if="task.state === 'uploading'" class="is-loading" />
              <CircleCheck v-else-if="task.state === 'done'" />
              <CircleClose v-else />
            </el-icon>
            <span class="attachment-upload-copy">
              <span class="attachment-upload-name">{{ task.name }}</span>
              <span class="attachment-upload-meta">{{ uploadTaskLabel(task) }}</span>
            </span>
          </div>
          <el-progress
            :percentage="task.progress"
            :show-text="false"
            :stroke-width="3"
            :status="task.state === 'error' ? 'exception' : undefined"
          />
        </div>
        <div v-if="hiddenUploadCount" class="attachment-upload-more">
          另有 {{ hiddenUploadCount }} 个文件正在处理
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import '@wangeditor/editor/dist/css/style.css'
  import './rich-editor.scss'
  import { computed, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
  import { ElMessage } from 'element-plus'
  import { CircleCheck, CircleClose, Loading, UploadFilled } from '@element-plus/icons-vue'
  import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
  import {
    Boot,
    DomEditor,
    SlateElement,
    SlateTransforms,
  } from '@wangeditor/editor'
  import type { IButtonMenu, IDomEditor, IEditorConfig, IToolbarConfig } from '@wangeditor/editor'
  import { h, type VNode } from 'snabbdom'
  import { fileApi } from '@/api'
  import { copyText } from '@/utils/clipboard'
  import {
    ATTACHMENT_ELEMENT_TYPE,
    ATTACHMENT_FILE_ICON,
    fileDownloadUrl,
    isSiteAttachmentUrl,
  } from '@/utils/attachments'
  import { downloadUrl } from '@/utils/download'
  import type { FileAsset } from '@/types'

  type InsertImageFnType = (url: string, alt: string, href: string) => void
  type InsertVideoFnType = (url: string, poster: string) => void
  type ExportFormat = 'md' | 'html' | 'doc' | 'pdf' | 'png'

  interface AttachmentElement {
    [key: string]: unknown
    type: typeof ATTACHMENT_ELEMENT_TYPE
    url: string
    name: string
    children: { text: string }[]
  }

  function escapeHtml(value: string) {
    return value.replace(/[&<>"']/g, (character) => {
      switch (character) {
        case '&': return '&amp;'
        case '<': return '&lt;'
        case '>': return '&gt;'
        case '"': return '&quot;'
        default: return '&#39;'
      }
    })
  }

  function renderAttachment(
    elemNode: SlateElement,
    _children: VNode[] | null,
    editor: IDomEditor,
  ): VNode {
    const { url, name } = elemNode as unknown as AttachmentElement
    const selected = DomEditor.isNodeSelected(editor, elemNode)
    return h('span', {
      attrs: {
        title: name,
        contenteditable: 'false',
        role: 'group',
        'aria-label': `${name}，附件`,
        'data-selected': selected ? 'true' : '',
        class: 'shiyi-attachment-card',
      },
    }, [
      h('span', {
        attrs: { class: 'shiyi-attachment-icon' },
        props: { innerHTML: ATTACHMENT_FILE_ICON },
      }),
      h('span', { attrs: { class: 'shiyi-attachment-name' } }, name),
      h('a', {
        attrs: {
          class: 'shiyi-attachment-action',
          href: url,
          download: name,
          title: `下载 ${name}`,
          'aria-label': `下载 ${name}`,
        },
        on: {
          mousedown(event: MouseEvent) {
            event.stopPropagation()
          },
          click(event: MouseEvent) {
            event.preventDefault()
            event.stopPropagation()
            downloadUrl(url, name)
            ElMessage.success('已开始下载附件')
          },
        },
      }, '下载'),
    ])
  }

  const ATTACHMENT_MODULE_VERSION = 'attachment-card-v3'
  const attachmentModuleState = globalThis as unknown as {
    __shiyiAttachmentModule?: string
    __shiyiAttachmentPluginRegistered?: boolean
  }

  if (attachmentModuleState.__shiyiAttachmentModule !== ATTACHMENT_MODULE_VERSION) {
    attachmentModuleState.__shiyiAttachmentModule = ATTACHMENT_MODULE_VERSION
    Boot.registerPreParseHtml({
      selector: 'a[href]',
      preParseHtml(element) {
        const href = element.getAttribute('href') ?? ''
        if (!isSiteAttachmentUrl(href) || element.querySelector('img, video, audio')) {
          return element
        }
        const name = element.getAttribute('data-name')
          || element.textContent?.trim()
          || '附件'
        element.setAttribute('data-w-e-type', ATTACHMENT_ELEMENT_TYPE)
        element.setAttribute('data-w-e-is-void', 'true')
        element.setAttribute('data-name', name)
        return element
      },
    })
    Boot.registerRenderElem({
      type: ATTACHMENT_ELEMENT_TYPE,
      renderElem: renderAttachment,
    })
    Boot.registerElemToHtml({
      type: ATTACHMENT_ELEMENT_TYPE,
      elemToHtml(element) {
        const { url, name } = element as unknown as AttachmentElement
        const safeName = escapeHtml(name || '附件')
        return `<span class="shiyi-attachment-card" data-w-e-type="${ATTACHMENT_ELEMENT_TYPE}" ` +
          `data-w-e-is-void="true" data-name="${safeName}" title="${safeName}" ` +
          `aria-label="${safeName}，附件">` +
          `<span class="shiyi-attachment-icon">${ATTACHMENT_FILE_ICON}</span>` +
          `<span class="shiyi-attachment-name">${safeName}</span>` +
          `<a class="shiyi-attachment-action" href="${escapeHtml(url)}" download="${safeName}" ` +
          `aria-label="下载 ${safeName}">下载</a></span>`
      },
    })
    Boot.registerParseElemHtml({
      selector: `[data-w-e-type="${ATTACHMENT_ELEMENT_TYPE}"]`,
      parseElemHtml(element) {
        const name = element.getAttribute('data-name') || element.textContent?.trim() || '附件'
        return {
          type: ATTACHMENT_ELEMENT_TYPE,
          url: element.getAttribute('href')
            || element.querySelector<HTMLAnchorElement>('.shiyi-attachment-action')?.getAttribute('href')
            || '',
          name,
          children: [{ text: '' }],
        } as AttachmentElement
      },
    })
  }

  if (!attachmentModuleState.__shiyiAttachmentPluginRegistered) {
    attachmentModuleState.__shiyiAttachmentPluginRegistered = true
    Boot.registerPlugin((editor) => {
      const { isInline, isVoid } = editor
      editor.isInline = (element) => (
        SlateElement.isElement(element) && element.type === ATTACHMENT_ELEMENT_TYPE
      ) || isInline(element)
      editor.isVoid = (element) => (
        SlateElement.isElement(element) && element.type === ATTACHMENT_ELEMENT_TYPE
      ) || isVoid(element)
      return editor
    })
  }

  /** 附件按钮与组件实例的桥接：菜单类全局注册，exec 时找当前挂载的编辑器组件 */
  type AttachmentTrigger = { trigger: () => void; disabled: () => boolean }
  const triggerStore = globalThis as unknown as { __shiyiAttachmentTrigger?: AttachmentTrigger | null }

  /** 附件上传菜单：实现和内置菜单同款（IButtonMenu），图标、悬停样式自动一致 */
  class UploadAttachmentMenu implements IButtonMenu {
    readonly title = '上传附件'
    readonly iconSvg =
      '<svg viewBox="0 0 1024 1024"><path d="M602.496 240.448A192 192 0 1 1 874.048 512l-316.8 316.8A256 256 0 0 1 195.2 466.752L602.496 59.456l45.248 45.248L240.448 512A192 192 0 0 0 512 783.552l316.8-316.8a128 128 0 1 0-181.056-181.056L353.6 579.904a32 32 0 1 0 45.248 45.248l294.144-294.144 45.312 45.248L444.096 670.4a96 96 0 1 1-135.744-135.744z"></path></svg>'
    readonly tag = 'button'

    getValue(): string {
      return ''
    }

    isActive(): boolean {
      return false
    }

    isDisabled(): boolean {
      const trigger = triggerStore.__shiyiAttachmentTrigger
      return !trigger || trigger.disabled()
    }

    exec(): void {
      triggerStore.__shiyiAttachmentTrigger?.trigger()
    }
  }

  /**
   * 复制链接菜单：出现的位置和内置的 editLink / unLink / viewLink 一致（悬停/选中超链接时的悬浮条），
   * 取链接 url 的方式也照搬内置 ViewLink（DomEditor.getSelectedNodeByType），保证启用条件完全对齐。
   */
  class CopyLinkMenu implements IButtonMenu {
    readonly title = '复制链接'
    readonly tag = 'button'
    readonly iconSvg =
      '<svg viewBox="0 0 1024 1024"><path d="M768 832a128 128 0 0 1-128 128H192A128 128 0 0 1 64 832V384a128 128 0 0 1 128-128v64a64 64 0 0 0-64 64v448a64 64 0 0 0 64 64h448a64 64 0 0 0 64-64z"></path><path d="M384 128a64 64 0 0 0-64 64v448a64 64 0 0 0 64 64h448a64 64 0 0 0 64-64V192a64 64 0 0 0-64-64zm0-64h448a128 128 0 0 1 128 128v448a128 128 0 0 1-128 128H384a128 128 0 0 1-128-128V192A128 128 0 0 1 384 64z"></path></svg>'

    /** 取当前选区所在的 link 节点，和内置 ViewLink 用同一套判断 */
    private getSelectedLinkElem(editor: IDomEditor): { url?: string } | null {
      return DomEditor.getSelectedNodeByType(editor, 'link') as { url?: string } | null
    }

    getValue(editor: IDomEditor): string {
      return this.getSelectedLinkElem(editor)?.url || ''
    }

    isActive(): boolean {
      return false
    }

    isDisabled(editor: IDomEditor): boolean {
      return editor.selection == null || this.getSelectedLinkElem(editor) == null
    }

    exec(editor: IDomEditor, value: string | boolean): void {
      if (this.isDisabled(editor)) return
      const url = typeof value === 'string' ? value : ''
      if (!url) {
        ElMessage.warning('没有可复制的链接')
        return
      }
      void copyText(url).then(
        () => ElMessage.success('已复制链接'),
        () => ElMessage.error('复制失败，请手动复制'),
      )
    }
  }

  /** 单个导出格式的菜单项，出现在「导出」分组下拉里 */
  class ExportNoteMenu implements IButtonMenu {
    readonly tag = 'button'

    constructor(
      readonly title: string,
      private readonly format: ExportFormat,
    ) {}

    getValue(): string {
      return ''
    }

    isActive(): boolean {
      return false
    }

    isDisabled(): boolean {
      // 导出不随编辑器状态受限：进页面就能直接导出当前内容
      return false
    }

    exec(): void {
      exportTriggerStore.__shiyiExportNote?.(this.format)
    }
  }

  /**
   * 导出菜单的唯一数据源：注册的 key 必须和工具栏分组里的 menuKeys 完全一致，
   * 否则工具栏渲染分组时找不到菜单工厂会抛错，整组按钮都不会出现。
   */
  const EXPORT_MENUS: { key: string; title: string; format: ExportFormat }[] = [
    { key: 'shiyiExportMd', title: '导出 Markdown', format: 'md' },
    { key: 'shiyiExportHtml', title: '导出网页', format: 'html' },
    { key: 'shiyiExportDoc', title: '导出 Word', format: 'doc' },
    { key: 'shiyiExportPdf', title: '导出 PDF', format: 'pdf' },
    { key: 'shiyiExportPng', title: '导出长图', format: 'png' },
  ]

  try {
    // vite HMR 会重复执行模块，重复注册直接忽略
    Boot.registerMenu({ key: 'shiyiUploadAttachment', factory: () => new UploadAttachmentMenu() })
    Boot.registerMenu({ key: 'shiyiCopyLink', factory: () => new CopyLinkMenu() })
    for (const { key, title, format } of EXPORT_MENUS) {
      Boot.registerMenu({ key, factory: () => new ExportNoteMenu(title, format) })
    }
  } catch (error) {
    ;(globalThis as unknown as { __shiyiMenuRegError?: unknown }).__shiyiMenuRegError = String(error)
  }

  // Props 定义
  interface Props {
    /** 编辑器高度，100% 表示撑满父容器 */
    height?: string
    /** 编辑器模式 */
    mode?: 'default' | 'simple'
    /** 占位符文本 */
    placeholder?: string
    /** 附件归属的笔记，上传的文件会关联到它 */
    memoId?: number | null
  }

  const props = withDefaults(defineProps<Props>(), {
    height: '100%',
    mode: 'default',
    placeholder: '随手写点什么',
    memoId: null,
  })

  // v-model 直接绑定编辑器的 HTML 内容
  const modelValue = defineModel<string>({ required: true })
  const emit = defineEmits<{ uploaded: [FileAsset] }>()

  // 编辑器实例
  const editorRef = shallowRef<IDomEditor>()
  const fileInput = ref<HTMLInputElement | null>(null)

  interface UploadTask {
    id: number
    name: string
    progress: number
    state: 'uploading' | 'done' | 'error'
  }

  const uploadTasks = ref<UploadTask[]>([])
  const visibleUploadTasks = computed(() => uploadTasks.value.slice(-3))
  const hiddenUploadCount = computed(() => Math.max(0, uploadTasks.value.length - 3))
  const uploading = computed(() => uploadTasks.value.some((task) => task.state === 'uploading'))
  const dragDepth = ref(0)
  const dragActive = computed(() => dragDepth.value > 0)
  const uploadTimers = new Set<ReturnType<typeof setTimeout>>()
  let uploadTaskId = 0

  function patchUploadTask(id: number, patch: Partial<UploadTask>) {
    uploadTasks.value = uploadTasks.value.map((task) => (
      task.id === id ? { ...task, ...patch } : task
    ))
  }

  function finishUploadTask(id: number, state: 'done' | 'error') {
    patchUploadTask(id, { progress: state === 'done' ? 100 : uploadTasks.value.find((task) => task.id === id)?.progress ?? 0, state })
    const timer = setTimeout(() => {
      uploadTasks.value = uploadTasks.value.filter((task) => task.id !== id)
      uploadTimers.delete(timer)
    }, state === 'done' ? 900 : 3500)
    uploadTimers.add(timer)
  }

  function uploadTaskLabel(task: UploadTask) {
    if (task.state === 'done') return '已完成'
    if (task.state === 'error') return '上传失败'
    if (task.progress >= 100) return '正在处理…'
    return `上传中 ${task.progress}%`
  }

  /** 高度用 100% 时交给 flex 布局撑满，否则用固定值 */
  const bodyStyle = computed(() => (props.height === '100%' ? undefined : { height: props.height }))

  /** 工具栏分组菜单（与 wangEditor 内置分组同构） */
  interface MenuGroupEntry {
    key: string
    title: string
    iconSvg: string
    menuKeys: string[]
  }

  // 分组菜单图标，取自 wangEditor 内置常量；导出分组用 Download 图标
  const MORE_STYLE_SVG =
    '<svg viewBox="0 0 1024 1024"><path d="M204.8 505.6m-76.8 0a76.8 76.8 0 1 0 153.6 0 76.8 76.8 0 1 0-153.6 0Z"></path><path d="M505.6 505.6m-76.8 0a76.8 76.8 0 1 0 153.6 0 76.8 76.8 0 1 0-153.6 0Z"></path><path d="M806.4 505.6m-76.8 0a76.8 76.8 0 1 0 153.6 0 76.8 76.8 0 1 0-153.6 0Z"></path></svg>'
  const JUSTIFY_SVG =
    '<svg viewBox="0 0 1024 1024"><path d="M768 793.6v102.4H51.2v-102.4h716.8z m204.8-230.4v102.4H51.2v-102.4h921.6z m-204.8-230.4v102.4H51.2v-102.4h716.8zM972.8 102.4v102.4H51.2V102.4h921.6z"></path></svg>'
  const INDENT_SVG =
    '<svg viewBox="0 0 1024 1024"><path d="M0 64h1024v128H0z m384 192h640v128H384z m0 192h640v128H384z m0 192h640v128H384zM0 832h1024v128H0z m0-128V320l256 192z"></path></svg>'
  const IMAGE_SVG =
    '<svg viewBox="0 0 1024 1024"><path d="M959.877 128l0.123 0.123v767.775l-0.123 0.122H64.102l-0.122-0.122V128.123l0.122-0.123h895.775zM960 64H64C28.795 64 0 92.795 0 128v768c0 35.205 28.795 64 64 64h896c35.205 0 64-28.795 64-64V128c0-35.205-28.795-64-64-64zM832 288.01c0 53.023-42.988 96.01-96.01 96.01s-96.01-42.987-96.01-96.01S682.967 192 735.99 192 832 234.988 832 288.01zM896 832H128V704l224.01-384 256 320h64l224.01-192z"></path></svg>'
  const VIDEO_SVG =
    '<svg viewBox="0 0 1024 1024"><path d="M981.184 160.096C837.568 139.456 678.848 128 512 128S186.432 139.456 42.816 160.096C15.296 267.808 0 386.848 0 512s15.264 244.16 42.816 351.904C186.464 884.544 345.152 896 512 896s325.568-11.456 469.184-32.096C1008.704 756.192 1024 637.152 1024 512s-15.264-244.16-42.816-351.904zM384 704V320l320 192-320 192z"></path></svg>'
  const DOWNLOAD_SVG =
    '<svg viewBox="0 0 1024 1024"><path d="M160 832h704a32 32 0 1 1 0 64H160a32 32 0 1 1 0-64m384-253.696 236.288-236.352 45.248 45.248L508.8 704 192 387.2l45.248-45.248L480 584.704V128h64z"></path></svg>'

  /**
   * 显式工具栏列表：以 wangEditor 默认完整工具栏为底，去掉字体族和全屏，
   * 插入附件菜单（插入表格左侧）和导出分组（行尾，替代全屏的位置）。
   * 升级 wangEditor 时如果默认工具栏变了，这里要对照官方 genDefaultToolbarKeys 更新。
   */
  const DEFAULT_TOOLBAR_KEYS: (string | MenuGroupEntry)[] = [
    'headerSelect',
    'blockquote',
    '|',
    'bold',
    'underline',
    'italic',
    {
      key: 'group-more-style',
      title: '更多样式',
      iconSvg: MORE_STYLE_SVG,
      menuKeys: ['through', 'code', 'sup', 'sub', 'clearStyle'],
    },
    'color',
    'bgColor',
    '|',
    'fontSize',
    'lineHeight',
    '|',
    'bulletedList',
    'numberedList',
    'todo',
    {
      key: 'group-justify',
      title: '对齐',
      iconSvg: JUSTIFY_SVG,
      menuKeys: ['justifyLeft', 'justifyRight', 'justifyCenter', 'justifyJustify'],
    },
    {
      key: 'group-indent',
      title: '缩进',
      iconSvg: INDENT_SVG,
      menuKeys: ['indent', 'delIndent'],
    },
    '|',
    'emotion',
    'insertLink',
    {
      key: 'group-image',
      title: '图片',
      iconSvg: IMAGE_SVG,
      menuKeys: ['insertImage', 'uploadImage'],
    },
    {
      key: 'group-video',
      title: '视频',
      iconSvg: VIDEO_SVG,
      menuKeys: ['insertVideo', 'uploadVideo'],
    },
    'shiyiUploadAttachment',
    'insertTable',
    'codeBlock',
    'divider',
    'undo',
    'redo',
    {
      key: 'shiyiExportGroup',
      title: '导出',
      iconSvg: DOWNLOAD_SVG,
      menuKeys: EXPORT_MENUS.map((menu) => menu.key),
    },
  ]

  // 工具栏配置
  const toolbarConfig = computed((): Partial<IToolbarConfig> => ({
    toolbarKeys: DEFAULT_TOOLBAR_KEYS,
  }))

  /** 站内统一上传：图片 / 视频 / 附件都走 fileApi，并把真实进度同步到编辑器。 */
  async function uploadToServer(
    file: File,
    options: { notify?: boolean } = {},
  ): Promise<FileAsset | null> {
    const { notify = true } = options
    const taskId = ++uploadTaskId
    uploadTasks.value = [
      ...uploadTasks.value,
      { id: taskId, name: file.name, progress: 0, state: 'uploading' },
    ]
    try {
      const asset = await fileApi.upload(file, props.memoId ?? undefined, (progress) => {
        const current = uploadTasks.value.find((task) => task.id === taskId)
        if (current && current.progress !== progress) {
          patchUploadTask(taskId, { progress })
        }
      })
      emit('uploaded', asset)
      finishUploadTask(taskId, 'done')
      if (notify) ElMessage.success('已上传')
      return asset
    } catch {
      finishUploadTask(taskId, 'error')
      if (notify) ElMessage.error('上传失败')
      return null
    }
  }

  // 编辑器配置
  const editorConfig: Partial<IEditorConfig> = {
    placeholder: props.placeholder,
    // 超链接悬浮条：在默认的「编辑 / 取消链接 / 打开链接」前面加一个「复制链接」。
    // 这里只覆盖 link，其余（text / image / table 等）沿用 wangEditor 默认配置。
    hoverbarKeys: {
      link: { menuKeys: ['shiyiCopyLink', 'editLink', 'unLink', 'viewLink'] },
    },
    MENU_CONF: {
      uploadImage: {
        async customUpload(file: File, insertFn: InsertImageFnType) {
          const asset = await uploadToServer(file)
          if (asset) {
            insertFn(asset.url, asset.originalName, '')
          }
        },
      },
      uploadVideo: {
        async customUpload(file: File, insertFn: InsertVideoFnType) {
          const asset = await uploadToServer(file)
          if (asset) {
            insertFn(asset.url, '')
          }
        },
      },
    },
  }

  // 编辑器创建回调
  const onCreateEditor = (editor: IDomEditor) => {
    editorRef.value = editor
    ensureTailParagraph(editor)
  }

  /**
   * wangEditor 自己会给落在文档末尾的 divider / pre / table / video 后面补一个空段落，
   * 但引用块和图片没做——引用是最后一个块时鼠标点不到它的下面，光标出不去。
   * 这里照同样的写法补齐：normalize 时末尾是这类块就在后面插一个空段落。
   */
  const TAIL_BLOCK_TYPES = new Set(['blockquote', 'image'])

  function ensureTailParagraph(editor: IDomEditor) {
    const { normalizeNode } = editor
    editor.normalizeNode = ([node, path]) => {
      if (path.length === 0 && editor.children.length > 0) {
        const last = editor.children[editor.children.length - 1]
        if (SlateElement.isElement(last) && TAIL_BLOCK_TYPES.has(last.type)) {
          SlateTransforms.insertNodes(
            editor,
            { type: 'paragraph', children: [{ text: '' }] },
            { at: [editor.children.length] },
          )
          return
        }
      }
      normalizeNode([node, path])
    }
  }

  function hasDraggedFiles(event: DragEvent) {
    return Array.from(event.dataTransfer?.types ?? []).includes('Files')
  }

  function onDragEnter(event: DragEvent) {
    if (!hasDraggedFiles(event)) return
    event.preventDefault()
    event.stopPropagation()
    dragDepth.value += 1
  }

  function onDragOver(event: DragEvent) {
    if (!hasDraggedFiles(event)) return
    event.preventDefault()
    event.stopPropagation()
    if (event.dataTransfer) event.dataTransfer.dropEffect = 'copy'
  }

  function onDragLeave() {
    if (dragDepth.value === 0) return
    dragDepth.value = Math.max(0, dragDepth.value - 1)
  }

  function onDrop(event: DragEvent) {
    const files = Array.from(event.dataTransfer?.files ?? [])
    dragDepth.value = 0
    if (!files.length) return

    event.preventDefault()
    event.stopPropagation()
    const editor = editorRef.value
    if (!editor) return

    // 在拖放位置建立选区；上传完成后仍回到这里插入，而不是跳到别处。
    let dropSelection: IDomEditor['selection'] = null
    try {
      editor.focus()
      const caretDocument = document as Document & {
        caretRangeFromPoint?: (x: number, y: number) => Range | null
        caretPositionFromPoint?: (x: number, y: number) => { offsetNode: Node; offset: number } | null
      }
      let domRange: Range | null = null
      if (caretDocument.caretRangeFromPoint) {
        domRange = caretDocument.caretRangeFromPoint(event.clientX, event.clientY)
      } else {
        const position = caretDocument.caretPositionFromPoint?.(event.clientX, event.clientY)
        if (position) {
          domRange = document.createRange()
          domRange.setStart(position.offsetNode, position.offset)
          domRange.collapse(true)
        }
      }
      if (domRange) {
        const slateRange = DomEditor.toSlateRange(editor, domRange, {
          exactMatch: false,
          suppressThrow: true,
        })
        if (slateRange) SlateTransforms.select(editor, slateRange)
      }
      dropSelection = editor.selection ? { ...editor.selection } : null
    } catch {
      dropSelection = editor.selection ? { ...editor.selection } : null
    }

    void insertDroppedFiles(files, dropSelection)
  }

  function restoreDropSelection(editor: IDomEditor, selection: IDomEditor['selection']) {
    if (!selection) {
      if (editor.selection == null) editor.restoreSelection()
      return
    }
    try {
      SlateTransforms.select(editor, selection)
    } catch {
      // 上传期间正文结构变化时，原选区可能失效，退回当前光标即可。
    }
  }

  function insertDroppedAsset(editor: IDomEditor, asset: FileAsset) {
    if (asset.kind === 'image') {
      SlateTransforms.insertNodes(editor, {
        type: 'image',
        src: asset.url,
        href: '',
        alt: asset.originalName,
        style: {},
        children: [{ text: '' }],
      } as unknown as Parameters<typeof editor.insertNode>[0])
      return
    }

    editor.insertNode({
      type: ATTACHMENT_ELEMENT_TYPE,
      url: fileDownloadUrl(asset.id),
      name: asset.originalName || '附件',
      children: [{ text: '' }],
    } as unknown as Parameters<typeof editor.insertNode>[0])
    editor.insertNode({ text: ' ' })
  }

  async function insertDroppedFiles(
    files: File[],
    dropSelection: IDomEditor['selection'],
  ) {
    const assets = await Promise.all(
      files.map((file) => uploadToServer(file, { notify: false })),
    )
    const editor = editorRef.value
    if (!editor) return

    const inserted = assets.filter((asset): asset is FileAsset => asset != null)
    if (!inserted.length) {
      ElMessage.error('文件上传失败')
      return
    }

    restoreDropSelection(editor, dropSelection)
    let insertedCount = 0
    for (const asset of inserted) {
      try {
        insertDroppedAsset(editor, asset)
        insertedCount += 1
      } catch {
        // 单个文件落点无效时不阻断其余文件。
      }
    }

    if (insertedCount > 0) {
      ElMessage.success(
        insertedCount === 1
          ? `已将「${inserted[0].originalName}」添加到笔记`
          : `已添加 ${insertedCount} 个文件`,
      )
    }
    if (insertedCount < files.length) {
      ElMessage.warning(`${files.length - insertedCount} 个文件未能插入`)
    }
  }

  function onFilePicked(event: Event) {
    const input = event.target as HTMLInputElement
    const file = input.files?.[0]
    input.value = ''
    if (!file) return
    void (async () => {
      const asset = await uploadToServer(file)
      const editor = editorRef.value
      if (!asset || !editor) return
      try {
        editor.insertNode({
          type: ATTACHMENT_ELEMENT_TYPE,
          url: fileDownloadUrl(asset.id),
          name: asset.originalName || '附件',
          children: [{ text: '' }],
        } as unknown as Parameters<typeof editor.insertNode>[0])
        editor.insertNode({ text: ' ' })
      } catch {
        ElMessage.warning('当前位置不能插入附件')
      }
    })()
  }

  // 暴露编辑器实例和方法
  defineExpose({
    /** 获取编辑器实例 */
    getEditor: () => editorRef.value,
    /** 设置编辑器内容 */
    setHtml: (html: string) => editorRef.value?.setHtml(html),
    /** 获取编辑器内容 */
    getHtml: () => editorRef.value?.getHtml(),
    /** 清空编辑器 */
    clear: () => editorRef.value?.clear(),
    /** 聚焦编辑器 */
    focus: () => editorRef.value?.focus(),
  })

  // 生命周期
  onMounted(() => {
    triggerStore.__shiyiAttachmentTrigger = {
      trigger: () => fileInput.value?.click(),
      disabled: () => uploading.value,
    }
  })

  onBeforeUnmount(() => {
    triggerStore.__shiyiAttachmentTrigger = null
    for (const timer of uploadTimers) clearTimeout(timer)
    uploadTimers.clear()
    const editor = editorRef.value
    if (editor) {
      editor.destroy()
    }
  })
</script>
