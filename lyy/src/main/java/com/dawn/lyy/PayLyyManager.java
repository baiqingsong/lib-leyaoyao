package com.dawn.lyy;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;

import com.dawn.socket.LSocketUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 乐摇摇支付管理器
 * <p>
 * 替代原来的 PayLyyService，不再依赖 Android Service 和 BroadcastReceiver，
 * 直接通过方法调用控制支付流程，避免多服务导致的资源占用和卡顿。
 * </p>
 *
 * <p>稳定性特性：</p>
 * <ul>
 *   <li>连接失败自动重连：指数退避，最多5次</li>
 *   <li>心跳保活：10秒间隔，连续5次失败自动重连</li>
 *   <li>登录超时保护：连接后30秒内未完成登录流程自动重连</li>
 *   <li>登录状态检查：需要登录的操作自动校验登录态</li>
 *   <li>线程安全：AtomicBoolean/AtomicInteger + volatile</li>
 * </ul>
 */
public class PayLyyManager {

    private static final String TAG = "PayLyy";

    // ==================== Handler消息类型 ====================
    private static final int MSG_CONNECT_TIMEOUT = 0x101;
    private static final int MSG_SETTING_TIMEOUT = 0x102;
    private static final int MSG_HEARTBEAT = 0x104;
    private static final int MSG_CONNECT_SUCCESS = 0x105;
    private static final int MSG_RECONNECT = 0x106;

    // ==================== 时间常量(ms) ====================
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int SETTING_TIMEOUT_MS = 30_000;
    private static final int HEARTBEAT_INTERVAL_MS = 10_000;
    private static final int CONNECT_DELAY_MS = 3_000;
    private static final int RECONNECT_BASE_DELAY_MS = 3_000;

    // ==================== 配置常量 ====================
    private static final int MAX_HEARTBEAT_FAIL = 5;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final int GIFT_AMOUNT = 500;

    // ==================== 核心字段 ====================
    private final PayConfig config;
    private volatile OnPayListener listener;
    private final Gson gson;
    private final ExecutorService sendExecutor;
    private final Handler mainHandler;
    private HandlerThread handlerThread;
    private Handler workHandler;

    // ==================== 连接状态 ====================
    private LSocketUtil socketUtil;
    private final AtomicBoolean isLogin = new AtomicBoolean(false);
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicInteger heartFailNum = new AtomicInteger(0);
    private final AtomicInteger reconnectCount = new AtomicInteger(0);
    private final AtomicBoolean isDestroyed = new AtomicBoolean(false);
    private final AtomicBoolean manualDestroy = new AtomicBoolean(false);

    PayLyyManager(PayConfig config) {
        this.config = config;
        this.gson = new GsonBuilder().create();
        this.sendExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "PayLyy-Send");
            t.setDaemon(true);
            return t;
        });
        this.mainHandler = new Handler(Looper.getMainLooper());
        initWorkHandler();
    }

    private void initWorkHandler() {
        handlerThread = new HandlerThread("PayLyy-Work");
        handlerThread.start();
        workHandler = new PayHandler(handlerThread.getLooper(), this);
    }

    /**
     * 设置支付回调监听
     */
    public void setListener(OnPayListener listener) {
        this.listener = listener;
    }

    /**
     * 获取支付回调监听
     */
    public OnPayListener getListener() {
        return listener;
    }

    /**
     * 启动支付连接
     */
    public void start() {
        if (isDestroyed.get()) {
            PaymentLog.w(TAG, "Manager已销毁，请重新创建实例");
            return;
        }
        PaymentLog.i(TAG, "支付管理器启动");
        manualDestroy.set(false);
        reconnectCount.set(0);
        payConnect();
    }

    /**
     * 销毁支付管理器，释放所有资源
     */
    public void destroy() {
        if (isDestroyed.getAndSet(true)) return;
        manualDestroy.set(true);
        PaymentLog.i(TAG, "支付管理器销毁");
        if (workHandler != null) {
            workHandler.removeCallbacksAndMessages(null);
        }
        if (handlerThread != null) {
            handlerThread.quitSafely();
        }
        disconnectSocket();
        sendExecutor.shutdownNow();
        isLogin.set(false);
        isConnected.set(false);
        listener = null;
    }

    /**
     * 是否已登录到支付平台
     */
    public boolean isLoggedIn() {
        return isLogin.get();
    }

    /**
     * Socket是否已连接
     */
    public boolean isSocketConnected() {
        return isConnected.get();
    }

    /**
     * 获取支付二维码
     *
     * @param key   支付流水号
     * @param price 支付金额（单位分）
     */
    public void requestPayQrCode(String key, int price) {
        if (isDestroyed.get()) return;
        PaymentLog.i(TAG, "请求支付二维码: key=" + key + " price=" + price);
        if (isLogin.get()) {
            sendLYYCommand(buildPayQrCode(key, price));
        } else {
            PaymentLog.w(TAG, "未登录，无法获取支付二维码");
            notifyListener(l -> l.getPayQrCode(key, null));
        }
    }

    /**
     * 发送游戏结果
     *
     * @param key    支付流水号
     * @param status 游戏结果（true=成功，false=失败）
     */
    public void sendGameResult(String key, boolean status) {
        if (isDestroyed.get()) return;
        if (!isLogin.get()) {
            PaymentLog.w(TAG, "未登录，无法发送游戏结果");
            return;
        }
        PaymentLog.i(TAG, "发送游戏结果: key=" + key + " status=" + status);
        sendLYYCommand(buildGameResult(key, status));
        sendLYYCommand(buildRemainder(key));
    }

    /**
     * 更新参数设置
     */
    public void updateSettings() {
        if (isDestroyed.get()) return;
        if (!isLogin.get()) {
            PaymentLog.w(TAG, "未登录，无法更新设置");
            return;
        }
        sendLYYCommand(buildParamSetting());
    }

    /**
     * 退款
     *
     * @param key 支付流水号
     * @param pay 退款金额
     */
    public void refund(String key, String pay) {
        if (isDestroyed.get()) return;
        if (!isLogin.get()) {
            PaymentLog.w(TAG, "未登录，无法执行退款");
            return;
        }
        PaymentLog.i(TAG, "发起退款: key=" + key + " pay=" + pay);
        sendLYYCommand(buildRefundResult(key, pay));
    }

    /**
     * 更新库存
     */
    public void updateInventory(int inventory) {
        config.setInventory(inventory);
    }

    /**
     * 更新价格
     */
    public void updatePrice(int price) {
        config.setPrice(price);
    }

    // ==================== 内部实现 ====================

    private static class PayHandler extends Handler {
        private final WeakReference<PayLyyManager> ref;

        PayHandler(Looper looper, PayLyyManager manager) {
            super(looper);
            ref = new WeakReference<>(manager);
        }

        @Override
        public void handleMessage(Message msg) {
            PayLyyManager manager = ref.get();
            if (manager == null || manager.isDestroyed.get()) return;
            switch (msg.what) {
                case MSG_CONNECT_TIMEOUT:
                    PaymentLog.w(TAG, "连接超时");
                    manager.handleConnectionLost();
                    break;
                case MSG_SETTING_TIMEOUT:
                    PaymentLog.w(TAG, "登录初始化超时");
                    manager.handleConnectionLost();
                    break;
                case MSG_HEARTBEAT:
                    manager.sendCycleHeart();
                    break;
                case MSG_CONNECT_SUCCESS:
                    manager.workHandler.removeMessages(MSG_SETTING_TIMEOUT);
                    manager.workHandler.sendEmptyMessageDelayed(MSG_SETTING_TIMEOUT, SETTING_TIMEOUT_MS);
                    manager.sendLYYCommand(manager.buildLoginRandom());
                    break;
                case MSG_RECONNECT:
                    manager.payConnect();
                    break;
            }
        }
    }

    private void payConnect() {
        if (isDestroyed.get() || manualDestroy.get()) return;
        disconnectSocket();
        PaymentLog.i(TAG, "发起Socket连接: " + config.getPayUrl() + ":" + config.getPayPort());
        socketUtil = new LSocketUtil();
        socketUtil.connect(config.getPayUrl(), config.getPayPort(), new LSocketUtil.SocketListener() {
            @Override
            public void connectSuccess() {
                PaymentLog.i(TAG, "Socket连接成功");
                isConnected.set(true);
                heartFailNum.set(0);
                reconnectCount.set(0);
                workHandler.removeMessages(MSG_CONNECT_TIMEOUT);
                workHandler.sendEmptyMessageDelayed(MSG_CONNECT_SUCCESS, CONNECT_DELAY_MS);
            }

            @Override
            public void receiverMsg(String msg) {
                resolveReceiverData(msg);
            }

            @Override
            public void connectFail() {
                PaymentLog.e(TAG, "Socket连接失败");
                isConnected.set(false);
                isLogin.set(false);
                handleConnectionLost();
            }
        });
        workHandler.removeMessages(MSG_CONNECT_TIMEOUT);
        workHandler.sendEmptyMessageDelayed(MSG_CONNECT_TIMEOUT, CONNECT_TIMEOUT_MS);
    }

    private void disconnectSocket() {
        isConnected.set(false);
        if (socketUtil != null) {
            try {
                socketUtil.disConnect();
            } catch (Exception e) {
                PaymentLog.w(TAG, "断开Socket异常", e);
            }
            socketUtil = null;
        }
    }

    /**
     * 连接丢失处理：通知回调 + 尝试重连
     */
    private void handleConnectionLost() {
        if (isDestroyed.get() || manualDestroy.get()) return;

        workHandler.removeMessages(MSG_HEARTBEAT);
        workHandler.removeMessages(MSG_CONNECT_TIMEOUT);
        workHandler.removeMessages(MSG_SETTING_TIMEOUT);
        isLogin.set(false);
        isConnected.set(false);
        disconnectSocket();
        notifyListener(l -> l.onPayConnectStatus(false));

        attemptReconnect();
    }

    /**
     * 带退避延迟的自动重连
     */
    private void attemptReconnect() {
        if (isDestroyed.get() || manualDestroy.get()) return;

        int count = reconnectCount.incrementAndGet();
        if (count > MAX_RECONNECT_ATTEMPTS) {
            PaymentLog.e(TAG, "重连失败，已达最大重试次数: " + MAX_RECONNECT_ATTEMPTS);
            reconnectCount.set(0);
            // 重置后继续尝试（支付系统不应彻底放弃连接）
            workHandler.sendEmptyMessageDelayed(MSG_RECONNECT, RECONNECT_BASE_DELAY_MS * MAX_RECONNECT_ATTEMPTS);
            return;
        }

        long delay = RECONNECT_BASE_DELAY_MS * count;
        PaymentLog.i(TAG, "准备第 " + count + " 次重连，延迟 " + delay + "ms");
        workHandler.sendEmptyMessageDelayed(MSG_RECONNECT, delay);
    }

    private void sendMsg(final String msg) {
        if (socketUtil != null && !TextUtils.isEmpty(msg) && !isDestroyed.get()) {
            sendExecutor.execute(() -> {
                LSocketUtil su = socketUtil;
                if (su != null) {
                    try {
                        su.sendMsg(msg + "\r\n");
                    } catch (Exception e) {
                        PaymentLog.e(TAG, "发送消息异常", e);
                    }
                }
            });
        }
    }

    private void sendCycleHeart() {
        if (isDestroyed.get()) return;
        int failCount = heartFailNum.incrementAndGet();
        if (failCount > MAX_HEARTBEAT_FAIL) {
            PaymentLog.w(TAG, "心跳连续失败 " + failCount + " 次，触发重连");
            handleConnectionLost();
        } else {
            LyySocketReqModel req = new LyySocketReqModel("heartbeat");
            sendMsg(gson.toJson(req));
            workHandler.removeMessages(MSG_HEARTBEAT);
            workHandler.sendEmptyMessageDelayed(MSG_HEARTBEAT, HEARTBEAT_INTERVAL_MS);
        }
    }

    private void sendLYYCommand(LyySocketModel model) {
        if (model == null) return;
        try {
            String data = LCipherUtil.encryptAES(gson.toJson(model), config.getAppSecret());
            sendMsg(gson.toJson(new LyySocketReqModel(config.getAppId(), data)));
        } catch (Exception e) {
            PaymentLog.e(TAG, "发送指令失败", e);
        }
    }

    private void resolveReceiverData(String data) {
        if (TextUtils.isEmpty(data)) return;
        try {
            LyySocketReqModel reqModel = gson.fromJson(data, LyySocketReqModel.class);
            if (reqModel == null) return;

            if ("heartbeat".equals(reqModel.getData())) {
                heartFailNum.set(0);
                return;
            }

            String dataStr = LCipherUtil.decryptAES(reqModel.getData(), config.getAppSecret());
            if (TextUtils.isEmpty(dataStr)) return;

            LyySocketModel model = gson.fromJson(dataStr, LyySocketModel.class);
            if (model == null) return;

            String action = model.getA();
            if (TextUtils.isEmpty(action)) return;
            action = action.trim();

            PaymentLog.i(TAG, "收到消息: " + action);
            handleAction(action, model, dataStr);
        } catch (Exception e) {
            PaymentLog.e(TAG, "解析消息失败", e);
        }
    }

    private void handleAction(String action, LyySocketModel model, String dataStr) {
        switch (action) {
            case "rsr":
                handleLoginRandom(model);
                break;
            case "lr":
                handleLoginResult(model, dataStr);
                break;
            case "mbpr":
                handleParamSettingResult();
                break;
            case "rgr":
                handleProductUploadResult(model);
                break;
            case "rpr":
                handleProductRelationResult();
                break;
            case "b":
                handleBindSuccess();
                break;
            case "ub":
                handleUnbindSuccess();
                break;
            case "bqr":
                handleBindQrCode(model);
                break;
            case "pqr":
                handlePayQrCode(model);
                break;
            case "pr":
                handlePaySuccess(model);
                break;
            case "srr":
                handleGameResultResponse(model);
                break;
            case "gr":
                handleRefundResponse(model);
                break;
            case "bspi":
                handleServerSetting(model);
                break;
            case "ras":
                handleRemotePay(model);
                break;
            case "eqb":
                handleCustomParamQuery(model);
                break;
            case "esb":
                handleCustomParamSetting(model);
                break;
            default:
                PaymentLog.d(TAG, "未处理的指令: " + action);
                break;
        }
    }

    // ==================== 消息处理 ====================

    private void handleLoginRandom(LyySocketModel model) {
        if (model.getP() == null) return;
        String randomStr = model.getP().getD();
        sendLYYCommand(buildLogin(randomStr));
    }

    private void handleLoginResult(LyySocketModel model, String dataStr) {
        PaymentLog.i(TAG, "登录成功: " + dataStr);
        isLogin.set(true);
        if (model.getP() == null) return;

        String d = model.getP().getD();
        String v = model.getP().getV();

        if (!TextUtils.isEmpty(v)) {
            notifyListener(l -> l.getPayId(v));
        }

        if ("1".equals(d)) {
            String qr = model.getP().getQ();
            notifyListener(l -> l.getBindQrCode(qr));
            PaymentLog.i(TAG, "设备未绑定");
            workHandler.removeMessages(MSG_SETTING_TIMEOUT);
            notifyListener(l -> l.onPayConnectStatus(true));
            sendCycleHeart();
        } else {
            sendLYYCommand(buildParamSetting());
        }
    }

    private void handleParamSettingResult() {
        PaymentLog.i(TAG, "仓位数据上传成功");
        workHandler.removeMessages(MSG_SETTING_TIMEOUT);
        notifyListener(l -> l.onPayConnectStatus(true));
        sendLYYCommand(buildUploadProductMsg());
    }

    private void handleProductUploadResult(LyySocketModel model) {
        PaymentLog.i(TAG, "商品信息上传成功");
        String si = (model.getP() == null) ? "" : model.getP().getSi();
        sendLYYCommand(buildUploadParamProduct(si));
    }

    private void handleProductRelationResult() {
        PaymentLog.i(TAG, "商品仓道关系上传成功");
        sendCycleHeart();
    }

    private void handleBindSuccess() {
        PaymentLog.i(TAG, "设备绑定成功");
        notifyListener(OnPayListener::onPayBindSuccess);
        sendLYYCommand(buildBindSuccessReply());
        sendLYYCommand(buildParamSetting());
    }

    private void handleUnbindSuccess() {
        PaymentLog.i(TAG, "设备解绑成功");
        notifyListener(OnPayListener::onPayUnbindSuccess);
        sendLYYCommand(buildUnbindSuccessReply());
        sendLYYCommand(buildGetBindQrCode());
    }

    private void handleBindQrCode(LyySocketModel model) {
        if (model.getP() == null) return;
        String qr = model.getP().getD();
        PaymentLog.i(TAG, "收到绑定二维码: " + qr);
        notifyListener(l -> l.getBindQrCode(qr));
    }

    private void handlePayQrCode(LyySocketModel model) {
        String key = model.getK();
        String qrCode = (model.getP() != null) ? model.getP().getD() : null;
        PaymentLog.i(TAG, "收到支付二维码: key=" + key);
        notifyListener(l -> l.getPayQrCode(key, qrCode));
    }

    private void handlePaySuccess(LyySocketModel model) {
        String key = model.getK();
        PaymentLog.i(TAG, "支付成功: key=" + key);
        notifyListener(l -> l.onPaySuccess(key));
        sendLYYCommand(buildPaySuccessReply(key));
    }

    private void handleGameResultResponse(LyySocketModel model) {
        String key = model.getK();
        PaymentLog.i(TAG, "游戏结果已确认: key=" + key);
        // 注意：服务端确认了游戏结果，不再错误调用 onPaySuccess
    }

    private void handleRefundResponse(LyySocketModel model) {
        String key = model.getK();
        PaymentLog.i(TAG, "退款返回: key=" + key);
    }

    private void handleServerSetting(LyySocketModel model) {
        if (model.getP() == null) return;
        try {
            int price = Integer.parseInt(model.getP().getP());
            String key = model.getK();
            sendLYYCommand(buildSettingResult(key));
            notifyListener(l -> l.getPayPrice(price));
        } catch (NumberFormatException e) {
            PaymentLog.e(TAG, "解析价格失败", e);
        }
    }

    private void handleRemotePay(LyySocketModel model) {
        String key = model.getK();
        PaymentLog.i(TAG, "远程支付: key=" + key);
        sendLYYCommand(buildRemoteResult(key));
        notifyListener(OnPayListener::onRemotePaySuccess);
    }

    private void handleCustomParamQuery(LyySocketModel model) {
        String key = model.getK();
        String f = (model.getP() != null) ? model.getP().getF() : "";
        sendCustomParamReply(key, f);
    }

    private void handleCustomParamSetting(LyySocketModel model) {
        String key = model.getK();
        String d = (model.getP() != null) ? model.getP().getD() : "";
        sendCustomParamSettingReply(key, d);
    }

    // ==================== 指令构建 ====================

    private LyySocketModel buildLoginRandom() {
        PaymentLog.i(TAG, "获取登录随机数");
        LyySocketModel model = new LyySocketModel();
        model.setA("rs");
        LyySocketModel.LyySocketModelP p = model.new LyySocketModelP();
        p.setU(config.getDeviceId());
        model.setP(p);
        return model;
    }

    private LyySocketModel buildLogin(String randomStr) {
        if (TextUtils.isEmpty(randomStr)) return null;
        PaymentLog.i(TAG, "发送登录");
        LyySocketModel model = new LyySocketModel();
        model.setA("l");
        LyySocketModel.LyySocketModelP p = model.new LyySocketModelP();
        p.setD(randomStr);
        p.setU(config.getDeviceId());
        p.setV(PayConstant.SDK_VERSION);
        p.setF(PayConstant.LOGIN_FLAG);
        p.setT(PayConstant.DEVICE_TYPE);
        model.setP(p);
        return model;
    }

    private LyySocketModel buildParamSetting() {
        PaymentLog.i(TAG, "上传仓位数据");
        LyySocketModel model = new LyySocketModel();
        model.setA("mbp");
        model.setK(String.valueOf(System.currentTimeMillis()));
        LyySocketModel.LyySocketModelP p = model.new LyySocketModelP();
        p.setI("1");
        p.setG("1");
        p.setP(String.valueOf(config.getPrice()));
        p.setC("100");
        p.setCa(String.valueOf(GIFT_AMOUNT));
        p.setCu(String.valueOf(config.getInventory()));
        model.setP(p);
        return model;
    }

    private LyySocketModel buildUploadProductMsg() {
        PaymentLog.i(TAG, "上传商品信息");
        LyySocketModel model = new LyySocketModel();
        model.setA("rg");
        model.setK(String.valueOf(System.currentTimeMillis()));
        LyySocketModel.LyySocketModelP p = model.new LyySocketModelP();
        p.setCi("1001");
        p.setO("put");
        p.setN("照片底片");
        p.setI("");
        p.setP("1");
        model.setP(p);
        return model;
    }

    private LyySocketModel buildUploadParamProduct(String productId) {
        PaymentLog.i(TAG, "上传商品和仓道关系");
        LyySocketModel model = new LyySocketModel();
        model.setA("rp");
        model.setK(String.valueOf(System.currentTimeMillis()));
        LyySocketModel.LyySocketModelP p = model.new LyySocketModelP();
        p.setO("put");
        p.setI("1");
        p.setSi(productId);
        p.setN("A01");
        p.setP(String.valueOf(config.getPrice()));
        p.setS(String.valueOf(config.getInventory()));
        model.setP(p);
        return model;
    }

    private LyySocketModel buildBindSuccessReply() {
        LyySocketModel model = new LyySocketModel();
        model.setA("br");
        model.setK(String.valueOf(System.currentTimeMillis()));
        return model;
    }

    private LyySocketModel buildUnbindSuccessReply() {
        LyySocketModel model = new LyySocketModel();
        model.setA("ubr");
        model.setK(String.valueOf(System.currentTimeMillis()));
        return model;
    }

    private LyySocketModel buildGetBindQrCode() {
        LyySocketModel model = new LyySocketModel();
        model.setA("bq");
        return model;
    }

    private LyySocketModel buildPayQrCode(String payKey, int price) {
        if (TextUtils.isEmpty(payKey)) {
            payKey = String.valueOf(System.currentTimeMillis());
        }
        LyySocketModel model = new LyySocketModel();
        model.setA("pq");
        model.setK(payKey);
        LyySocketModel.LyySocketModelP p = model.new LyySocketModelP();
        p.setD("1,1," + price);
        model.setP(p);
        return model;
    }

    private LyySocketModel buildPaySuccessReply(String key) {
        LyySocketModel model = new LyySocketModel();
        model.setA("prr");
        model.setK(key);
        return model;
    }

    private LyySocketModel buildGameResult(String key, boolean status) {
        LyySocketModel model = new LyySocketModel();
        model.setA("sr");
        model.setK(key);
        LyySocketModel.LyySocketModelP p = model.new LyySocketModelP();
        p.setD(status ? "0" : "1");
        model.setP(p);
        return model;
    }

    private LyySocketModel buildRefundResult(String key, String pay) {
        LyySocketModel model = new LyySocketModel();
        model.setA("sr");
        model.setK(key);
        LyySocketModel.LyySocketModelP p = model.new LyySocketModelP();
        p.setD("1");
        p.setF("1,1," + pay);
        model.setP(p);
        return model;
    }

    private LyySocketModel buildRemainder(String key) {
        LyySocketModel model = new LyySocketModel();
        model.setA("g");
        model.setK(key);
        LyySocketModel.LyySocketModelP p = model.new LyySocketModelP();
        p.setI("1");
        p.setA("1");
        p.setT(String.valueOf(GIFT_AMOUNT - config.getInventory()));
        p.setO("BUY");
        model.setP(p);
        return model;
    }

    private LyySocketModel buildSettingResult(String key) {
        LyySocketModel model = new LyySocketModel();
        model.setA("ipr");
        model.setK(key);
        return model;
    }

    private LyySocketModel buildRemoteResult(String key) {
        LyySocketModel model = new LyySocketModel();
        model.setA("rasr");
        model.setK(key);
        return model;
    }

    private void sendCustomParamReply(String key, String f) {
        LyySocketModel model = new LyySocketModel();
        model.setA("eqbr");
        model.setK(key);
        LyySocketModel.LyySocketModelP p = model.new LyySocketModelP();
        p.setF(f);
        p.setD("0000");
        model.setP(p);
        try {
            String data = LCipherUtil.encryptAES(gson.toJson(model), config.getAppSecret());
            sendMsg(gson.toJson(new LyySocketReqModel(config.getAppId(), data)));
        } catch (Exception e) {
            PaymentLog.e(TAG, "发送自定义参数查询回复失败", e);
        }
    }

    private void sendCustomParamSettingReply(String key, String d) {
        if (TextUtils.isEmpty(d) || d.length() < 4) return;
        LyySocketModel model = new LyySocketModel();
        model.setA("esbr");
        model.setK(key);
        try {
            String data = LCipherUtil.encryptAES(gson.toJson(model), config.getAppSecret());
            sendMsg(gson.toJson(new LyySocketReqModel(config.getAppId(), data)));
        } catch (Exception e) {
            PaymentLog.e(TAG, "发送自定义参数设置回复失败", e);
        }
    }

    // ==================== 工具方法 ====================

    private void notifyListener(ListenerAction action) {
        OnPayListener l = listener;
        if (l != null) {
            mainHandler.post(() -> {
                OnPayListener current = listener;
                if (current != null) {
                    action.execute(current);
                }
            });
        }
    }

    @FunctionalInterface
    private interface ListenerAction {
        void execute(OnPayListener listener);
    }
}
