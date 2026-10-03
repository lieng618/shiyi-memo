// wangEditor 没有对 slate 做 CustomTypes 增强，不补的话
// editor.insertNode({ type: ... }) 这类字面量过不了类型检查。
// 项目里自定义节点的属性都走索引签名（wangEditor 的节点本身就是开放式结构）。
import type { Descendant } from 'slate'

declare module 'slate' {
  interface CustomTypes {
    Element: {
      type: string
      children: Descendant[]
      [key: string]: unknown
    }
    Text: {
      text: string
      [key: string]: unknown
    }
  }
}
