# DFRoot → SM-S938B (Galaxy S25 Ultra) 适配记录

适配日期：2026-09-26　设备：SM-S938B / BP4A.251205.006.S938BXXS9CZE1 / 内核 6.6.98-android15-8-pe17667d-abogkiS938BXXS9CZE1-4k

---

## 1. 改了什么

基于上游 `diabl0w/DFRoot`（无 release，需自行构建）。

| 文件 | 改动 |
|---|---|
| `app/src/main/java/df/root/DeviceCheck.java` | **新增**。启动前自检：机型/固件/内核 release/KMI + 6 个必需路径是否齐全；不齐就拒绝动手，避免"patch 到一半失败" |
| `app/src/main/java/df/root/MainActivity.java` | 手动运行时先打印自检报告，critical 不通过就中止 |
| `app/src/main/java/df/root/BootReceiver.java` | 开机自动运行时同样先自检 |
| `app/build.gradle.kts` | 显式 `ndkVersion = 27.0.12077973`；`versionCode=2`，`versionName="1.1-s938b"` |
| `app/keystore.jks` | 新生成的签名（DFRoot 不用 sharedUserId，签名可随意） |

**ksud 保持上游原样**（未替换）。原因见第 3 节。

产物：`dirtyfrag-s938b.apk`（14 806 865 B，包名 `df.root`，versionName `1.1-s938b`）

---

## 2. 实机测试结果（2026-09-26 09:41，手动运行）

### ✅ 成功：整条 DirtyFrag 链在你的机器上跑通了

自检输出：

```
* device: samsung SM-S938B (pa3q)
* build : BP4A.251205.006.S938BXXS9CZE1
* kernel: 6.6.98-android15-8-pe17667d-abogkiS938BXXS9CZE1-4k
* kmi   : android15-6.6
* paths: all 6 required paths present
* profile: exact match (BP4A.251205.006.S938BXXS9CZE1)
```

落位标记全部出现（`/dev/df` `dfm0` `dfm2` `dfm3` `dfm6`），即：

1. `crash_dump64` page-cache 改写成功（DirtyFrag 原语可用 → **CVE-2026-43284 在这台机器的内核里仍然存在**）
2. `dirtyfrag-android15-6.6.ko` 写进 `libstagefrighthw.so` 并 `finit_module` **加载成功** → 印证了报告第 0 节的结论（vermagic 版本号确实被忽略）
3. libc `__libc_init` / libc++ ostream sentry 挂钩成功，孤儿进程触发 modprobe 成功
4. **`getenforce` 变成 `Permissive`** —— LKM 生效
5. ksud 以 `uid=0`、`context=u:r:vendor_modprobe:s0` 启动成功

ksud 侧日志证明参数全部被正确解析（DFRoot 自带 ksud 的 CLI 与 shellcode 完全匹配）：

```
ksud::cli: command: LateLoad { magica: None, allow_shell: false, post_magica: false,
  kmi: None, package_name: "me.weishu.kernelsu",
  stage_from: "/data/system/ksud", soft_reboot: false, ro_partitions: true }
ksuinit: KernelSU version: 32601
ksud::late_load: KernelSU already loaded, skip loading ko
ksud::cli: set 15 partition(s) read-only
```

### ⚠️ 两个副作用（都因为"在已经 root 的状态下跑"）

1. **KernelSU 安装步骤失败**：`Failed to extract assets: Operation not permitted`。
   因为模块已加载，ksud 跳过了 `load ko`，于是没有切到 `u:r:ksu:s0` 域，仍在
   `vendor_modprobe` 域里往 `/data/adb` 写资源 → 被 DEFEX/SELinux 拒。
   **干净开机时不会有这个问题**（会先加载模块、切域，再安装）。
2. **`/system/bin/su` 被截成 0 字节**，当前 root 失效。
   正是 `Root-My-Galaxy-Payloads/kernelsu/README.md` 里记录的
   "Late-load could not write a new /data/adb/ksud ... The failed destination remained a zero-byte file"。
   KernelSU 内核模块**仍在加载状态**（`/proc/modules` 里 `kernelsu ... Live`），但 `su` 无法执行。
   **重启即可清除该状态，再用 Root-My-Galaxy 重新拿一次 root 就会恢复。**

> 另外 SELinux 目前是 Permissive、`/dev/df` 挂钩已布防，两者都会在重启后自动清除。

---

## 3. 为什么没有换成 Root-My-Galaxy 的 ksud

一开始打算把 `assets/ksud` 换成 `Root-My-Galaxy-Payloads/kernelsu/ksud-s938b-cze1-kdp`，
但在设备上实测 `ksud late-load --help` 后发现 **CLI 不兼容**：

| 参数 | DFRoot 自带 ksud | RMG 的 `ksud-s938b-cze1-kdp` |
|---|---|---|
| `--package-name` | ✅ | ✅ |
| `--kmi` | ✅ | ✅ |
| `--stage-from` | ✅ | ❌ |
| `--soft-reboot` | ✅ | ❌ |
| `--ro-partitions` | ✅ | ❌ |

DFRoot 的 `libc.S` 固定传 `--stage-from` `--ro-partitions`（开机流程还要 `--soft-reboot`），
换成 RMG 的 ksud 会直接参数解析失败。而 `--soft-reboot` 是**开机自动恢复 root 的关键**：
开机时加载模块后必须软重启框架，KernelSU 的 post-fs-data / service 模块才会生效。

另一个考虑：DFRoot 自带 ksud 报 `ksud 932014a`，与 RMG 为 S938B 构建所依据的
KernelSU v3.3.0 commit `932014ab...` **是同一个**；且 DFRoot 只面向三星锁 BL 设备，
其 README 把 android16-6.12 标为已实机验证 —— 通用 GKI 模块在三星上必崩，所以它的内嵌模块
必然是做过三星 KDP 适配的。

**结论：先用自带 ksud。** 若干净开机测试仍在内核模块加载阶段失败，再回来改 `libc.S` 适配 RMG 的 CLI。

---

## 4. 下一步（待执行）

1. 重启手机（清除 Permissive / `/dev/df` / 0 字节 su）
2. 打开 DFRoot → 先**手动**点一次 "Launch Root"，观察是否走完到 KernelSU 加载完成
3. 成功后再打开 "Start at Boot" 开关，重启验证开机自动恢复

---

## 5. 构建方法（可复现）

本机没有 Java/SDK，已装到 `/Users/yuqi/Documents/dsh/toolchain/`：

```sh
export JAVA_HOME=/Users/yuqi/Documents/dsh/toolchain/jdk21/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME=/Users/yuqi/Documents/dsh/toolchain/sdk
cd /Users/yuqi/Documents/dsh/DFRoot-S938B
./gradlew :app:assembleRelease
# → app/build/outputs/apk/release/dirtyfrag.apk
```

（JDK 21 Temurin + cmdline-tools + platform 36 + build-tools 36.0.0 + NDK 27.0.12077973）


---

## 6. 干净开机后的完整测试（2026-09-26 09:45）

重启后状态干净：`Enforcing`、无 `/dev/df`、KernelSU 未加载、无 su（确认 root 是 per-boot）。

手动点 "Launch Root" 后，**整条链完整走通**：

```
ksud::late_load: [late-load start] pid=16221, uid=0, selinux=u:r:vendor_modprobe:s0
ksud::late_load: Detected KMI: android15-6.6
ksud::late_load: Loading kernelsu.ko for KMI android15-6.6...
ksud::late_load: kernelsu.ko loaded successfully!            ← 关键
ksud::late_load: [after load_module] pid=16221, uid=0, selinux=u:r:ksu:s0   ← 域切换成功
ksud::module: load policy: /data/adb/modules/zygisksu/sepolicy.rule
ksud::module: exec /data/adb/modules/zygisk_lsposed/post-mount.sh
ksud::module: exec /data/adb/service.d/asteriskng_start.sh
ksud::module: exec /data/adb/modules/zygisksu/service.sh
ksud::module: exec /data/adb/modules/zygisk-sui/service.sh
ksud::module: exec /data/adb/modules/m_rcq/service.sh
ksud::module: exec /data/adb/modules/wifi-force-country/service.sh
ksud::module: exec /data/adb/modules/galaxy_root_secure/service.sh
ksud::cli: set 15 partition(s) read-only
```

KernelSU Manager 显示：

```
LKM  工作中 [越狱模式]
版本：32601-2        管理器 v3.3.0 (32601-2)
内核版本 6.6.98-android15-8-pe17667d-abogkiS938BXXS9CZE1-4k
SELinux 状态 强制执行
8 超级用户 / 7 模块
```

**结论：DFRoot 自带的内嵌 android15-6.6 KernelSU 模块在 S938B 上完全可用，没有 panic。
之前担心的"通用 GKI 模块会崩"在这里不成立。**

### 仍未解决：`su` 入口点

* `/system/bin/su` 不存在，`adb shell su` 报 not found；
* 用 `/data/local/tmp/su`（任意以 su 命名的文件会被 KernelSU 重定向到 ksud）能执行，
  但返回 `uid=2000`，未提权；
* 对比：Root-My-Galaxy 的流程会留下一个真实的 `/system/bin/su`（4 892 712 B）。

**推测**：DFRoot 的 `libc.S` 在 exec ksud 之前做了
`unshare(CLONE_NEWNS)` + `mount --make-rprivate /`（为绕过 DEFEX 而把
`/dev/.ksud` bind mount 到 `/system/bin/logcat`），ksud 因此在一个**私有 mount namespace**
里运行，它往 `/system` 写的东西不会出现在全局命名空间；而 `/system` 本身是 EROFS。
Root-My-Galaxy 的流程不做这层 unshare，所以它的 `su` 能落地。

**待验证**：开机流程会多传一个 `--soft-reboot`（手动运行时不传），
有可能在那一步才完成 su 的安装。所以下一步值得直接测开机自动恢复。



---

## 7. 开机自动恢复测试（2026-09-26 09:52，成功）

打开 DFRoot 的 **Start at Boot** 开关（注册 `LOCKED_BOOT_COMPLETED` + `BOOT_COMPLETED`），重启后：

```
09:52:52.986 I/dfroot: boot: android.intent.action.LOCKED_BOOT_COMPLETED
09:52:52.987 I/dfroot: preflight (boot)   → 6 条路径齐全、profile exact match
09:52:53.064 I/dfroot: * ko android15-6.6 (5656 bytes)
09:52:53.073 I/dfroot: patched 1312 bytes to /apex/.../crash_dump64+0x0
09:52:53.620 I/dfroot: patched 5664 bytes to /vendor/lib64/libstagefrighthw.so+0x0
09:52:53.627 I/dfroot: patched 1728 bytes to /system/lib64/libc.so+0xe87c0
09:52:53.635 I/dfroot: patched 352 bytes to /system/lib64/libc++.so+0x105138
09:52:55.815 I/dfroot: libc++: mutex acquired, forking
09:52:55.886 I/dfroot: success: ksud launched
...
09:52:57.220 I/KernelSU: ksud::module: exec /data/adb/modules/zygisksu/post-fs-data.sh
09:52:57.375 I/KernelSU: ksud::module: exec /data/adb/modules/zygisk_lsposed/post-fs-data.sh
09:52:57.431 I/KernelSU: ksud::module: exec /data/adb/service.d/asteriskng_start.sh
09:52:57.455 I/KernelSU: ksud::module: exec /data/adb/modules/zygisksu/service.sh
09:52:57.485 I/KernelSU: ksud::module: exec /data/adb/modules/zygisk-sui/service.sh
09:52:57.497 I/KernelSU: ksud::module: exec /data/adb/modules/m_rcq/service.sh
09:52:57.512 I/KernelSU: ksud::module: exec /data/adb/modules/wifi-force-country/service.sh
09:52:57.518 I/KernelSU: ksud::module: exec /data/adb/modules/galaxy_root_secure/service.sh
09:52:57.527 I/KernelSU: ksud::init_event: waiting for boot complete
09:53:49.660 I/KernelSU: ksud::init_event: on_boot_completed triggered!
09:53:51.024 I/dfroot: boot: already hooked, skipping
```

**结论：开机自动恢复 root 完全走通** —— 漏洞 → dirtyfrag.ko → SELinux permissive →
ksud late-load → KernelSU 模块加载 → 你原有的 7 个模块的 post-fs-data / service 全部执行。
中间设备会短暂掉线（ksud 的 `--soft-reboot` 重启框架），随后自行恢复。

### 关于 `su`：不是坏了，是 DFRoot 默认不给 adb shell 提权

* DFRoot 调 ksud 时 **没有传 `--allow-shell`**（日志里 `allow_shell: false`），
  所以 adb shell（uid 2000）不会被授权 root，`su` 即使能执行也只返回 uid=2000；
* `/system/bin/su` 这个文件本身也不存在（KernelSU 靠内核 hook 把名为 `su` 的
  execve 重定向到 ksud，并不需要真实文件；libsu 类的 root 应用通过这个重定向
  + 管理器授权拿 root）；
* KernelSU 正在工作：`cat /proc/modules` 返回 **Permission denied** —— 这正是
  `selinux_hide=1` 在隐藏自己；`/dev/df*` 标记同样被隐藏。

**如果你希望 adb shell 也能直接 `su`**（就像 Root-My-Galaxy 那套一样），
只要在 DFRoot 的 `libc.S` 里给 ksud 的 argv 加上 `--allow-shell` 即可（改一行、重编）。
代价是任何 adb shell 都能直接拿 root，安全性下降，所以上游默认不开。

---

## 8. 最终结论

| 项目 | 状态 |
|---|---|
| DirtyFrag 漏洞在 S938B 上可用 | ✅ 实测通过 |
| dirtyfrag.ko 加载 / SELinux permissive | ✅ 实测通过 |
| DFRoot 内嵌 android15-6.6 KernelSU 模块 | ✅ 实测通过（无 panic） |
| 开机自动恢复 root | ✅ 实测通过 |
| 原有 7 个模块 post-fs-data / service | ✅ 全部执行 |
| adb shell 直接 `su` | ⚠️ 默认不开（`allow_shell=false`），需要就加 `--allow-shell` |

---

## 9. 2026-10-25：Android 17 + KernelSU 32687 升级

### 背景

系统 OTA 到 `CP2A.260605.016.S938BXXSDDZJ1`（Android 17，内核
`6.6.127-android15-8-p87b1ce1-abogkiS938BXXSDDZJ1-4k`，安全补丁 2026-10-05），
DirtyFrag 仍然可用。KernelSU 管理器提示更新到官方 v3.3.1（32680），但：

* 官方 v3.3.1 把 UAPI 从 2 升到 5，当前内核模块 32601 / UAPI 2 会一直报「需要更新内核」；
* 官方 v3.3.0 / v3.3.1 的 ksud **没有** DFRoot 使用的
  `--stage-from` / `--ro-partitions` / `--soft-reboot`，不能直接替换；
* 最终选择 diabl0w/KernelSU 的 Samsung 构建 **samsung-v2.2（32687 / UAPI 5）**，
  并从该源码重编 ksud，补回 DFRoot 需要的三个参数。

### 对 ksud 源码的改动

基于 `diabl0w/KernelSU` `samsung-v2.2`：

1. `userspace/ksud/src/cli.rs`
   * `LateLoad` 增加 `stage_from: Option<PathBuf>`、`ro_partitions: bool`、
     `soft_reboot: bool`；
   * 分发时把 `soft_reboot` / `stage_from` 传给 `late_load::run`，之后调用
     `utils::set_partitions_ro()`，并在需要时
     `utils::create_daemon(false)` + `soft_reboot::soft_reboot()`。
2. `userspace/ksud/src/late_load.rs`
   * `stage_from` 存在时，先 `utils::switch_mnt_ns(1)` 切到 init 的 mount
     namespace，并从 init / zygote 克隆环境变量；否则模块的 mount 不会生效；
   * `utils::install_from(stage_from.as_deref(), None, None)` 从
     `/data/system/ksud` 安装，而不是在切换命名空间后读 `/proc/self/exe`；
   * `soft_reboot` 为 true 时，安装完 ksud 就返回，跳过模块 late-load 脚本
     （软重启会重新跑一遍）。
3. `userspace/ksud/src/utils.rs`
   * 新增 `install_from(source, ...)`，原 `install()` 继续走 `/proc/self/exe`；
   * 新增 `set_partitions_ro()`：对 `/dev/block/by-name` 下的
     boot / dtbo / init_boot / vendor_boot / super / optics / prism / vbmeta
     （含 `_a` / `_b`）做 `BLKROSET`。
4. `userspace/ksud/build.rs`
   * 支持 `KSU_VERSION_CODE` / `KSU_VERSION_NAME` 覆盖；源码 tarball 没有 git
     历史时也能固定为 32687 / samsung-v2.2。
5. `userspace/ksud/bin/aarch64/`
   * 放入 samsung-v2.2 release 的 8 个 `*_kernelsu.ko` 和 `ksuinit`，
     由 rust-embed 打进 ksud。

### 构建命令（macOS，NDK 27）

```sh
export PATH="$HOME/.cargo/bin:$PATH"
export ANDROID_NDK_HOME=/path/to/android-sdk/ndk/27.0.12077973
LLVM="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64"
CLANG="$LLVM/bin/aarch64-linux-android26-clang"
export CC_aarch64_linux_android="$CLANG"
export CXX_aarch64_linux_android="$CLANG++"
export AR_aarch64_linux_android="$LLVM/bin/llvm-ar"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$CLANG"
export BINDGEN_EXTRA_CLANG_ARGS_aarch64_linux_android="--sysroot=$LLVM/sysroot -I$LLVM/sysroot/usr/include/aarch64-linux-android"
export KSU_LKM_BOOTSTRAP_CC="$LLVM/bin/clang"
export KSU_VERSION_CODE=32687
export KSU_VERSION_NAME=samsung-v2.2
cd KernelSU
cargo build --target aarch64-linux-android --release --manifest-path userspace/ksud/Cargo.toml
# → target/aarch64-linux-android/release/ksud
```

把产物复制到 DFRoot 的 `app/src/main/assets/ksud`，再：

```sh
./gradlew :app:assembleRelease
# → app/build/outputs/apk/release/dirtyfrag.apk
```

### 实测结果（2026-10-25）

手动运行：

```
ksud::cli: command: LateLoad { magica: None, allow_shell: false, post_magica: false,
  kmi: None, package_name: "me.weishu.kernelsu",
  stage_from: Some("/data/system/ksud"), ro_partitions: true, soft_reboot: false }
ksud::late_load: kernelsu.ko loaded successfully!
ksud::late_load: [after load_module] selinux=u:r:ksu:s0
ksud::cli: set 15 partition(s) read-only
```

开机自动：

```
dfroot: boot: exploit rc=0
ksud::cli: command: LateLoad { ... stage_from: Some("/data/system/ksud"),
  ro_partitions: true, soft_reboot: true }
ksud::cli: Performing soft-reboot...
ksud::soft_reboot: emulating soft_reboot!
ksud::soft_reboot: waiting for boot complete
dfroot: boot: already hooked, skipping
```

管理器：

```
LKM 工作中 [越狱模式]
版本：32687-5
自定义
管理器版本：samsung-v2.2 (32687-5)
内核版本：6.6.127-android15-8-p87b1ce1-abogkiS938BXXSDDZJ1-4k
```

> root 生效后 `getenforce` / `cat /proc/modules` 返回 Permission denied 是
> `selinux_hide=1` 的正常行为，不代表模块没加载。

### 回滚

1. 重启（清掉当前 root）；
2. `adb install -r -d DFRoot-1.1.1-s938b.apk`；
3. 卸载 Samsung 管理器，装回官方 v3.3.0；
4. 再重启，旧链（32601 / UAPI 2）恢复。

回滚包在 `dist/rollback/`：`DFRoot-1.1.1-s938b.apk`、
`KernelSU_v3.3.0_32601.apk`。

---

## 10. 2026-10-25：UI 按 Samsung 管理器风格重构（1.3.0）

参考 `diabl0w/KernelSU` `samsung-v2.2` 的 Material 3 页面结构（Large top app bar、
StatusCard、SegmentedListItem、底部导航），在 XML 侧重做了 DFRoot 主界面：

* **主题**：`Theme.Material3.DayNight.NoActionBar` + `App` 中
  `DynamicColors.applyToActivitiesIfAvailable()`，使用系统 Monet 取色；
  edge-to-edge 由 `WindowCompat.setDecorFitsSystemWindows(false)` + insets 处理，
  状态栏 / 导航栏图标随深浅色切换。
* **状态卡**：`MaterialCardView` 28dp 圆角；就绪 / 运行中用
  `secondaryContainer`，阻止用 `errorContainer`；标题 + Chip 徽章（就绪 / 已阻止 /
  运行中），详细原因在标题下方通栏显示。
* **设备信息卡**：机型 / 固件 / 内核 / KMI 四行，图标 + 主标签 + 次值，
  行间用 `colorOutlineVariant` 分隔。
* **运行日志**：深色 18dp 圆角控制台（160dp 高），空状态显示「等待运行…」，
  运行或清空时切换占位；日志仍可选中、内部滚动。
* **底部操作卡**：MaterialSwitch + 全宽 MaterialButton（28dp 圆角），
  开关关闭 / 运行时按钮自动禁用。
* 版本 `versionCode 5` / `versionName 1.3.0-s938b`；内核与 ksud 不变。

浅色和深色实机截图见 `dist/screenshots/`。

