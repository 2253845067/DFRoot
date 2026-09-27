# Release 标题

```
v1.1.1-s938b —— SM-S938B（Galaxy S25 Ultra）适配版
```

Tag 选 **`v1.1.1-s938b`**，Target 选 **`s938b`** 分支。

---

# Release 正文（整段粘贴）

基于上游 [diabl0w/DFRoot](https://github.com/diabl0w/DFRoot) `fc81429` 的 fork，
专门适配 **三星 Galaxy S25 Ultra（SM-S938B / pa3q）**，界面与运行输出已全部中文化。

**开机自动获取 root** —— 利用 DirtyFrag 漏洞（CVE-2026-43284），在 bootloader 锁定的
三星机型上，每次开机自动重新加载 KernelSU，不用再手动跑一遍一键 root 工具。

## 本版新增（v1.1.1）

- **已 root 保护**：启动时检测 `/system/bin/su` 等路径。如果发现别的 root 方案已经在生效，
  会**直接禁用按钮**并显示「检测到已有 root，请先重启手机再运行本应用」。
  避免在已有 root 时误跑 —— 那样 ksud 会跳过加载模块、安装失败，把 `su` 截成 0 字节。
- **界面内置使用步骤**：顶部固定显示三步流程和当前状态（🟢 可以运行 / 🔴 已 root / 🔴 本轮已运行过）。
- 版本号 `1.1.1-s938b`（versionCode 3）。

## 已验证环境

| 项目 | 值 |
| --- | --- |
| 机型 | SM-S938B（Galaxy S25 Ultra，pa3q） |
| 固件 | `BP4A.251205.006.S938BXXS9CZE1` |
| 内核 | `6.6.98-android15-8-pe17667d-abogkiS938BXXS9CZE1-4k` |
| KMI | android15-6.6 |
| 安全补丁 | 2026-05-05 |

## 实机验证结果

- DirtyFrag 原语可用，四个补丁全部成功（crash_dump64 / libstagefrighthw.so / libc.so / libc++.so）
- `dirtyfrag.ko` 加载成功，SELinux 转为 permissive
- 内嵌 android15-6.6 KernelSU 模块加载成功，**无 panic**，域切换到 `u:r:ksu:s0`
- 原有 7 个模块的 post-fs-data / service 脚本全部执行
- KernelSU Manager 显示「**LKM 工作中 [越狱模式]**」
- **开机自动恢复 root 走通**

## 附件

`dirtyfrag-s938b.apk` —— v1.1.1-s938b（versionCode 3），中文界面

## 安装与使用

**前置：先装 KernelSU Manager v3.3.0**
<https://github.com/tiann/KernelSU/releases/tag/v3.3.0>

```
1. 安装本 APK：adb install -r dirtyfrag-s938b.apk
2. 重启手机，确认 KernelSU Manager 显示「未安装」（必须是未 root 状态）
3. 打开 DFRoot，确认顶部状态是「状态：可以运行」
4. 点「一键获取 Root（DirtyFrag CVE-2026-43284）」
5. 输出出现「成功：ksud 已启动」即成功
6. 打开「开机自动获取 Root」开关，以后每次重启自动完成
```

界面顶部就写着这三步，不用记。

详细说明见 [README](https://github.com/2253845067/DFRoot/blob/s938b/README.md)，
适配过程与实测日志见 [PORTING.md](https://github.com/2253845067/DFRoot/blob/s938b/PORTING.md)。

## 注意事项

- 过程中手机会**短暂黑屏/闪一下** —— 那是 ksud 在重启系统框架（soft reboot），属正常现象
- **不要在已经有 root 的状态下运行**（App 会自动拦住；如果绕过了，会导致 `su` 失效，重启即可恢复）
- 如果内核的安全补丁已包含 DirtyFrag 修复（上游提交 `f4c50a4034e6`，2026-05-08），本工具会失败，这不是 bug
- 所有改动只作用于**内存 page cache**，重启即全部还原；不改分区、不写 boot，不会变砖
- 本 fork **刻意不给 ksud 传 `--allow-shell`**：普通 App 申请 root 正常（走管理器授权弹窗），
  但 `adb shell` 里直接 `su` 不会提权。需要的话见 README 第六节

## 相对上游的改动

- 新增**设备启动自检**：机型、固件、内核版本、KMI、6 条必需路径，不满足硬性条件直接中止
- 新增**已 root 保护**：命中时禁用按钮并提示，避免把 `su` 搞坏
- **界面与运行输出中文化**（含 `exp.c` 的 50 条进度/错误信息），界面内置三步使用流程
- 固定 `ndkVersion 27.0.12077973`，版本号 `1.1.1-s938b`
- 中文 README 与 PORTING.md 适配记录

**未改动**：漏洞利用逻辑、`dirtyfrag-lkm/`、shellcode（`libc.S` / `libcxx.S`）、ksud 二进制。

---

漏洞利用原理与绝大部分代码来自上游作者
（[lsposed/lspromise](https://github.com/lsposed/lspromise)、
[polygraphene/DFReroot](https://github.com/polygraphene/DFReroot)、
[combeng6th/DirtyInit](https://github.com/combeng6th/DirtyInit)、
[diabl0w/DFRoot](https://github.com/diabl0w/DFRoot)），详见 README 致谢部分。

> [!WARNING]
> 仅用于你本人拥有或已获明确授权的设备。作者不对任何设备损坏负责。

---

# 仓库 About（右上角齿轮里填）

**Description：**
```
DFRoot 的 SM-S938B（Galaxy S25 Ultra）适配版：利用 DirtyFrag 一键 / 开机自动获取 root，界面已中文化
```

**Topics：**
```
android, root, samsung, galaxy-s25-ultra, sm-s938b, kernelsu, dirtyfrag, cve-2026-43284, s938b, pa3q
```

**Website：** 留空

---

# 建 Release 的步骤

1. 打开 <https://github.com/2253845067/DFRoot/releases/new?tag=v1.1.1-s938b>
2. **Target 选 `s938b` 分支**（不要选 master）
3. 标题、正文按上面填
4. 把 `dirtyfrag-s938b.apk`（14.8 MB）拖进附件框
5. 勾选 **Set as the latest release** → **Publish release**
