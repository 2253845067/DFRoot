# DFRoot 1.3.0-s938b（SM-S938B / Galaxy S25 Ultra）

基于 [diabl0w/DFRoot](https://github.com/diabl0w/DFRoot) 的 fork，适配
**三星 Galaxy S25 Ultra（SM-S938B / pa3q）**，界面与运行输出中文化。

利用 DirtyFrag（CVE-2026-43284），在 bootloader 锁定的三星机型上每次开机自动
重新加载 KernelSU —— 不用再手动跑一遍一键 root 工具。

## 本次更新（1.3.0，2026-10-25）

- UI 参考 [DirtyFragX](https://github.com/dugongzi/DirtyFragX) 重做为卡片式仪表盘：
  大标题、状态卡（就绪 / 已阻止 / 运行中）、SU 管理器卡、设备信息卡、
  深色日志控制台，以及底部「首页 / 设置」导航；
- 青绿配色，浅色 / 深色均适配；
- 设备信息卡支持一键复制；进入页面显示机型 / 系统 / 固件 / 内核 / KMI / 架构 / 指纹；
- `versionCode 5` / `versionName 1.3.0-s938b`。内核部分不变：仍是
  KernelSU 32687 / UAPI 5。

## 1.2.0 内核升级（2026-10-25）

| 项目 | 旧（1.1.1） | 新（1.2.0） |
|---|---|---|
| 固件 | BP4A.251205.006.S938BXXS9CZE1（Android 15） | CP2A.260605.016.S938BXXSDDZJ1（Android 17） |
| 内核 | 6.6.98-android15-8 | 6.6.127-android15-8 |
| 安全补丁 | 2026-05-05 | 2026-10-05 |
| KernelSU | 32601 / UAPI 2 | 32687 / UAPI 5 |
| 管理器 | 官方 v3.3.0 | diabl0w Samsung samsung-v2.2 |
| DFRoot | 1.1.1（versionCode 3） | 1.2.0（versionCode 4） |

- `assets/ksud` 换成基于 diabl0w/KernelSU `samsung-v2.2` 重编的 32687；
- 把 DFRoot 需要的 `--stage-from` / `--ro-partitions` / `--soft-reboot` 重新补回；
- `--stage-from` 会先切到 init 的 mount namespace，再从 `/data/system/ksud` 安装；
- 自检的已验证固件更新为 DDZJ1 / 6.6.127；
- 管理器换成 Samsung 版 samsung-v2.2（32687，UAPI 5）。

## 实机验证（2026-10-25）

- **手动运行**：四个补丁全部成功；ksud 32687 加载 `kernelsu.ko`（android15-6.6），
  域切换到 `u:r:ksu:s0`；15 个分区设为只读；7 个模块的脚本全部执行。
- 管理器显示「**LKM 工作中 [越狱模式]**」「版本：32687-5」「自定义」。
- **开机自动**：打开开关后重启，BootReceiver 自动执行；ksud 带 `--soft-reboot`
  完成框架软重启；第二次 `BOOT_COMPLETED` 显示 `already hooked, skipping`。
- root 生效后 `selinux_hide` 会让 `getenforce` / `cat /proc/modules` 返回
  Permission denied，这是正常的隐藏行为。

## 安装

1. 如果装的是官方 KernelSU 管理器，先卸载；安装 Samsung 版：
   <https://github.com/diabl0w/KernelSU/releases/tag/samsung-v2.2>
2. 安装 DFRoot：
   ```sh
   adb install -r DFRoot-1.3.0-s938b.apk
   ```
3. 重启，确认管理器显示「未安装」（未 root 状态）。
4. 打开 DFRoot，点「一键获取 Root」，等待输出「成功：ksud 已启动」。
5. 打开「开机自动获取 Root」，以后每次重启自动完成。

> 从官方管理器换到 Samsung 版必须卸载重装（签名不同）；`/data/adb` 里的模块和
> 授权记录不受影响，管理器里的设置会重置。

## 注意

- `adb shell` 里直接 `su` 仍然不会提权（`allow_shell=false`）。
- 所有改动只作用于内存 page cache，不改分区、不写 boot；重启即还原。
- 如果内核以后合入 DirtyFrag 修复，本工具会失败 —— 这不是 bug。
- 回滚：装回 1.1.1 APK（`adb install -r -d`）并换回官方 v3.3.0 管理器，然后重启。

## 相对上游的改动

- 新增**设备启动自检**：机型、固件、内核版本、KMI、6 条必需路径；
- 新增**已 root 保护**：命中时禁用按钮并提示；
- **界面与运行输出中文化**（含 `exp.c` 的 50 条进度/错误信息）；
- 固定 `ndkVersion 27.0.12077973`；
- **1.2.0**：内置 KernelSU 升级到 32687 / UAPI 5（详见 [PORTING.md](PORTING.md)）。

**未改动**：漏洞利用逻辑、`dirtyfrag-lkm/`、shellcode（`libc.S` / `libcxx.S`）。

---

漏洞利用原理与绝大部分代码来自上游作者
（[lsposed/lspromise](https://github.com/lsposed/lspromise)、
[polygraphene/DFReroot](https://github.com/polygraphene/DFReroot)、
[combeng6th/DirtyInit](https://github.com/combeng6th/DirtyInit)、
[diabl0w/DFRoot](https://github.com/diabl0w/DFRoot)），详见 README 致谢部分。

> [!WARNING]
> 仅用于你本人拥有或已获明确授权的设备。作者不对任何设备损坏负责。
