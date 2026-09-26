# DFRoot —— SM-S938B（Galaxy S25 Ultra）适配版

> 这是 [diabl0w/DFRoot](https://github.com/diabl0w/DFRoot) 的 fork，专门适配
> **三星 Galaxy S25 Ultra（SM-S938B / pa3q）**，界面与运行输出已全部中文化。
> 漏洞利用原理与绝大部分代码来自上游及下列作者，本 fork 只做了机型适配、启动自检和中文化。

一句话：**开机自动获取 root**。利用 DirtyFrag 漏洞（CVE-2026-43284），
在 bootloader 锁定的三星机型上，每次开机自动把 KernelSU 重新加载起来 ——
不用再手动跑一遍一键 root 工具。

---

## 一、支持机型

| 机型 | 固件 | 内核 | 状态 |
|---|---|---|---|
| SM-S938B（Galaxy S25 Ultra，pa3q） | `BP4A.251205.006.S938BXXS9CZE1` | `6.6.98-android15-8-pe17667d-abogkiS938BXXS9CZE1-4k` | ✅ **实机验证通过** |

上游原本支持的其它 KMI（android12-5.10 ~ android17-6.18）本 fork 没有改动，
理论上仍可用，但未经本 fork 验证。

> **换机型或换固件前请先看第四节"启动自检"**：App 会在动手之前把机型、固件、
> 内核版本、KMI 和 6 条必需路径全部列出来，不满足硬性条件会直接中止，
> 不会写到一半失败。

---

## 二、已验证的事实（2026-09-26，SM-S938B 实机）

在干净开机（未 root）状态下，手动点一次按钮，整条链完整走通：

```
* 机型    : samsung SM-S938B（pa3q）
* 固件    : BP4A.251205.006.S938BXXS9CZE1
* 内核    : 6.6.98-android15-8-pe17667d-abogkiS938BXXS9CZE1-4k
* KMI     : android15-6.6
* 依赖路径: 6 条全部就位
* 适配档位: 精确匹配（BP4A.251205.006.S938BXXS9CZE1）

* 补丁 #1（crash_dump64 ← splicehelper，1312 字节）
* 补丁 #2（libstagefrighthw.so ← dirtyfrag.ko，5664 字节）
* 正在写入 /system/lib64/libc.so 的 shellcode
* 正在写入 /system/lib64/libc++.so 的 shellcode
* 正在触发……
libc++：已抢到互斥锁，正在 fork
libc：正在从内存文件复制 ksud
libc：已隔离 mount 命名空间
libc：bind mount 完成，正在启动 ksud……
成功：ksud 已启动

KernelSU：Detected KMI: android15-6.6
KernelSU：Loading kernelsu.ko for KMI android15-6.6...
KernelSU：kernelsu.ko loaded successfully!
KernelSU：[after load_module] selinux=u:r:ksu:s0
KernelSU：exec /data/adb/modules/.../post-fs-data.sh
KernelSU：exec /data/adb/modules/.../service.sh
KernelSU：init_event: on_boot_completed triggered!
```

KernelSU Manager 显示 **「LKM 工作中 [越狱模式]」**，原有模块全部正常加载。

**开机自动获取 root 也已验证**：打开开关后重启，BootReceiver 会在
`LOCKED_BOOT_COMPLETED` 自动接管，全流程无需人工干预。

---

## 三、编译

需要：**JDK 17+**、**Android SDK（platform 36 / build-tools 36.0.0）**、
**NDK 27.0.12077973**（已在 `app/build.gradle.kts` 里固定）。

```sh
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME=/path/to/android-sdk

# 生成签名（仓库里不含 keystore，已被 .gitignore 排除）
./create-keystore.sh

# 指向你的 SDK
echo "sdk.dir=$ANDROID_HOME" > local.properties

# 出包
./gradlew :app:assembleRelease
# → app/build/outputs/apk/release/dirtyfrag.apk
```

也可以直接用 `./build.sh`，它会构建并复制成根目录下的 `dirtyfrag.apk`。

---

## 四、安装与使用

### 前置条件

1. **装好 KernelSU Manager**（`me.weishu.kernelsu`，v3.3.0）：
   <https://github.com/tiann/KernelSU/releases/tag/v3.3.0>
2. **确认内核仍然存在 DirtyFrag 漏洞**（CVE-2026-43284）。
   上游修复提交是 `f4c50a4034e6`（2026-05-08）。如果手机的安全补丁级别已经
   包含该修复，本工具会失败 —— 这不是 bug，是漏洞没了。

### 第一步：安装

```sh
adb install -r dirtyfrag.apk
```

或者直接把 APK 拷进手机点击安装。

### 第二步：先手动跑一次（推荐）

1. 打开 **DFRoot**；
2. 点底部按钮 **「一键获取 Root（DirtyFrag CVE-2026-43284）」**；
3. 上方文本框会实时打印自检报告和进度。

**成功的标志**是最后出现：

```
成功：ksud 已启动
```

然后打开 KernelSU Manager，应该看到 **「LKM 工作中 [越狱模式]」**。

> 过程中手机会**短暂黑屏/闪一下** —— 那是 ksud 在重启系统框架（soft reboot），
> 属于正常现象，十几秒后会恢复，USB 调试可能会短暂掉线。

### 第三步：打开开机自动获取

确认手动能成功后：

1. 打开 **DFRoot**；
2. 把底部的 **「开机自动获取 Root」** 开关打开；
3. 重启手机验证：开机后不用碰任何东西，root 会自动恢复。

以后每次重启都会自动完成，**不用再手动跑任何一键 root 工具**。

### 开关的说明

| 开关 | 作用 |
|---|---|
| 开机自动获取 Root | 开启后注册开机广播，每次开机自动执行一次。**首次使用建议先手动跑通再打开。** |

> 上游设计了一个防变砖保护：开机自动执行时，如果你在启动过程中**按住音量下键**，
> 会跳过自动 soft reboot，方便在出问题时打断流程。

---

## 五、卸载 / 回退

1. 先关掉 **「开机自动获取 Root」** 开关；
2. 卸载 DFRoot：`adb uninstall df.root`（或在系统设置里卸载）；
3. 重启手机即可回到"无 root"状态。

想继续用别的方式 root（例如 Root-My-Galaxy），装回去重新跑一次就行，
两者互不冲突。

---

## 六、关于 su 与 `--allow-shell`

本 fork **刻意没有**给 ksud 传 `--allow-shell`（上游也没有），因此：

* **普通 App 申请 root 正常**：走 KernelSU 的内核重定向 + 管理器授权弹窗，
  和平时一样在 KernelSU Manager 的「超级用户」里授权即可；
* **`adb shell` 里直接敲 `su` 不会提权**，会返回当前的 shell 身份。

这样做更安全（任何 adb 连接都不能直接拿 root）。
如果你确实需要 adb shell 直接 `su`，在 `app/src/main/jni/libc.S` 里给 ksud 的
argv 加上 `--allow-shell`，重新编译即可 —— 但请自行评估风险。

---

## 七、常见问题

**Q：点了按钮没反应 / 报"自检未通过"？**
看输出框里的自检报告。如果是「依赖路径: 缺失 -> ...」，说明你的固件里少了我
预期存在的文件，把报告贴出来即可判断。

**Q：走到一半失败，提示补丁 #1 / #2 失败？**
大概率是内核里的 DirtyFrag 漏洞已经被修复了。检查「Android 安全补丁级别」，
如果晚于 2026-05 月，基本可以确认。

**Q：root 拿到了，但过一会儿又没了？**
KernelSU 模块是**每次开机重新加载**的（bootloader 锁定，无法改 boot 分区）。
所以一定要打开「开机自动获取 Root」，否则重启后需要手动再点一次。

**Q：会不会变砖？**
不会。所有改动都只作用于**内存里的 page cache**（libc / libc++ / crash_dump64 /
libstagefrighthw.so），重启即全部还原；不改分区、不写 boot。
上游还额外加了把块设备设为只读的保护。

**Q：日志里那些英文（KernelSU / ksud 打的）能中文化吗？**
那是 KernelSU 内核模块和 ksud 自己的输出，属于上游二进制，本 fork 没有改动它们。
App 自己的输出已经全部中文。

---

## 八、本 fork 相对上游改了什么

基于上游 `fc81429`，共 3 个提交：

| 提交 | 内容 |
|---|---|
| `Add SM-S938B (Galaxy S25 Ultra) port` | 新增 `DeviceCheck.java`（启动自检）；`MainActivity`/`BootReceiver` 接入自检；固定 `ndkVersion`；版本号 `1.1-s938b`；新增 `PORTING.md` |
| `Localize the UI and runtime output to Chinese` | 新增 `res/values/strings.xml`；布局与清单改用字符串资源；`DeviceCheck`/`MainActivity`/`BootReceiver` 日志中文化；`jni/exp.c` 的 50 条输出中文化（格式符与参数顺序未变） |
| `Rewrite README in Chinese ...` | 本文件 |

**没有改动**：漏洞利用逻辑、`dirtyfrag-lkm/`、`libc.S` / `libcxx.S` 的 shellcode、
ksud 二进制。

详细适配记录见 [PORTING.md](PORTING.md)。

---

## 九、原理简述

1. **DirtyFrag（CVE-2026-43284）**：Android 内核对 AES-CBC ESP 包做原地解密时，
   会把明文直接写进 `splice()` 引用到的 page cache 页面。
   构造 `IV = AES_ECB_DEC(key, 当前内容) ⊕ 目标内容`，就能改写只读文件的 page cache。
2. 用它把 **splicehelper** 写进 `crash_dump64`，借它的权限读取 vendor 库页面。
3. 把 **dirtyfrag.ko** 写进 `/vendor/lib64/libstagefrighthw.so`（`vendor_file` 标签，可被 modprobe）。
4. 把 shellcode 挂钩到 `libc++.so`（在 init 里执行）和 `libc.so`（在 vendor_modprobe 里执行）。
5. init 执行 `/vendor/bin/modprobe` 时触发 shellcode：加载 dirtyfrag.ko 把 SELinux 设为
   permissive，然后 bind mount ksud 覆盖 `/system/bin/logcat` 并执行，
   由 ksud 完成 KernelSU 模块的 late-load。
6. 收尾：还原 libc / libc++ 的改动，丢弃 crash_dump64 的缓存。

---

## 十、致谢与许可

本 fork 的代码与思路全部来自上游及以下作者：

- 原始 PoC 与大量代码：[lsposed/lspromise](https://github.com/lsposed/lspromise)
- SELinux Permissive 内核模块与相关代码：[polygraphene/DFReroot](https://github.com/polygraphene/DFReroot)
- 非特权 XFRM socket 方法：[combeng6th/DirtyInit](https://github.com/combeng6th/DirtyInit)
- 上游项目：[diabl0w/DFRoot](https://github.com/diabl0w/DFRoot)

> [!WARNING]
> 仅用于你本人拥有或已获明确授权的设备。作者不对任何设备损坏负责。
