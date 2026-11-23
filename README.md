# F50 Monitor

macOS 菜单栏小工具，用来实时看**中兴 F50（MU5120）随身 Wi-Fi**的信号、速率、流量和在线设备。

形态：菜单栏常驻一个条目（`5G` 大字 + 上下两行 `↓下行` / `↑上行`），点击展开面板。

## 功能

| 区域 | 内容 |
| --- | --- |
| 菜单栏 | 网络类型大字 + 下行/上行速率（两行、带色、不随位数跳动） |
| 面板顶部 | 运营商、网络类型、5 格信号、RSRP、接口响应耗时 |
| 面板速率 | 实时上下行速率 + 最近 40 个采样的迷你折线 |
| 面板流量 | 本月剩余 / 套餐总量、进度条、今日用量、校准基准 |
| 面板设备 | 在线设备列表（主机名 + IP）、本机 IP |
| 面板底部 | 打开管理页、校准套餐、菜单栏是否显示速率、退出 |

## 数据从哪来

不需要登录、不需要抓包。设备管理页的 goform 接口免登录可读，只要求请求头带 `Referer`：

```
GET http://192.168.0.1/goform/goform_get_cmd_process
    ?isTest=false&multi_data=1&cmd=<逗号分隔字段>
Referer: http://192.168.0.1/index.html
```

单次 50~100ms，返回 JSON。请求的字段见 `Sources/RouterClient.swift`。

### 速率有两个来源，逐次采样择优

同一台设备会随状态切换数据源，必须两条都兼容：

1. `realtime_rx_thrpt` / `realtime_tx_thrpt`：固件自报瞬时速率，**单位字节/秒**，和厂商 Web 端用的是同一对字段。实测纯下载时它与本机网卡统计 1:1 吻合（比值 0.98）。该字段平时可能是 0，某些状态下（如断网重连后）才有效。
2. `monthly_rx_bytes` / `monthly_tx_bytes` 的差分：固件不吐瞬时值时的兜底。

`realtime_tx_thrpt` 偏高（实测约是真实上行的 2.5 倍，空闲时也会蹦到几百 KB/s，混了链路开销和其它设备流量），所以**用量估算只采信下行**。

### 套餐口径

设备自带的「当月流量」用的是它自己的计数起点，和运营商账单对不上，所以这里用校准口径：

- 已用 = 校准时的剩余量 − 校准后设备计数器增量
- 计数器若被清零（变小），基准自动迁移，不会出现负数
- **计数器冻结**时（实测断网重连后 `monthly_rx/tx_bytes` 连续多次采样完全不变，只有 `monthly_time` 在走），改用「瞬时下行速率 × 采样间隔」估算，记在独立的 `extraBytes` 账上，并设 30 KB/s 死区、扣掉 8 KB/s 空闲基线，避免空闲时凭空累加

所以计数器冻结期间的剩余是**估算值**，偏保守；想对齐就点「校准套餐」按运营商 App 重填一次。

### 固件不支持 / 返回空的字段

`wifi_chip_temp`（温度）、`therm_tj_level`、`pm_sensor_mdm`、`wan_active_band`（频段）、`Z5g_SINR`、`wifi_band` / `Channel`、`station_num` / `connect_num`、`data_volume`（套餐侧未配置）。

另外 `realtime_rx_bytes` / `realtime_tx_bytes` 恒为 0，`realtime_time` 是倒计时，都不能当累计量用。

## 构建

无第三方依赖，只需命令行工具（`xcode-select --install`）：

```bash
./build.sh          # 产出 build/F50Monitor.app（自动匹配当前 CPU 架构）
open build/F50Monitor.app
```

安装到 /Applications 并开机自启：

```bash
./install.sh
./uninstall.sh      # 卸载
```

## 结构

```
F50Monitor/
├── Sources/
│   ├── F50MonitorApp.swift          # 入口（纯 AppKit 生命周期，不声明 SwiftUI 场景）
│   ├── StatusItemController.swift   # 菜单栏项（NSStatusItem + 自绘位图，两行两色）
│   ├── CalibrationWindowController.swift # 套餐校准窗口（NSWindow，启动不自动弹）
│   ├── PanelView.swift              # 面板
│   ├── CalibrationView.swift        # 校准表单
│   ├── MonitorEngine.swift          # 1 秒采样循环、速率双源择优、状态聚合
│   ├── RouterClient.swift           # goform 请求与解析
│   ├── QuotaStore.swift             # 套餐基准 / 每日用量 / 冻结期估算
│   └── Models.swift                 # 数据模型 + 格式化
├── Resources/Info.plist             # LSUIElement=true（不占 Dock）
├── build.sh / install.sh / uninstall.sh
└── README.md
```

## 已知限制

- 卡在菜单栏的 `NSStatusItem` 用的是自绘位图（不是 SwiftUI 自定义 label），因此不受系统把自定义 label 压成「一行纯文字」的影响；代价是外观改动需要改绘制代码。
- 设备固件本身不提供温度、频段、电量，界面上没有这几项。
- 计数器冻结期间的流量是估算，长时间不校准会与实际账单有偏差。
