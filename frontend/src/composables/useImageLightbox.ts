import { ref } from 'vue'

/**
 * v-html 富文本里的图片「点开看大图」：给容器内的 img 挂上点击事件，
 * 打开 el-image-viewer 全屏相册（容器内所有图片组成一个可左右翻页的序列）。
 * 在内容渲染完成后调用 attach(root)；重复调用只对新增的 img 生效，
 * 每个视图里配一个 <el-image-viewer> 消费返回的三个状态即可。
 */
export function useImageLightbox() {
  const viewerVisible = ref(false)
  const viewerUrls = ref<string[]>([])
  const viewerIndex = ref(0)

  function attach(root: HTMLElement | null) {
    if (!root) return
    for (const img of Array.from(root.querySelectorAll<HTMLImageElement>('img'))) {
      if (img.dataset.lightboxAttached) continue
      img.dataset.lightboxAttached = '1'
      img.addEventListener('click', (event) => {
        event.preventDefault()
        // 外层卡片可能自己有点击行为（比如站内信列表进详情），这里一并拦掉
        event.stopPropagation()
        const images = Array.from(root.querySelectorAll<HTMLImageElement>('img[src]'))
        viewerUrls.value = images.map((item) => item.currentSrc || item.src)
        viewerIndex.value = Math.max(0, images.indexOf(img))
        viewerVisible.value = true
      })
    }
  }

  return { viewerVisible, viewerUrls, viewerIndex, attach }
}
