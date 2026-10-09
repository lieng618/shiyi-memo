-- 欢迎信换成「三步上手 + 截图」的草稿版本。
-- 截图是前端构建产物：frontend/public/welcome/step-*.jpg → 站点根路径 /welcome/step-*.jpg。
--
-- 只在内容还是最早那份草稿时才替换（后台自己改过文案的环境不动它）。
-- 想强制换成这一版，手动执行下面这条去掉 WHERE 那一行的 UPDATE 即可。
UPDATE welcome_letter
SET title        = '欢迎使用拾忆',
    content_html = '<p><strong>你好 {nickname}，欢迎来到拾忆 👋</strong></p><p>这里是一个安静的笔记本：随手记下想法、照片和文件，需要的时候再翻出来。三步就能上手。</p><h3>1. 先写一条</h3><p>点右上角「新建笔记」，正文能排版、插图、传文件。</p><p><img src="/welcome/step-1.jpg" alt="写一条笔记"></p><h3>2. 打上标签</h3><p>写完在笔记上方加标签，比如「工作」「读书」；以后点列表顶部的标签就能筛出来。</p><p><img src="/welcome/step-2.jpg" alt="按标签筛选"></p><h3>3. 分享给别人</h3><p>点笔记右侧的分享按钮生成一条链接，无需登录也能预览。</p><p><img src="/welcome/step-3.jpg" alt="分享出去的页面"></p><p>—— 拾忆</p>'
WHERE id = 1
  AND content_html LIKE '%这里有三件可以马上做的事%';
