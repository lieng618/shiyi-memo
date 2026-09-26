const appDir = (process.env.APP_DIR || '/home/shiyi-memo/start').replace(/\/+$/, '');
const javaBin = process.env.JAVA_BIN || '/usr/bin/java';
const jarName = process.env.APP_JAR || 'shiyi-memo-1.0.0.jar';

module.exports = {
  apps: [
    {
      name: 'shiyi-memo',
      script: javaBin,
      args: [
        '-Xms256m',
        '-Xmx1g',
        '-Dfile.encoding=UTF-8',
        '-Duser.timezone=Asia/Shanghai',
        '-jar',
        `${appDir}/${jarName}`,
        `--spring.config.additional-location=file:${appDir}/`,
      ].join(' '),
      interpreter: 'none',
      cwd: appDir,
      exec_mode: 'fork',
      instances: 1,
      autorestart: true,
      watch: false,
      max_memory_restart: '1500M',
      restart_delay: 3000,
      kill_timeout: 30000,
      time: true,
      merge_logs: true,
      out_file: `${appDir}/logs/pm2-out.log`,
      error_file: `${appDir}/logs/pm2-error.log`,
      env: {
        TZ: 'Asia/Shanghai',
        SPRING_PROFILES_ACTIVE: 'local',
      },
    },
  ],
};
