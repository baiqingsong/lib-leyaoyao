package com.dawn.lyy;

/**
 * 乐摇摇支付工厂类
 * 提供支付管理器的创建和统一访问入口，不再依赖 Service 和 BroadcastReceiver。
 * 通过直接方法调用与 PayLyyManager 交互，避免跨进程通信开销。
 */
public class PayFactory {

    private static volatile PayFactory instance;
    private PayLyyManager manager;

    private PayFactory() {
    }

    public static PayFactory getInstance() {
        if (instance == null) {
            synchronized (PayFactory.class) {
                if (instance == null) {
                    instance = new PayFactory();
                }
            }
        }
        return instance;
    }

    /**
     * 初始化并启动支付
     *
     * @param config   支付配置
     * @param listener 支付事件回调
     */
    public void init(PayConfig config, OnPayListener listener) {
        destroy();
        manager = new PayLyyManager(config);
        manager.setListener(listener);
        manager.start();
    }

    /**
     * 销毁支付管理器，释放所有资源
     */
    public void destroy() {
        if (manager != null) {
            manager.destroy();
            manager = null;
        }
    }

    /**
     * 获取支付管理器实例
     */
    public PayLyyManager getManager() {
        return manager;
    }

    /**
     * 设置支付监听
     */
    public void setListener(OnPayListener listener) {
        if (manager != null) {
            manager.setListener(listener);
        }
    }

    /**
     * 获取支付监听
     */
    public OnPayListener getListener() {
        return manager != null ? manager.getListener() : null;
    }

    /**
     * 请求支付二维码
     *
     * @param key   支付流水号
     * @param price 支付金额（单位分）
     */
    public void sendGetPayQrCode(String key, int price) {
        if (manager != null) {
            manager.requestPayQrCode(key, price);
        }
    }

    /**
     * 发送游戏结果
     *
     * @param key    支付流水号
     * @param status 游戏结果
     */
    public void sendGameResult(String key, boolean status) {
        if (manager != null) {
            manager.sendGameResult(key, status);
        }
    }

    /**
     * 更新参数设置
     */
    public void sendUpdateResult() {
        if (manager != null) {
            manager.updateSettings();
        }
    }

    /**
     * 退款
     *
     * @param key 支付流水号
     * @param pay 退款金额
     */
    public void sendRefund(String key, String pay) {
        if (manager != null) {
            manager.refund(key, pay);
        }
    }

    /**
     * 更新库存
     */
    public void updateInventory(int inventory) {
        if (manager != null) {
            manager.updateInventory(inventory);
        }
    }

    /**
     * 更新价格
     */
    public void updatePrice(int price) {
        if (manager != null) {
            manager.updatePrice(price);
        }
    }
}
