package com.dawn.libpaylyy;

import android.webkit.JavascriptInterface;

import com.dawn.lyy.OnPayListener;
import com.dawn.lyy.PayConfig;
import com.dawn.lyy.PayFactory;

/**
 * 乐摇摇支付的工厂类
 * 直接使用 SDK 层的 OnPayListener，无需再定义冗余接口。
 */
public class PayLyyFactory {

    private static volatile PayLyyFactory instance = null;

    private PayLyyFactory() {
    }

    public static PayLyyFactory getInstance() {
        if (instance == null) {
            synchronized (PayLyyFactory.class) {
                if (instance == null) {
                    instance = new PayLyyFactory();
                }
            }
        }
        return instance;
    }

    private final PayFactory payFactory = PayFactory.getInstance();

    /**
     * 支付初始化
     *
     * @param listener 支付事件回调，直接使用 SDK 的 OnPayListener
     */
    public void payInit(OnPayListener listener) {
        PayConfig config = new PayConfig.Builder()
                .payUrl("ehw.leyaoyao.com")
                .payPort(0)
                .appId("your_app_id")      // TODO: 替换为真实的 appId
                .appSecret("your_secret")   // TODO: 替换为真实的 appSecret
                .deviceId(Constant.deviceId)
                .price(Constant.price)
                .inventory(Constant.giftInventory)
                .build();

        payFactory.init(config, listener);
    }

    /**
     * 销毁支付
     */
    public void destroy() {
        payFactory.destroy();
    }

    @JavascriptInterface
    public void payGetQrCode(String key, int price) {
        payFactory.sendGetPayQrCode(key, price);
    }

    @JavascriptInterface
    public void payGetGameResult(String key, boolean status) {
        payFactory.sendGameResult(key, status);
    }

    @JavascriptInterface
    public void sendUpdateResult() {
        payFactory.sendUpdateResult();
    }

    @JavascriptInterface
    public void sendRefund(String key, String pay) {
        payFactory.sendRefund(key, pay);
    }
}
