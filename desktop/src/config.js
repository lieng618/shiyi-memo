'use strict'

const fs = require('node:fs')
const path = require('node:path')
const { app } = require('electron')

/** 默认服务器地址。用户改 %APPDATA%\shiyi-memo\config.json 即可覆盖，无需重新打包。 */
const DEFAULT_SERVER_URL = 'http://49.232.236.230:8080'

function getConfigPath() {
  return path.join(app.getPath('userData'), 'config.json')
}

/**
 * 首次运行时写出一份默认配置，方便用户直接编辑。
 * 已存在则不覆盖，避免把用户改过的地址冲掉。
 */
function ensureConfigFile() {
  const file = getConfigPath()
  if (fs.existsSync(file)) return
  try {
    fs.mkdirSync(path.dirname(file), { recursive: true })
    fs.writeFileSync(
      file,
      JSON.stringify({ serverUrl: DEFAULT_SERVER_URL, openDevTools: false }, null, 2) + '\n',
      'utf8',
    )
  } catch (err) {
    console.warn('[config] 写入默认配置失败：', err.message)
  }
}

function normalizeUrl(raw) {
  const value = String(raw || '').trim()
  if (!value) return DEFAULT_SERVER_URL
  const withScheme = /^https?:\/\//i.test(value) ? value : `http://${value}`
  return withScheme.replace(/\/+$/, '')
}

function loadConfig() {
  let fileConfig = {}
  try {
    fileConfig = JSON.parse(fs.readFileSync(getConfigPath(), 'utf8'))
  } catch (err) {
    if (err.code !== 'ENOENT') {
      console.warn('[config] 读取失败，回退到默认地址：', err.message)
    }
  }
  return {
    serverUrl: normalizeUrl(fileConfig.serverUrl),
    openDevTools: Boolean(fileConfig.openDevTools),
  }
}

module.exports = { DEFAULT_SERVER_URL, getConfigPath, ensureConfigFile, loadConfig }
