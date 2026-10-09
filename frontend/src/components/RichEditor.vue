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
  <div class="rich-editor">
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
    </div>
  </div>
</template>

<script setup lang="ts">
  import '@wangeditor/editor/dist/css/style.css'
  import './rich-editor.scss'
  import { computed, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
  import { ElMessage } from 'element-plus'
  import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
  import { Boot, DomEditor, SlateElement, SlateTransforms } from '@wangeditor/editor'
  import type { IButtonMenu, IDomEditor, IEditorConfig, IToolbarConfig } from '@wangeditor/editor'
  import { fileApi } from '@/api'
  import { copyText } from '@/utils/clipboard'
  import type { FileAsset } from '@/types'

  type InsertImageFnType = (url: string, alt: string, href: string) => void
  type InsertVideoFnType = (url: string, poster: string) => void
  type ExportFormat = 'md' | 'html' | 'doc' | 'pdf' | 'png'

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
  const uploading = ref(false)

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

  /** 站内统一上传：图片 / 视频 / 附件都走 fileApi，落库时关联当前笔记 */
  async function uploadToServer(file: File): Promise<FileAsset | null> {
    uploading.value = true
    try {
      const asset = await fileApi.upload(file, props.memoId ?? undefined)
      emit('uploaded', asset)
      ElMessage.success('已上传')
      return asset
    } catch {
      ElMessage.error('上传失败')
      return null
    } finally {
      uploading.value = false
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
          type: 'link',
          url: asset.url,
          target: '_blank',
          children: [{ text: asset.originalName || '附件' }],
        })
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
    const editor = editorRef.value
    if (editor) {
      editor.destroy()
    }
  })
</script>
