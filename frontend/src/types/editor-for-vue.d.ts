// @wangeditor/editor-for-vue 的 package.json exports 漏了 types 条目，
// TS 解析不到它自带的声明，这里手动补一份组件声明（本文件必须保持
// 无顶层 import / export 的脚本形态，才会被当成环境模块声明）。
declare module '@wangeditor/editor-for-vue' {
  import type { DefineComponent } from 'vue'
  import type { IDomEditor, IEditorConfig, IToolbarConfig, SlateDescendant } from '@wangeditor/editor'

  export interface EditorProps {
    /** 编辑器模式 */
    mode?: 'default' | 'simple'
    defaultContent?: SlateDescendant[]
    defaultHtml?: string
    defaultConfig?: Partial<IEditorConfig>
    modelValue?: string
  }

  export interface ToolbarProps {
    editor?: IDomEditor
    mode?: 'default' | 'simple'
    defaultConfig?: Partial<IToolbarConfig>
  }

  export const Editor: DefineComponent<
    EditorProps,
    {},
    any,
    {},
    {},
    any,
    any,
    {
      onCreated: (editor: IDomEditor) => void
      onChange: (editor: IDomEditor) => void
      onDestroyed: (editor: IDomEditor) => void
      'update:modelValue': (html: string) => void
    }
  >

  export const Toolbar: DefineComponent<ToolbarProps, {}, any>
}
