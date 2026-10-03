<!--
  富文本编辑器，基于 wangEditor 5，实现搬运自 art-design-pro 的 ArtWangEditor 组件。
  插件地址：https://www.wangeditor.com/
  与 art 版的差异：上传走站内文件接口（fileApi），并加了附件按钮和工具栏末尾插槽。
-->
<template>
  <div class="rich-editor">
    <div class="rich-editor-toolbar-row">
      <Toolbar
        class="rich-editor-toolbar"
        :editor="editorRef"
        :mode="mode"
        :defaultConfig="toolbarConfig"
      />
      <!-- 工具栏末尾留给页面注入的低频操作（如导出），样式随工具栏按钮 -->
      <div class="rich-editor-extras">
        <slot name="toolbar-end" />
      </div>
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
  import { Boot, SlateElement, SlateTransforms } from '@wangeditor/editor'
  import type { IButtonMenu, IDomEditor, IEditorConfig, IToolbarConfig } from '@wangeditor/editor'
  import { fileApi } from '@/api'
  import type { FileAsset } from '@/types'

  type InsertImageFnType = (url: string, alt: string, href: string) => void
  type InsertVideoFnType = (url: string, poster: string) => void

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

  try {
    // vite HMR 会重复执行模块，重复注册直接忽略
    Boot.registerMenu({ key: 'shiyiUploadAttachment', factory: () => new UploadAttachmentMenu() })
  } catch {
    /* 已注册过 */
  }

  // Props 定义
  interface Props {
    /** 编辑器高度，100% 表示撑满父容器 */
    height?: string
    /** 自定义工具栏配置 */
    toolbarKeys?: string[]
    /** 插入新工具到指定位置 */
    insertKeys?: { index: number; keys: string[] }
    /** 排除的工具栏项 */
    excludeKeys?: string[]
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
    placeholder: '随手写点什么，支持标题、列表、表格、图片…',
    excludeKeys: () => ['fontFamily', 'fullScreen'],
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

  // 工具栏配置
  const toolbarConfig = computed((): Partial<IToolbarConfig> => {
    const config: Partial<IToolbarConfig> = {}

    // 完全自定义工具栏
    if (props.toolbarKeys && props.toolbarKeys.length > 0) {
      config.toolbarKeys = props.toolbarKeys
    }

    // 插入新工具：默认把附件按钮插到「插入表格」左侧（wangEditor 默认完整工具栏里的索引）
    config.insertKeys = props.insertKeys ?? { index: 24, keys: ['shiyiUploadAttachment'] }

    // 排除工具
    if (props.excludeKeys && props.excludeKeys.length > 0) {
      config.excludeKeys = props.excludeKeys
    }

    return config
  })

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
