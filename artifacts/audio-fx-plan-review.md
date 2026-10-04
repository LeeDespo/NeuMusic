# 音效方案独立审阅

审阅对象：`.zcode/audio-fx-research/audio-fx-plan.md`（R2，107012 bytes）与当前 `PlayerHost`、`EqualizerHost`、`DynamicsFxHost`、`SmartEq`、`VizHost`、`VizProcessor`、`PlaybackService`、`EqualizerScreen`。2026-10-04。只读审阅，未修改应用代码。

## 结论与建议范围

P0 的问题识别基本成立：DVC 的截止频率/段级启停、音效实例销毁与重挂、智能调音误报、整页不支持门控、滑杆逐事件落盘均有现有代码证据。建议先实现这些修复和三分区音效页；自研 EQ、导入导出、AutoEq 为后续独立里程碑，先修正下面的 DSP 和数据模型问题。计划中符号存在性核对很充分，但不能据此推导处理数学、设备兼容性和验收的正确性。

## 必须修正的阻断问题

### P0：通知服务退出不能销毁播放器

计划 §5.1-9 建议把 `PlayerHost.release()` 供 `PlaybackService` 收摊使用。当前 `PlaybackService.kt:24-25` 明确只拥有通知与前台身份；`PlayerHost` 在 `onIsPlayingChanged(false)` 调用 `PlaybackService.stop`，该服务的 `ACTION_STOP` 路径调用 `pause()`、`stopSelf()`。把播放器 release 放入服务 `onDestroy` 会在每次暂停时销毁播放器，破坏从当前位置恢复。

建议：保留通知服务与播放器的现有所有权边界；新增明确终止播放会话的 teardown 方法，释放 media session、effects、player、消息/协程后再清字段。服务普通暂停收摊不调用它。音效 detach 绑定实际 session 变化，不绑定页面退出、曲目变化或通知消失。构造部分成功后失败也要释放局部实例，避免 Equalizer 已赋值、后续初始化失败导致重复 attach 被幂等返回挡住。

### P1：16bit EQ 输出后再限幅已经来不及

计划 §5.5 要求 EQ 只接受 16bit，链序为 `EqProcessor → LimiterProcessor → VizProcessor`；同时 §9 要在 limiter 前观察 `maxAbs > 1`。若 EQ 仍输出 16bit，则处理结果在写出时必须被裁剪或发生溢出；下一处理器无法观察未削波浮点结果，也无法恢复已丢失峰值。自动 preamp 降低频域增益，不构成所有时域瞬态的安全证明。

建议：初版将 EQ、前级和 linked limiter 放在同一处理器里，内部浮点计算，最后统一转换成 PCM16；或明确提供 float 中间格式与末端 PCM16 转换级，但不能打开会绕过用户链的 sink float 输出开关。离线测试要覆盖高幅瞬态、交替符号样本、多频叠加、左右声道峰值不同、NaN 防御与输出边界。单独的平台 DP limiter 同样不能保护之前已经裁剪的 PCM。

### P1：响应采样不是滤波器增益拟合

§7.2 把目标合成响应在各段中心处的 dB 值直接作为新 PK 滤波器 gain，并宣称保住形状。这不成立：新滤波器相互叠加，在中心处的总响应也包括其它滤波器贡献，直接采样再级联通常会二次叠加。反复跨引擎转换还会漂移。平台公开 API 更没有 Q，不能保证 AOSP LVM 的 Q=0.96 就是当前设备实现。

建议：保留原始预设作为唯一真值；跨引擎只生成派生近似，不回写源值；若采用采样近似，明确称为近似并测误差。需要真正保形时用约束优化拟合，且必须有设备支持的段频与范围。平台曲线视图只能标为估计曲线。

### P1：固定五段平台模型与预设 v2 不可移植

§7.2/7.4 硬写 60/230/910/3600/14000 Hz 和 Q=0.96，而当前 `EqualizerHost.bandFreqs`/`numberOfBands` 正确向设备读取。§7.1 的 v2 只存 gains，没有频点；两设备都是五段、频点却不同的预设会被误判为兼容。参数与图形数组并存但没有真值来源，也没定义用户拖动后 filters 是否重算/失效，导出的曲线可能与听到的曲线不同。

建议 v2 至少增加稳定 `id`、`kind`、`frequenciesHz`、来源布局/设备描述；genreMap 和 selected 保存 id，显示名单独存。PEQ 模式保存 filters 为真值；图形模式保存频点+gain 为真值；变换结果单独缓存。读取 v1 时只能用已有设备快照推断，不能假装知道旧频点。当前设备段频与范围在 attach 时查询并缓存。[Equalizer 官方 API](https://developer.android.com/reference/android/media/audiofx/Equalizer) 提供设备段数、中心频率和范围查询，没有固定五段契约。

### P1：精确引擎仍被平台能力挡住

§6.2 表格将整个均衡区用 `EqualizerHost.available` 门控，§5.6 又在 PRECISE 模式 release 平台实例。若设备根本不支持平台 EQ，自研 EQ 应仍可用；若平台已 detach，mounted=false 也不能把自研控制禁掉。

建议门控以选中引擎的能力为依据；平台 unsupported、暂未挂载、无控制权、初始化失败应分开。一次构造异常可能是会话或控制权问题，不足以证明设备永远不支持。

## 应完善的实现与产品约束

1. **P1，避免每次拖动都 drain/flush DSP 管线。** §5.5 将增益变化与 isActive 变化都走同值 `sink.setSkipSilenceEnabled`。Media3 1.11.1 的实现确实会强制 drain，因此这个技巧存在；但普通 gain 更新无需重新选择 active processors。每事件 drain 会增加延迟、反复清空滤波器/变速器状态并带来听感跳变风险。建议 processor 持续 active 时，在播放线程更新目标参数并做短平滑；仅启停/格式变化才刷新链，且合并消息。代码出处：[固定 1.11.1 DefaultAudioSink](https://raw.githubusercontent.com/androidx/media/1.11.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/DefaultAudioSink.java)，`setSkipSilenceEnabled` 1307-1312。
2. **P1，补采样率与 Nyquist 约束。** 固定 16kHz 滤波段、20kHz headroom 扫描不能直接用于 22.05/24/32kHz 等本地音源。滤波器 Fc 必须低于 Nyquist；扫描上界取采样率相关值。补每声道独立滤波器状态、seek/flush/reset 的状态清理、系数更新平滑、单声道/多声道行为，避免左右串扰、旧曲尾音和参数突变。
3. **P0，滑杆提交需要并发变更规则。** 本地拖动 pending 值与外部选预设、智能调音、会话重挂、页面换区可能同时发生。必须有编辑版本/目标 preset id：旧手势结束不能把 pending 写入刚选中的另一预设；cancel 是提交当前值还是回滚应明确。单个 `pending: Float?` 不足以表达频段数组和 speed/pitch 两个值。
4. **P1，强度不可调不等于没有 BassBoost。** §5.9 以 `getStrengthSupported()==false` 隐藏整块；官方含义是该实现可能只有固定强度，仍可开关。建议可调时显示滑杆，固定强度时显示开关，并保留不足说明。[BassBoost 官方 API](https://developer.android.com/reference/android/media/audiofx/BassBoost)。
5. **P1，明确总开关范围与限幅顺序。** 当前 eqEnabled 控制 EQ+低音，但 DVC/平衡独立。新增总开关不能显示关了却仍有响度/动态处理；也不能为了随平台 EQ 开关把独立功能全关掉。LoudnessEnhancer 是增益增强，不是曲目响度归一化；建议称“响度增强”。平台 effect 执行顺序未被方案证明，不应承诺 DP limiter 能兜住独立 LoudnessEnhancer 的所有峰值；响度增强默认关。
6. **P1，可视化归零还需要停止发布。** 单次 zero 后，管线可能继续处理预缓冲/seek 数据，FFT 回调也可能还在队列内，导致暂停后马上非零。播放态/可视化开关需要门控数据发布，并在暂停/关闭时清零；PCM 透传仍正常。权限申请可以保留，但软件自身拿到 PCM，技术上也能自算 FFT，方案“免权限拿不到频域”不准确。软件 FFT 可作为后续方向，不必在当前批次扩张。
7. **P2，AutoEq 子集不宜按耳机原始 Score>=80 筛选。** 这会排除更需要校正的型号。建议选用户耳机型号或明确测试列表，保留 measurement source/target/source revision，避免同型号不同测量源互相覆盖。此阶段不建议加入第三方在线 API。

## 需要重写的验收

- §9 批次1“切歌10次，attach/release各10”：切歌未必改变 session。正确断言是同 sid attach 幂等、session 真变时 release 旧实例一次、活动实例最多一个；最终释放后 attach/release 总量相等，运行期间可差1。切歌只验证用户曲线不丢，不能假定触发新 session。
- “拖动30次后commit=1”应写为“一次手势30个更新事件，commit增量1”；30次独立手势应30次commit。
- “关闭EQ后所有 Effect Chains=0”以及“PRECISE所有 Effect Chains=0”都不成立：Visualizer、独立动态处理或其它应用的系统 effect 可仍存在。按本应用 sid、effect 类型和 enabled 状态核对。
- 批次3④写成“再切回PRECISE，isActive=false”是自相矛盾，应是平台模式下自研EQ不参与，精确模式下平台EQ/BassBoost不参与；明确 DP 是否仍保留。
- 部分失败验收只比较 `bandsSent` 与UI，不能证明底层实际成功。记录 API 返回/异常/控制权与 mounted 状态，必要时回读；渲染验证用离线样本，不能只靠设置日志。
- 增加暂停→恢复位置、通知服务销毁→重新播放、页面离开时滑杆 pending、旧预设迁移回滚、非44.1/48k本地音源测试。DSP 离线测试需要独立 oracle，不能只让导入/导出同一函数互相证明。

## 推荐实际批次

1. P0：修 DVC 全频/段级启停；安全实例生命周期；分区门控；智能映射明确失败；滑杆实时下发与单手势提交；不改变播放器所有权。
2. 页面：三分区、各项真实能力与状态、DVC档位、可视化统一开关/归零、诊断；维持现有新拟物基元与返回动画。
3. 独立后续：先确定 canonical preset schema，再做同处理器浮点 EQ+limiter、稳定更新与独立离线测试，然后开放精确引擎、曲线和文本导入导出。
4. AutoEq 与空间状态视为增量，未经上述基础不要并入同一次大变更。5.1 的测试边界可自动化；真实厂商效果与DVC听感仍需实机确认，但不阻碍基础修复。
