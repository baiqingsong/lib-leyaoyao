# LibPayLyy

乐摇摇支付 Android SDK，提供与乐摇摇支付平台的 Socket 通信、设备绑定、支付二维码获取、游戏结果上报、退款等完整支付功能。

---

## 目录

- [项目结构](#项目结构)
- [引入方式](#引入方式)
- [快速开始](#快速开始)
- [核心类说明](#核心类说明)
  - [PayConfig](#payconfig)
  - [PayFactory](#payfactory)
  - [PayLyyManager](#paylyymanager)
  - [OnPayListener](#onpaylistener)
- [内部类说明（包级访问）](#内部类说明包级访问)
  - [PayConstant](#payconstant)
  - [LyySocketModel](#lyysocketmodel)
  - [LyySocketReqModel](#lyysocketreqmodel)
  - [LCipherUtil](#lcipherutil)
  - [LStringUtil](#lstringutil)
- [通信协议流程](#通信协议流程)
- [完整使用示例](#完整使用示例)
- [注意事项](#注意事项)

---

## 项目结构

```
lyy/src/main/java/com/dawn/lyy/
├── PayConfig.java          # 支付配置类（Builder 模式）
├── PayFactory.java         # 支付工厂类（统一入口，单例）
├── PayLyyManager.java      # 支付管理器（核心逻辑）
├── OnPayListener.java      # 支付事件回调接口
├── PayConstant.java        # 内部常量（包级访问）
├── LyySocketModel.java     # Socket 通信数据模型（包级访问）
├── LyySocketReqModel.java  # Socket 请求封装模型（包级访问）
├── LCipherUtil.java        # AES 加解密工具（包级访问）
└── LStringUtil.java        # Hex 转换工具（包级访问）
```

---

## 引入方式

### 方式一：JitPack 远程依赖

**1. 根目录 `build.gradle` 添加仓库：**

```gradle
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url "https://jitpack.io" }
    }
}
```

**2. 模块 `build.gradle` 添加依赖：**

```gradle
dependencies {
    implementation 'com.github.baiqingsong:lib-leyaoyao:1.0.3'
    implementation 'com.google.code.gson:gson:2.8.5'       // Gson 需要单独引入
}
```

### 方式二：本地模块依赖

**1. `settings.gradle`：**

```gradle
include ':lyy'
```

**2. 模块 `build.gradle`：**

```gradle
dependencies {
    implementation project(path: ':lyy')
    implementation 'com.google.code.gson:gson:2.8.5'
}
```

---

## 快速开始

```java
// 1. 构建配置
PayConfig config = new PayConfig.Builder()
        .payUrl("ehw.leyaoyao.com")    // 乐摇摇 Socket 域名
        .payPort(8080)                  // 端口号
        .appId("your_app_id")          // 乐摇摇后台的 AppId
        .appSecret("your_app_secret")  // 乐摇摇后台的秘钥（hex格式）
        .deviceId("your_device_id")    // 设备唯一标识
        .price(100)                     // 默认支付金额（单位：分）
        .inventory(50)                  // 初始库存
        .build();

// 2. 初始化支付（自动连接）
PayFactory.getInstance().init(config, new OnPayListener() {
    @Override
    public void onPayConnectStatus(boolean status) {
        // 支付连接状态变化
    }

    @Override
    public void getPayId(String payId) {
        // 获取到支付商户号
    }

    @Override
    public void getBindQrCode(String qrCode) {
        // 获取到绑定二维码内容，展示给用户扫描
    }

    @Override
    public void onPayBindSuccess() {
        // 设备绑定成功
    }

    @Override
    public void onPayUnbindSuccess() {
        // 设备解绑成功
    }

    @Override
    public void getPayQrCode(String key, String qrCode) {
        // 获取到支付二维码，qrCode 为 null 表示获取失败
    }

    @Override
    public void onPaySuccess(String key) {
        // 支付成功回调
    }

    @Override
    public void getPayPrice(int price) {
        // 服务端下发了新的支付价格（单位：分）
    }

    @Override
    public void onRemotePaySuccess() {
        // 远程上分成功
    }
});

// 3. 在 Activity/Fragment 销毁时释放资源
@Override
protected void onDestroy() {
    super.onDestroy();
    PayFactory.getInstance().destroy();
}
```

---

## 核心类说明

### PayConfig

**包名：** `com.dawn.lyy`  
**说明：** 支付配置类，使用 Builder 模式构建，封装所有支付参数。构建时会校验必填参数。

#### Builder 方法

| 方法 | 参数 | 必填 | 说明 |
|------|------|:----:|------|
| `payUrl(String)` | Socket 域名 | ✅ | 乐摇摇服务端地址，如 `"ehw.leyaoyao.com"` |
| `payPort(int)` | 端口号 | - | 服务端端口，默认 `0` |
| `appId(String)` | 应用ID | ✅ | 乐摇摇后台分配的 AppId |
| `appSecret(String)` | 应用秘钥 | ✅ | 乐摇摇后台分配的秘钥（hex 格式字符串） |
| `deviceId(String)` | 设备ID | ✅ | 设备唯一标识符 |
| `price(int)` | 价格 | - | 默认支付金额，单位：分，默认 `0` |
| `inventory(int)` | 库存 | - | 初始库存数量，默认 `0` |
| `build()` | - | - | 构建配置对象，必填项为空时抛出 `IllegalArgumentException` |

#### 运行时可修改的属性

| 方法 | 说明 |
|------|------|
| `setPrice(int)` | 更新支付价格 |
| `setInventory(int)` | 更新库存数量 |

#### 示例

```java
PayConfig config = new PayConfig.Builder()
        .payUrl("ehw.leyaoyao.com")
        .payPort(8080)
        .appId("abc123")
        .appSecret("0A1B2C3D4E5F")
        .deviceId("DEVICE001")
        .price(300)      // 3元
        .inventory(100)
        .build();
```

---

### PayFactory

**包名：** `com.dawn.lyy`  
**说明：** 支付工厂类，全局单例，作为 SDK 的统一入口。内部管理 `PayLyyManager` 的生命周期。

#### 方法列表

| 方法 | 参数 | 返回值 | 说明 |
|------|------|--------|------|
| `getInstance()` | - | `PayFactory` | 获取单例实例 |
| `init(PayConfig, OnPayListener)` | 配置 + 回调 | `void` | 初始化并启动支付连接。重复调用会先销毁旧实例 |
| `destroy()` | - | `void` | 销毁支付管理器，释放 Socket/线程/Handler 等所有资源 |
| `getManager()` | - | `PayLyyManager` | 获取内部管理器实例（高级用法） |
| `setListener(OnPayListener)` | 回调接口 | `void` | 运行时更换回调监听 |
| `getListener()` | - | `OnPayListener` | 获取当前回调监听 |
| `sendGetPayQrCode(String, int)` | 流水号, 金额(分) | `void` | 请求支付二维码 |
| `sendGameResult(String, boolean)` | 流水号, 结果 | `void` | 上报游戏结果（true=成功） |
| `sendUpdateResult()` | - | `void` | 重新上传参数设置到服务端 |
| `sendRefund(String, String)` | 流水号, 金额 | `void` | 发起退款 |
| `updateInventory(int)` | 库存数量 | `void` | 运行时更新库存 |
| `updatePrice(int)` | 价格(分) | `void` | 运行时更新价格 |

#### 示例

```java
PayFactory pay = PayFactory.getInstance();

// 初始化
pay.init(config, listener);

// 请求支付二维码（金额 500 分 = 5 元）
pay.sendGetPayQrCode("order_001", 500);

// 上报游戏成功
pay.sendGameResult("order_001", true);

// 更新库存
pay.updateInventory(45);

// 退款
pay.sendRefund("order_001", "500");

// 重新上传设置
pay.sendUpdateResult();

// 销毁
pay.destroy();
```

---

### PayLyyManager

**包名：** `com.dawn.lyy`  
**说明：** 支付核心管理器，负责 Socket 连接、心跳维护、协议加解密、消息收发、自动重连等。通常通过 `PayFactory` 间接使用，不需要直接操作。

#### 公开方法

| 方法 | 说明 |
|------|------|
| `setListener(OnPayListener)` | 设置回调监听 |
| `getListener()` | 获取回调监听 |
| `start()` | 启动 Socket 连接 |
| `destroy()` | 销毁管理器，释放所有资源，不可复用 |
| `requestPayQrCode(String, int)` | 请求支付二维码 |
| `sendGameResult(String, boolean)` | 上报游戏结果 |
| `updateSettings()` | 重新上传仓位/商品参数 |
| `refund(String, String)` | 发起退款 |
| `updateInventory(int)` | 更新库存 |
| `updatePrice(int)` | 更新价格 |

#### 公开查询方法

| 方法 | 返回值 | 说明 |
|------|--------|------|
| `isLoggedIn()` | `boolean` | 是否已登录到支付平台 |
| `isSocketConnected()` | `boolean` | Socket 是否已连接 |

#### 内部机制

| 机制 | 说明 |
|------|------|
| **自动重连** | 连接超时（30秒）或设置超时（30秒）自动重连，指数退避延迟（3s × 重试次数），最多5次后重置继续尝试 |
| **心跳检测** | 每 10 秒发送心跳，连续 5 次无响应自动重连 |
| **登录状态检查** | 所有需要登录的操作（获取二维码、游戏结果、退款、更新设置）会自动校验登录态，未登录时安全返回 |
| **线程安全** | AtomicBoolean/AtomicInteger + volatile，防止并发问题 |
| **线程模型** | HandlerThread 处理定时任务，单线程 ExecutorService 发送消息 |
| **防泄漏** | Handler 使用 WeakReference，销毁时清理所有资源 |
| **主线程回调** | 所有 `OnPayListener` 回调在主线程执行 |

---

### OnPayListener

**包名：** `com.dawn.lyy`  
**说明：** 支付事件回调接口，所有回调方法均在**主线程**执行。

#### 方法列表

| 方法 | 参数 | 触发时机 |
|------|------|----------|
| `onPayConnectStatus(boolean status)` | `true`=已连接, `false`=断开 | 连接状态变化时 |
| `getPayId(String payId)` | 支付商户号 | 登录成功获取到商户号时 |
| `getBindQrCode(String qrCode)` | 二维码内容字符串 | 设备未绑定，需要展示二维码供用户扫描绑定 |
| `onPayBindSuccess()` | - | 设备绑定成功时 |
| `onPayUnbindSuccess()` | - | 设备解绑成功时 |
| `getPayQrCode(String key, String qrCode)` | 流水号, 二维码内容 | 获取到支付二维码时。`qrCode` 为 `null` 表示获取失败 |
| `onPaySuccess(String key)` | 支付流水号 | 用户支付成功时 |
| `getPayPrice(int price)` | 价格(分) | 服务端下发新价格时 |
| `onRemotePaySuccess()` | - | 远程上分成功时 |

#### 示例

```java
OnPayListener listener = new OnPayListener() {
    @Override
    public void onPayConnectStatus(boolean status) {
        if (status) {
            Log.i("Pay", "支付已连接");
        } else {
            Log.w("Pay", "支付连接断开，等待重连...");
        }
    }

    @Override
    public void getPayId(String payId) {
        Log.i("Pay", "商户号: " + payId);
    }

    @Override
    public void getBindQrCode(String qrCode) {
        // 将 qrCode 内容生成二维码图片展示在界面上
        showQrCodeImage(qrCode);
    }

    @Override
    public void onPayBindSuccess() {
        Toast.makeText(context, "设备绑定成功", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onPayUnbindSuccess() {
        Toast.makeText(context, "设备已解绑", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void getPayQrCode(String key, String qrCode) {
        if (qrCode != null) {
            // 展示支付二维码
            showPayQrCode(key, qrCode);
        } else {
            // 获取失败，可能未登录
            Log.e("Pay", "获取支付二维码失败");
        }
    }

    @Override
    public void onPaySuccess(String key) {
        Log.i("Pay", "支付成功，流水号: " + key);
        // 开始游戏或发货逻辑
        startGame(key);
    }

    @Override
    public void getPayPrice(int price) {
        Log.i("Pay", "服务端更新价格: " + price + " 分");
    }

    @Override
    public void onRemotePaySuccess() {
        Log.i("Pay", "远程上分成功");
    }
};
```

---

## 内部类说明（包级访问）

> 以下类为 SDK 内部使用，外部不可直接访问，此处仅供开发维护参考。

### PayConstant

内部常量定义。

| 常量 | 值 | 说明 |
|------|----|------|
| `SDK_VERSION` | `"2.2.1"` | SDK 版本号，登录时上报 |
| `LOGIN_FLAG` | `"4738"` | 登录标识，对应乐摇摇后台配置 |
| `DEVICE_TYPE` | `"SHJ"` | 设备类型标识 |

### LyySocketModel

Socket 通信数据模型，对应乐摇摇协议的 JSON 格式。

```json
{
    "a": "操作指令",
    "p": { "各种参数..." },
    "k": "指令唯一码"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `a` | `String` | action — 协议指令标识 |
| `p` | `LyySocketModelP` | parameter — 指令参数对象 |
| `k` | `String` | key — 指令唯一码/流水号 |

**LyySocketModelP 内部字段：**

| 字段 | 说明 | 字段 | 说明 |
|------|------|------|------|
| `u` | 设备UUID | `d` | 随机字符串/绑定状态 |
| `q` | 绑定二维码内容 | `t` | 类型标识 |
| `uid` | 付款用户ID | `i` | 仓位编号 |
| `n` | 商品名称 | `g` | 商品价格(分) |
| `p` | 支付价格(分) | `c` | 中奖概率 |
| `ca` | 容量 | `cu` | 库存 |
| `cn` | 货道名称 | `v` | 设备编号 |
| `o` | 来源类型 | `f` | 登录标识 |
| `ci` | 产品编号 | `si` | 服务端商品ID |
| `s` | 商品库存 | `a` | 退礼数量 |
| `error` | 错误信息 | | |

### LyySocketReqModel

Socket 请求封装，AES 加密后的传输格式。

```json
{
    "app": "应用AppId",
    "data": "AES加密后的数据"
}
```

### LCipherUtil

AES 加解密工具，使用 `AES/ECB/PKCS5Padding` 模式。

| 方法 | 说明 |
|------|------|
| `encryptAES(String message, String passWord)` | AES 加密，密码为 hex 字符串 |
| `decryptAES(String message, String passWord)` | AES 解密，密码为 hex 字符串 |

### LStringUtil

Hex 字符串与字节数组互转工具。

| 方法 | 说明 |
|------|------|
| `toHexString(byte[])` | 字节数组 → 大写 hex 字符串 |
| `toByteArray(String)` | hex 字符串 → 字节数组 |

---

## 通信协议流程

```
┌──────────┐                           ┌──────────────┐
│  客户端   │                           │ 乐摇摇服务端  │
└────┬─────┘                           └──────┬───────┘
     │                                        │
     │  1. Socket 连接                         │
     │ ──────────────────────────────────────> │
     │                                        │
     │  2. 获取登录随机数 (a:"rs")              │
     │ ──────────────────────────────────────> │
     │  3. 返回随机字符串 (a:"rsr")             │
     │ <────────────────────────────────────── │
     │                                        │
     │  4. 登录 (a:"l")                        │
     │ ──────────────────────────────────────> │
     │  5. 登录结果 (a:"lr")                    │
     │ <────────────────────────────────────── │
     │                                        │
     │  6. 上传仓位参数 (a:"mbp")               │
     │ ──────────────────────────────────────> │
     │  7. 仓位设置成功 (a:"mbpr")              │
     │ <────────────────────────────────────── │
     │                                        │
     │  8. 上传商品信息 (a:"rg")                │
     │ ──────────────────────────────────────> │
     │  9. 商品信息返回 (a:"rgr")               │
     │ <────────────────────────────────────── │
     │                                        │
     │  10. 上传仓道商品关系 (a:"rp")            │
     │ ──────────────────────────────────────> │
     │  11. 关系返回 (a:"rpr")                  │
     │ <────────────────────────────────────── │
     │                                        │
     │  ♥ 心跳保持 (每10秒, "heartbeat")       │
     │ <────────────────────────────────────> │
     │                                        │
     │  12. 请求支付二维码 (a:"pq")             │
     │ ──────────────────────────────────────> │
     │  13. 返回支付二维码 (a:"pqr")            │
     │ <────────────────────────────────────── │
     │                                        │
     │  14. 支付成功通知 (a:"pr")               │
     │ <────────────────────────────────────── │
     │  15. 支付成功响应 (a:"prr")              │
     │ ──────────────────────────────────────> │
     │                                        │
     │  16. 上报游戏结果 (a:"sr")               │
     │ ──────────────────────────────────────> │
     │  17. 游戏结果响应 (a:"srr")              │
     │ <────────────────────────────────────── │
```

### 协议指令速查表

| 指令 | 方向 | 说明 |
|------|------|------|
| `rs` | → | 请求登录随机数 |
| `rsr` | ← | 返回登录随机数 |
| `l` | → | 登录 |
| `lr` | ← | 登录结果 |
| `mbp` | → | 上传仓位参数 |
| `mbpr` | ← | 仓位参数设置成功 |
| `rg` | → | 上传商品信息 |
| `rgr` | ← | 商品信息返回 |
| `rp` | → | 上传仓道商品关系 |
| `rpr` | ← | 关系设置返回 |
| `pq` | → | 请求支付二维码 |
| `pqr` | ← | 返回支付二维码 |
| `pr` | ← | 支付成功通知 |
| `prr` | → | 支付成功响应 |
| `sr` | → | 上报游戏结果/退款 |
| `srr` | ← | 游戏结果响应 |
| `g` | → | 上传退礼数据 |
| `b` | ← | 设备绑定通知 |
| `br` | → | 绑定成功响应 |
| `ub` | ← | 设备解绑通知 |
| `ubr` | → | 解绑成功响应 |
| `bq` | → | 请求绑定二维码 |
| `bqr` | ← | 返回绑定二维码 |
| `bspi` | ← | 服务端批量设置 |
| `ipr` | → | 设置响应 |
| `ras` | ← | 远程上分 |
| `rasr` | → | 远程上分响应 |
| `eqb` | ← | 自定义参数查询 |
| `eqbr` | → | 自定义参数查询回复 |
| `esb` | ← | 自定义参数设置 |
| `esbr` | → | 自定义参数设置回复 |

---

## 完整使用示例

### Activity 中使用

```java
public class PayActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pay);

        // 构建配置
        PayConfig config = new PayConfig.Builder()
                .payUrl("ehw.leyaoyao.com")
                .payPort(8080)
                .appId("your_app_id")
                .appSecret("your_app_secret_hex")
                .deviceId(getDeviceId())
                .price(300)
                .inventory(50)
                .build();

        // 初始化支付
        PayFactory.getInstance().init(config, new OnPayListener() {
            @Override
            public void onPayConnectStatus(boolean status) {
                runOnUiThread(() -> {
                    tvStatus.setText(status ? "已连接" : "连接中...");
                });
            }

            @Override
            public void getPayId(String payId) {
                Log.i("Pay", "商户号: " + payId);
            }

            @Override
            public void getBindQrCode(String qrCode) {
                // 展示绑定二维码
                showBindQrCode(qrCode);
            }

            @Override
            public void onPayBindSuccess() {
                Toast.makeText(PayActivity.this, "绑定成功", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onPayUnbindSuccess() {
                Toast.makeText(PayActivity.this, "已解绑", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void getPayQrCode(String key, String qrCode) {
                if (qrCode != null) {
                    showPayQrCode(qrCode);
                } else {
                    Toast.makeText(PayActivity.this, "获取支付码失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onPaySuccess(String key) {
                // 支付成功，开始业务逻辑
                startGame(key);
            }

            @Override
            public void getPayPrice(int price) {
                // 服务端更新了价格
                PayFactory.getInstance().updatePrice(price);
            }

            @Override
            public void onRemotePaySuccess() {
                Log.i("Pay", "远程上分成功");
            }
        });
    }

    // 用户点击开始游戏按钮
    private void onStartGameClick() {
        String orderKey = "order_" + System.currentTimeMillis();
        PayFactory.getInstance().sendGetPayQrCode(orderKey, 300);
    }

    // 游戏结束
    private void onGameFinished(String key, boolean win) {
        PayFactory.getInstance().sendGameResult(key, win);
    }

    // 库存变化时
    private void onInventoryChanged(int newInventory) {
        PayFactory.getInstance().updateInventory(newInventory);
        PayFactory.getInstance().sendUpdateResult();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        PayFactory.getInstance().destroy();
    }
}
```

---

## 注意事项

1. **必须在 `onDestroy()` 中调用 `PayFactory.getInstance().destroy()`**，否则 Socket 连接和后台线程不会释放，造成内存泄漏。

2. **`appSecret` 必须是 hex 格式字符串**，它会被转换为字节数组作为 AES 密钥。

3. **金额单位为分**，例如 3 元 = 300。

4. **所有回调在主线程执行**，可以直接更新 UI，无需 `runOnUiThread()`。

5. **自动重连机制**：连接断开后 SDK 自动尝试重连，采用指数退避策略（3s × 重试次数），最多 5 次后重置继续尝试，支付系统不会彻底放弃连接。

6. **登录状态保护**：请求支付二维码、发送游戏结果、退款等操作会自动检查登录态，未登录时安全忽略或返回空值，避免发送无效请求。

7. **不依赖 Android Service**：SDK 不再注册任何 Service 或 BroadcastReceiver，不会影响应用的其他支付模块或造成卡顿。

8. **线程安全**：`PayFactory` 是线程安全的单例，`PayConfig` 中的 `price` 和 `inventory` 使用 `volatile` 保证可见性，连接状态使用 `AtomicBoolean`。
